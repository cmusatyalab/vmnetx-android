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

#include <endian.h>
#include <jni.h>
#include <android/bitmap.h>
#include <android/keycodes.h>
#include <android/log.h>

#include "android-spice-widget.h"
#include "android-spice-widget-priv.h"
#include "android-io.h"
#include "android-service.h"
#include "keymap.h"

#define TAG "vmnetx-io"

JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceSetFd(JNIEnv *env, jobject obj, jlong cookie, jint fd) {
    SpiceChannel *channel = (SpiceChannel *) cookie;
    if (!spice_channel_open_fd(channel, fd)) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to open FD");
    }
}


static void updatePixels (uint32_t *dest, uint32_t *source, int x, int y, int width, int height, int buffwidth, int buffheight) {
    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "Drawing x: %d, y: %d, w: %d, h: %d, wBuf: %d, hBuf: %d", x, y, width, height, wBuf, hBuf);
    uint32_t *sourcepix = &source[(buffwidth * y) + x];
    uint32_t *destpix   = &dest[(buffwidth * y) + x];

    for (int i = 0; i < height; i++) {
        for (int j = 0; j < width; j++) {
            // ARGB -> R G B X
            uint32_t value = sourcepix[j] << 8;
#if BYTE_ORDER == LITTLE_ENDIAN
            value = __builtin_bswap32(value);
#endif
            destpix[j] = value;
        }
        sourcepix = sourcepix + buffwidth;
        destpix   = destpix + buffwidth;
    }
}

JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceUpdateBitmap (JNIEnv* env, jobject obj, jlong context, jobject bitmap, gint x, gint y, gint width, gint height) {
    struct spice_context *ctx = (struct spice_context *) context;
    void* pixels;
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(ctx->display);

    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "AndroidBitmap_lockPixels() failed!");
        return;
    }
    //__android_log_write(ANDROID_LOG_DEBUG, TAG, "Copying new data into pixels.");
    updatePixels (pixels, d->data, x, y, width, height, d->width, d->height);
    AndroidBitmap_unlockPixels(env, bitmap);
}

static int update_mask (SpiceDisplayPrivate *d, int button, gboolean down) {
    int update = 0;
    if (button == SPICE_MOUSE_BUTTON_LEFT)
        update = SPICE_MOUSE_BUTTON_MASK_LEFT;
    else if (button == SPICE_MOUSE_BUTTON_MIDDLE)
        update = SPICE_MOUSE_BUTTON_MASK_MIDDLE;
    else if (button == SPICE_MOUSE_BUTTON_RIGHT)
        update = SPICE_MOUSE_BUTTON_MASK_RIGHT;
    if (down) {
        d->mouse_button_mask |= update;
    } else {
        d->mouse_button_mask &= ~update;
    }
    return d->mouse_button_mask;
}


/* JNI functions related to input (keyboard, mouse), and output (display). */
/***************************************************************************/


JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceRequestResolution(JNIEnv* env, jobject obj, jlong context, jint x, jint y) {
    struct spice_context *ctx = (struct spice_context *) context;
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(ctx->display);

    spice_main_update_display(d->main, get_display_id(ctx->display), 0, 0, x, y, TRUE);
    spice_main_set_display_enabled(d->main, -1, TRUE);
    // TODO: Sending the monitor config right away may be causing guest OS to shut down.
    /*
    if (spice_main_send_monitor_config(d->main)) {
        __android_log_write(ANDROID_LOG_DEBUG, TAG, "Successfully sent monitor config");
    } else {
        __android_log_write(ANDROID_LOG_WARN, TAG, "Failed to send monitor config");
    }*/
}


JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceKeyEvent(JNIEnv * env, jobject  obj, jlong context, jboolean down, jint hardware_keycode) {
    struct spice_context *ctx = (struct spice_context *) context;
    SpiceDisplayPrivate* d = SPICE_DISPLAY_GET_PRIVATE(ctx->display);

    SPICE_DEBUG("%s %s: keycode: %d", __FUNCTION__, "Key", hardware_keycode);

    if (!d->inputs)
        return;

    // The lookup table doesn't include mappings that require multiple
    // keypresses, so translate them here
    int shift = keymap_android2xtkbd[AKEYCODE_SHIFT_LEFT];
    switch (hardware_keycode) {
    case AKEYCODE_AT:
        hardware_keycode = AKEYCODE_2;
        break;
    case AKEYCODE_POUND:
        hardware_keycode = AKEYCODE_3;
        break;
    case AKEYCODE_STAR:
        hardware_keycode = AKEYCODE_8;
        break;
    default:
        shift = 0;
        break;
    }

    int scancode = 0;
    if (hardware_keycode > 0 &&
            hardware_keycode < G_N_ELEMENTS(keymap_android2xtkbd)) {
        scancode = keymap_android2xtkbd[hardware_keycode];
    }
    if (!scancode)
        return;
    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "Converted Android key %d to scancode %d", hardware_keycode, scancode);

    if (down) {
        if (shift)
            send_key(ctx->display, shift, 1);
        send_key(ctx->display, scancode, 1);
    } else {
        send_key(ctx->display, scancode, 0);
        if (shift)
            send_key(ctx->display, shift, 0);
    }
}


JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceButtonEvent(JNIEnv * env, jobject  obj, jlong context, jint x, jint y, jint type) {
    struct spice_context *ctx = (struct spice_context *) context;
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(ctx->display);
    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "Pointer event: %d at x: %d, y: %d", type, x, y);

    if (!d->inputs || (x >= 0 && x < d->width && y >= 0 && y < d->height)) {

        gboolean down = (type & PTRFLAGS_DOWN) != 0;
        int mouseButton = type &~ PTRFLAGS_DOWN;
        int newMask = update_mask(d, mouseButton, down);

        gint dx;
        gint dy;
        switch (d->mouse_mode) {
        case SPICE_MOUSE_MODE_CLIENT:
            //__android_log_write(ANDROID_LOG_DEBUG, TAG, "spice mouse mode client");
            spice_inputs_position(d->inputs, x, y, d->channel_id, newMask);
            break;
        case SPICE_MOUSE_MODE_SERVER:
            //__android_log_write(ANDROID_LOG_DEBUG, TAG, "spice mouse mode server");
            dx = d->mouse_last_x != -1 ? x - d->mouse_last_x : 0;
            dy = d->mouse_last_y != -1 ? y - d->mouse_last_y : 0;
            spice_inputs_motion(d->inputs, dx, dy, newMask);
            d->mouse_last_x = x;
            d->mouse_last_y = y;
            break;
        default:
            g_warn_if_reached();
            break;
        }

        if (mouseButton != SPICE_MOUSE_BUTTON_INVALID) {
            if (down) {
                //__android_log_write(ANDROID_LOG_DEBUG, TAG, "Button press");
                spice_inputs_button_press(d->inputs, mouseButton, newMask);
            } else {
                //__android_log_write(ANDROID_LOG_DEBUG, TAG, "Button release");
                // This sleep is an ugly hack to prevent stuck buttons after a drag/drop gesture.
                usleep(50000);
                spice_inputs_button_release(d->inputs, mouseButton, newMask);
            }
        }
    }
}


/* Callbacks to the UI layer to draw screen updates and invalidate part of the screen,
 * or to request a new bitmap. */

void uiCallbackGetFd (struct spice_context *ctx, SpiceChannel *channel) {
    // Ask the UI to connect a file descriptor for us.
    (*ctx->jenv)->CallVoidMethod(ctx->jenv, ctx->jni_connector, ctx->jni_get_fd, (jlong) channel);
}

void uiCallbackInvalidate (struct spice_context *ctx, gint x, gint y, gint w, gint h) {
    // Tell the UI that it needs to send in the bitmap to be updated and to redraw.
    (*ctx->jenv)->CallVoidMethod(ctx->jenv, ctx->jni_connector, ctx->jni_graphics_update, 0, x, y, w, h);
}

void uiCallbackSettingsChanged (struct spice_context *ctx, gint instance, gint width, gint height, gint bpp) {
    // Ask for a new bitmap from the UI.
    (*ctx->jenv)->CallVoidMethod(ctx->jenv, ctx->jni_connector, ctx->jni_settings_changed, instance, width, height, bpp);
}

void uiCallbackCursorConfig (struct spice_context *ctx, bool absolute_mouse) {
    (*ctx->jenv)->CallVoidMethod(ctx->jenv, ctx->jni_connector, ctx->jni_cursor_config, absolute_mouse);
}
