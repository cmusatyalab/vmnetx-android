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
#include <jni.h>
#include <android/log.h>

#define TAG "vmnetx-socket"

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

JNIEXPORT jint JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceConnectFd(JNIEnv *env, jobject obj, jstring h, jstring p) {
    // Connect
    const char *host = (*env)->GetStringUTFChars(env, h, NULL);
    const char *port = (*env)->GetStringUTFChars(env, p, NULL);
    int fd = connect_host(host, port);
    (*env)->ReleaseStringUTFChars(env, h, host);
    (*env)->ReleaseStringUTFChars(env, p, port);
    return fd;
}
