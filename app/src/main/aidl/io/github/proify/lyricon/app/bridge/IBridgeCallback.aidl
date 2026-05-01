/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

// IBridgeCallback.aidl
package io.github.proify.lyricon.app.bridge;

interface IBridgeCallback {
    void onReply(in Bundle data);
}