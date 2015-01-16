/**
 * Copyright (C) 2013 Iordan Iordanov
 *
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
 * USA.
 */

#ifndef ANDROID_SERVICE_H
#define ANDROID_SERVICE_H

#include <jni.h>
#include <android/bitmap.h>
#include <glib.h>

struct spice_main_thread {
    JavaVM               *jvm;  // only for thread startup
    JNIEnv               *jenv;
    jmethodID             jni_get_fd;
    jmethodID             jni_settings_changed;
    jmethodID             jni_graphics_update;
    jmethodID             jni_mouse_mode;
    jmethodID             jni_cursor_config;
    jmethodID             jni_disconnect;
};

struct spice_context {
    struct spice_main_thread *thr;
    struct spice_connection  *conn;
    struct _SpiceDisplay     *display;
    jobject                   jni_connector;
    int                       connections;
};

#endif
