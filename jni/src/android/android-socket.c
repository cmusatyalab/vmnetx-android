/*
 * android-socket - Viewer socket setup glue
 *
 * Copyright (C) 2014 Carnegie Mellon University
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of version 2 of the GNU General Public License as published
 * by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <stdint.h>
#include <stdbool.h>
#include <unistd.h>
#include <jni.h>
#include <android/log.h>

#define TAG "vmnetx-socket"

enum connect_status {
    CONNECT_CONTINUE = 0,
    CONNECT_DONE = 1,
    CONNECT_FAILED = 2,
};

static int connect_host(const char *host, const char *port) {
    const struct addrinfo hints = {
        .ai_family = AF_UNSPEC,
        .ai_socktype = SOCK_STREAM,
        .ai_flags = AI_ADDRCONFIG,
    };
    struct addrinfo *info;
    int ret = getaddrinfo(host, port, &hints, &info);
    if (ret) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Lookup failure: %s", gai_strerror(ret));
        return -1;
    }

    int fd = -1;
    for (struct addrinfo *cur = info; cur; cur = cur->ai_next) {
        fd = socket(cur->ai_family, cur->ai_socktype, cur->ai_protocol);
        if (fd == -1)
            continue;
        if (connect(fd, cur->ai_addr, cur->ai_addrlen)) {
            close(fd);
            fd = -1;
            continue;
        }
        break;
    }
    if (fd == -1)
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Connect failure: %s:%s", host, port);

    freeaddrinfo(info);
    return fd;
}

static bool send_reliably(int fd, const void *buf, size_t len) {
    while (len > 0) {
        ssize_t count = send(fd, buf, len, 0);
        if (count < 0)
            break;
        buf += count;
        len -= count;
    }
    if (len) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failure sending on viewer connection");
        return false;
    }
    return true;
}

static bool recv_reliably(int fd, void *buf, size_t len) {
    while (len > 0) {
        ssize_t count = recv(fd, buf, len, 0);
        if (count <= 0)
            break;
        buf += count;
        len -= count;
    }
    if (len) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failure receiving on viewer connection");
        return false;
    }
    return true;
}

JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_ViewerConnectionProcessor_Connect(JNIEnv *env, jobject obj, jstring h, jstring p) {
    // Find callbacks
    jclass cls = (*env)->GetObjectClass(env, obj);
    jmethodID connect_method = (*env)->GetMethodID(env, cls, "OnConnect", "(I)V");
    jmethodID recv_method = (*env)->GetMethodID(env, cls, "OnReceiveMessage", "([B)I");
    if (!connect_method || !recv_method) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Couldn't get callback methods");
        return;
    }

    // Connect
    const char *host = (*env)->GetStringUTFChars(env, h, NULL);
    const char *port = (*env)->GetStringUTFChars(env, p, NULL);
    int fd = connect_host(host, port);
    (*env)->ReleaseStringUTFChars(env, h, host);
    (*env)->ReleaseStringUTFChars(env, p, port);
    if (fd == -1)
        return;
    (*env)->CallVoidMethod(env, obj, connect_method, (jint) fd);

    // Run protocol
    int status;
    do {
        // Length
        uint32_t len;
        if (!recv_reliably(fd, &len, sizeof(len))) {
            close(fd);
            return;
        }
        len = ntohl(len);

        // Data
        jbyteArray data = (*env)->NewByteArray(env, len);
        jbyte *buf = (*env)->GetByteArrayElements(env, data, NULL);
        if (!recv_reliably(fd, buf, len)) {
            (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
            close(fd);
            return;
        }
        (*env)->ReleaseByteArrayElements(env, data, buf, 0);
        status = (*env)->CallIntMethod(env, obj, recv_method, data);
    } while (status == CONNECT_CONTINUE);

    // Clean up on failure
    if (status != CONNECT_DONE)
        close(fd);
}

JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_ViewerConnectionProcessor_SendMessage(JNIEnv *env, jobject obj, jint fd, jbyteArray data) {
    jbyte *buf = (*env)->GetByteArrayElements(env, data, NULL);
    jsize len = (*env)->GetArrayLength(env, data);
    uint32_t n_len = htonl(len);
    bool ok = send_reliably(fd, &n_len, sizeof(n_len));
    if (ok)
        send_reliably(fd, buf, len);
    (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
}
