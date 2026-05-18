/*
 * Copyright 2020 Sergey Shadchin (sergei.shadchin@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

'use strict';

const i18n = require('./i18n.js');

function getGijgoLocale() {
    const locale = i18n.locale.toLowerCase();
    const messages = window.gj && gj.core ? gj.core.messages : null;
    if (messages && messages[locale]) {
        return locale;
    }
    return 'ru-ru';
}

module.exports = getGijgoLocale;
