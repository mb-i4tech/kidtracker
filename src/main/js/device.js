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

const moment = require('moment/min/moment-with-locales.min.js');
const i18n = require('./i18n.js');
const {showWarning, showError} = require('./notification.js');
const {showInputToken, fetchWithRedirect, initCommand, initConfig, initCheck} = require('./util.js');

const $modal = $('#show-user-devices');
const $editModal = $('#edit-device');

const LAST_MESSAGE_TIME_FORMAT = 'D MMMM YYYY HH:mm ddd';

function onStatus(status) {
    status = Array.isArray(status) ? status : [status];
    status.forEach(s => {
        const $tr = $(`#kid-device-${s.deviceId}`).parent();
        $('div.user-device-name-deviceid', $tr).toggleClass('online', s.online).toggleClass('offline', !s.online);
        if (s.date) {
            const $time = $('div.user-device-name-time', $tr);
            $time.attr('data-timestamp', s.date);
            const fromNow = $time.attr('data-fromnow') == 'true';
            $time.text(fromNow ? moment(s.date).fromNow() : moment(s.date).format(LAST_MESSAGE_TIME_FORMAT));
        }
    });
}

async function showDevice(stompClient) {

    const $modal = $('#show-user-devices');
    const $add = $('#user-devices-add');
    const $close = $('#user-devices-close');

    async function renderDevice() {

        const $tbody = $('<tbody>');
        const kids = await fetchWithRedirect(`/api/user/kids/info`);
        if (!Array.isArray(kids)) return;
        kids.forEach(k => {
            const $tr = $('<tr>');
            const $thThumb = $('<th>').addClass('user-device-thumb');
            const $thName = $('<th>').addClass('user-device-name');
            const $td = $('<td>').addClass('user-device-others');
            const $thumb = k.thumb ? $('<img>').attr('src', k.thumb).addClass('thumb-img') : $('<div>').addClass('thumb-placeholder');
            $thThumb.append($thumb).attr('id', `kid-device-${k.deviceId}`);
            const $name = $('<div>').text(k.name);
            const $deviceId = $('<div>').text(k.deviceId).addClass('user-device-name-deviceid');
            const $time = $('<div>').addClass('user-device-name-time').attr('data-fromnow', false);
            $thName.append($name).append($deviceId).append($time);
            const $spanName = $('<span>').addClass('user-device-other-user-name').text();
            k.users.forEach(u => {
                $td.append($('<div>').append($('<span>').addClass('user-device-other-user').append($('<b>').text(u.name)).append(` ${u.phone}`)));
            })
            $tr.append($thThumb).append($thName).append($td);
            $tbody.append($tr);
        });

        $('table.table', $modal).empty().append($tbody);

        kids.forEach(k => {
            const $thumb = $(`#kid-device-${k.deviceId}`);
            $thumb.off('click');
            $thumb.on('click', async () => {
                await editDevice(k);
                await renderDevice();
                if (stompClient && stompClient.connected) stompClient.send(`/user/${stompClient.userId}/status`);
            });
            const $time = $('div.user-device-name-time', $thumb.parent());
            $time.off('click');
            $time.click(() => {
                if ($time[0].hasAttribute('data-timestamp')) {
                    const fromNow = $time.attr('data-fromnow') == 'true';
                    const timestamp = $time.attr('data-timestamp');
                    $time.attr('data-fromnow', !fromNow);
                    $time.text(!fromNow ? moment(timestamp).fromNow() : moment(timestamp).format(LAST_MESSAGE_TIME_FORMAT));
                }
            });
        });
    }

    await renderDevice();

    var subscription = null;

    return new Promise(resolve => {

        function hide() {

            if (subscription) subscription.unsubscribe();

            $close.off('click');
            $add.off('click');

            $modal.modal('hide');
            resolve(null);
        }

        $modal.on('shown.bs.modal', function onShow() {
            $modal.off('shown.bs.modal', onShow);
            if (stompClient && stompClient.connected) {
                subscription = stompClient.subscribe('/user/queue/status', response => onStatus(JSON.parse(response.body)));
            }
            $add.click(async () => {
                await editDevice();
                await renderDevice();
                if (stompClient && stompClient.connected) stompClient.send(`/user/${stompClient.userId}/status`);
            });
            $close.click(() => {
                hide();
            });
        });

        $modal.modal({
	        backdrop: 'static',
	        focus: true,
	        keyboard: false,
	        show: true
        });
    });
}

async function editDevice(kid) {

    const $remove = $('#edit-device-remove');
    const $removeThumb = $('#edit-device-remove-thumb');
    const $addThumb = $('#edit-device-add-thumb');
    const $upload = $('#edit-device-upload');
    const $add = $('#edit-device-add');
    const $close = $('#edit-device-close');

    const $thumbRow = $('.thumb-row', $editModal);
    const $deviceId = $('#device-deviceid');
    const $name = $('#device-name');

    const $info = $('div.alert-info', $editModal);

    const $status = $('#edit-device-status');
    const $copy = $('#edit-device-copy');
    let smsText = '';
    let busy = false;
    const create = !kid;
    $('.onboarding-only', $editModal).toggle(create);
    $status.text('');
    $copy.hide();
    $add.text(i18n.translate('Request confirmation code'));
    $close.attr('aria-label', i18n.translate('Cancel'));
    $('.modal-title', $editModal).text(i18n.translate(create ? 'Add a child’s watch' : 'Edit'));
    if (create) {
        kid = {};
        const serverConfig = await fetchWithRedirect('/api/user/config');
        if (serverConfig && isPublicEndpoint(serverConfig.publicHost, serverConfig.publicPort)) {
            smsText = `pw,123456,ip,${serverConfig.publicHost},${serverConfig.publicPort}#`;
            $copy.show();
            const $sms = $('<span>').addClass('user-add-device-sms').text(smsText);
            const $password = $('<span>').addClass('user-add-device-sms').text('123456');
            $info.html(i18n.format('Send text message to the device {}If the device password was changed, put it instead of {}', ['<br>'+$sms[0].outerHTML+'<br>', $password[0].outerHTML]));
        } else {
            $info.text(i18n.translate('Public device endpoint is not configured. Ask the administrator before configuring the watch.'));
        }
    }

    $info.toggle(create);

    $remove.toggle(create == false);
    $upload.toggle(create == false);
    $add.toggle(create == true);

    function render() {

        if (kid.thumb) {
            const $img = $('<img>').attr('src', kid.thumb).addClass('thumb-img-lg');
            $thumbRow.empty().append($img)
        } else {
            const $input = $('<input>').attr('type', 'file').prop('hidden', true);
            const $label = $('<label>').addClass('thumb-placeholder-lg').append($input);
            $thumbRow.empty().append($label);
        }
        $removeThumb.toggle(!!kid.thumb);
        $addThumb.toggle(!kid.thumb);
        $('label > input', $editModal).each(function (i) {
            $(this).off('change');
            if (!kid.thumb) {
                $(this).change(() => {
                    const reader = new FileReader();
                    reader.onload = function(e) {
                        kid.thumb = e.target.result;
                        kid.name = $name.val();
                        kid.deviceId = $deviceId.val();
                        render();
                    };
                    reader.readAsDataURL($(this)[0].files[0]);
                });
            }
        })

        $deviceId.val(kid.deviceId);
        $name.val(kid.name);

        $deviceId.prop('disabled', create == false);
    }

    render();
    // Localize only leaf text nodes; do not destroy controls or nested markup.
    $editModal.find('label[for], small, p.onboarding-only, .onboarding-steps li, #edit-device-copy').each(function () { i18n.apply($(this)); });

    return new Promise(resolve => {

        function hide() {

            $remove.off('click');
            $removeThumb.off('click');
            $addThumb.off('click');
            $upload.off('click');
            $add.off('click');
            $close.off('click');
            $copy.off('click');

            $editModal.modal('hide');
            resolve(null);
        }

        $editModal.on('shown.bs.modal', async function onShow() {
            $editModal.off('shown.bs.modal', onShow);
            $remove.click(async () => {
                await fetchWithRedirect('/api/user/kid', {
                    method: 'DELETE',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify(kid)
                },
                {
                    error: message => {
                        showError(i18n.translate(message || 'Command is not completed'));
                    },
                    success: hide
                });
            });
            $removeThumb.click(async () => {
                delete kid.thumb;
                kid.name = $name.val();
                kid.deviceId = $deviceId.val();
                render();
            });
            $upload.click(async () => {
                if (!$name.val()) {
                    showError(i18n.translate('Name should not be empty.'))
                } else {
                    kid.name = $name.val();
                    await fetchWithRedirect('/api/user/kid', {
                        method: 'PUT',
                        headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify(kid)
                    },
                    {
                        error: message => {
                            showError(i18n.translate(message || 'Command is not completed'));
                        },
                        success: hide
                    });
                }
            });
            $copy.off('click').on('click', async () => {
                try {
                    await navigator.clipboard.writeText(smsText);
                    $status.text(i18n.translate('SMS text copied. Send it yourself to the watch SIM number.'));
                } catch (e) {
                    $status.text(i18n.translate('Copy is unavailable. Select and copy the SMS text above.'));
                }
            });
            $add.click(async () => {
                if (busy) return;
                const name = $name.val().trim();
                const deviceId = $deviceId.val().trim();
                $name.attr('aria-invalid', !name);
                $deviceId.attr('aria-invalid', !/^[0-9]{5,20}$/.test(deviceId));
                if (!name) {
                    $status.text(i18n.translate('Name should not be empty.')); $name.trigger('focus'); return;
                }
                if (!/^[0-9]{5,20}$/.test(deviceId)) {
                    $status.text(i18n.translate('Enter the numeric device ID (5–20 digits) printed on the watch or its label, not the SIM phone number.'));
                    $deviceId.trigger('focus'); return;
                }
                kid.deviceId = deviceId; kid.name = name;
                const request = {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(kid)};
                busy = true; $add.prop('disabled', true); $close.prop('disabled', true);
                $status.text(i18n.translate('Sending request to the server. Watch connection is not yet confirmed.'));
                await fetchWithRedirect('/api/user/kid', request, {
                    error: () => $status.text(i18n.translate('Request failed. Check the watch power, SIM data and server settings, then retry. Your entries are saved.')),
                    accepted: () => $status.text(i18n.translate('Request accepted. Confirmation is still required.')),
                    resend: error => fetchWithRedirect('/api/user/kid', request, {skipToken: true, error}),
                    success: async () => {
                        // Token execution must also be reflected in the authenticated assignment list.
                        const kids = await fetchWithRedirect('/api/user/kids/info');
                        if (Array.isArray(kids) && kids.some(k => String(k.deviceId) === deviceId)) hide();
                        else $status.text(i18n.translate('Assignment is not confirmed yet. Reopen the child list to check before requesting another code.'));
                    }
                });
                busy = false; $add.prop('disabled', false); $close.prop('disabled', false);
            });
            $close.click(() => {
                hide();
            });
        });

        $editModal.modal({
            backdrop: 'static',
            focus: true,
            keyboard: false,
            show: true
        });
    });
}

// Reject LAN/loopback literals and local hostnames; never derive SMS from window.location.
function isPublicEndpoint(host, port) {
    if (typeof host !== 'string' || !Number.isInteger(Number(port)) || Number(port) < 1 || Number(port) > 65535) return false;
    if (!/^[a-z0-9.-]+$/i.test(host) || !host.includes('.') || /(?:\.local|\.localhost|\.internal|\.lan|\.home)$/i.test(host)) return false;
    if (/^[0-9.]+$/.test(host)) {
        const p = host.split('.').map(Number);
        if (p.length !== 4 || p.some(n => n < 0 || n > 255)) return false;
        if ([0,10,127].includes(p[0]) || p[0] >= 224 || (p[0] === 192 && p[1] === 168) || (p[0] === 172 && p[1] >= 16 && p[1] <= 31) || (p[0] === 169 && p[1] === 254) || (p[0] === 100 && p[1] >= 64 && p[1] <= 127)) return false;
    }
    return true;
}
module.exports = showDevice;