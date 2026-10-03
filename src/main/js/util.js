/*
 * Copyright 2020 Sergey Shadchin (sergei.shadchin@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

const i18n = require('./i18n.js');
const {showWarning, showError} = require('./notification.js');

// Session token comes from Spring, never from local storage or URL parameters.
async function csrfHeaders() {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 10000);
    try {
        const response = await fetch('/api/csrf', {credentials: 'same-origin', signal: controller.signal});
        if (!response.ok) throw new Error('Unable to obtain security token. Reload and sign in again.');
        const csrf = await response.json();
        if (!csrf.headerName || !csrf.token) throw new Error('Invalid security token response.');
        return {[csrf.headerName]: csrf.token};
    } finally { clearTimeout(timer); }
}

// One request contract: failed requests return undefined, never invoke success,
// and always release the blocking overlay. 202 completes only after token proof.
async function fetchWithRedirect(url, fetchOptions, options) {
    options = options || {};
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), options.timeout || 20000);
    if (options.block) $.blockUI({message: '<img src="images/confirmation.gif">', baseZ: 10000});
    let response;
    let body;
    try {
        const request = Object.assign({credentials: 'same-origin'}, fetchOptions, {signal: controller.signal});
        if (!['GET', 'HEAD', 'OPTIONS'].includes((request.method || 'GET').toUpperCase())) {
            request.headers = Object.assign({}, request.headers, await csrfHeaders());
        }
        response = await fetch(url, request);
        if (response.redirected) {
            window.location.assign(response.url);
            return;
        }
        const text = response.status === 204 ? '' : await response.text();
        try { body = text ? JSON.parse(text) : null; } catch (ignored) { body = null; }
        if (!response.ok) throw new Error(body && body.message || `Request failed (${response.status})`);
        if (text && body === null) throw new Error('Unexpected server response. Please retry.');
    } catch (error) {
        const message = error.name === 'AbortError' ? 'Request timed out. Please retry.' : error.message;
        if (options.error) options.error(message);
        else showError(i18n.translate(message || 'Command is not completed'));
        return;
    } finally {
        clearTimeout(timer);
        if (options.block) $.unblockUI();
    }
    if (response.status === 202 && !await showInputToken(options.deviceId)) return;
    if (options.success) await options.success(body);
    return body === null ? true : body;
}

function initCommand($button, command, deviceId, options) {
    options = options || {};
    if (options.init) {
        options.init();
    }
    $button.off('click');
    $button.click(async () => {
        if (options.before) {
            const message = options.before();
            if (message) {
                showError(i18n.translate(message));
                return;
            }
        }
        if (options.device) {
            deviceId = options.device();
        }
        await fetchWithRedirect(`/api/device/${deviceId}/command`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({type: command, payload: options.payload ? options.payload() : []})
        },
        {
            error: message => {
                showError(i18n.translate(message || 'Command is not completed'));
                if (options.error) {
                    options.error(message);
                }
            },
            success: () => {
                if (options.after) {
                    options.after();
                }
            },
            deviceId: deviceId,
            block: true
        });
    });
}

function initConfig($input, $elements, parameter, config, deviceId, options) {
    options = options || {};
    if (options.init) {
        options.init();
    } else {
        if (options.initValue) {
            $input.val(options.initValue());
        } else {
            let done = false;
            config.filter(c => c.parameter == parameter).forEach(c => {
                $input.val(c.value);
                done = true;
            });
            if (done == false) {
                $input.val(options.defaultValue ? options.defaultValue() : '');
            }
        }
    }
    if (!Array.isArray($elements)) {
        $elements = [$elements];
    }
    $elements.forEach($element => {
        $element.off('click');
        $element.click(async () => {
            if (options.before) {
                options.before();
            }
            await fetchWithRedirect(`/api/device/${deviceId}/config`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({parameter: parameter, value: options.value ? options.value() : $input.val()})
            },
            {
                error: message => {
                    showError(i18n.translate(message || 'Command is not completed'));
                    if (options.error) {
                        options.error(message);
                    }
                },
                success: () => {
                    if (options.after) {
                        options.after();
                    }
                },
                block: true
            });
        });
    });
}

function initCheck($check, parameter, config, deviceId, defaultValue) {
    let done = false;
    config.filter(c => c.parameter == parameter).forEach(c => {
        $check[0].checked = c.value == '1';
        done = true;
    });
    if (done == false) {
        $check[0].checked = !!defaultValue;
    }
    $check.off('click');
    $check.click(async () => {
        const value = $check[0].checked == true ? '1' : '0';
        await fetchWithRedirect(`/api/device/${deviceId}/config`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({parameter: parameter, value: value})
        },
        {
            error: message => {
                $check[0].checked = !$check[0].checked;
                showError(i18n.translate(message || 'Command is not completed'));
            },
            block: true
        });
    });
}

async function showInputToken(deviceId) {

    const $modalToken = $('#input-token');

    const $inputToken = $('#input-token-input');
    const $closeToken = $('#input-token-close');
    const $executeToken = $('#input-token-execute');

    $inputToken.val('');
    $executeToken.prop('disabled', false);

    return new Promise(resolve => {

        function hide(confirmed) {

            $closeToken.off('click');
            $executeToken.off('click');
            $modalToken.modal('hide');

            resolve(!!confirmed);
        }

        $modalToken.on('shown.bs.modal', function onShow() {
            $modalToken.off('shown.bs.modal', onShow);
            $closeToken.click(() => {
                hide();
            });
            $executeToken.click(async () => {
                const token = $inputToken.val().trim();
                if (!token) { showError(i18n.translate("Token should not be empty.")); return; }
                $executeToken.prop("disabled", true);
                await fetchWithRedirect(deviceId ? `/api/device/${deviceId}/execute/${encodeURIComponent(token)}` : `/api/user/token/${encodeURIComponent(token)}`, {method: 'POST'},
                {
                    error: message => {
                        showError(i18n.translate(message || 'Command is not completed'));
                    },
                    block: true,
                    success: () => hide(true)
                });
                $executeToken.prop('disabled', false);
            });
        });

        $modalToken.modal({
            backdrop: 'static',
            focus: true,
            keyboard: false,
            show: true
        });
    });
}

module.exports = {csrfHeaders, showInputToken, fetchWithRedirect, initCommand, initConfig, initCheck};