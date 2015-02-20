/*
   Copyright (C) 2014 Carnegie Mellon University
   Copyright (C) 2013 Iordan Iordanov
   Copyright (C) 2010 Red Hat, Inc.

   This library is free software; you can redistribute it and/or
   modify it under the terms of the GNU Lesser General Public
   License as published by the Free Software Foundation; either
   version 2.1 of the License, or (at your option) any later version.

   This library is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
   Lesser General Public License for more details.

   You should have received a copy of the GNU Lesser General Public
   License along with this library; if not, see <http://www.gnu.org/licenses/>.
*/
#include <glib/gi18n.h>
#include <android/log.h>

#include <sys/stat.h>
#include "android-spice-widget.h"
#include "spice-audio.h"
#include "android-io.h"
#include "android-spicy.h"
#include "android-service.h"

#define TAG "vmnetx-spicy"

static void connection_destroy(struct spice_context *ctx);

static void channel_open_fd(SpiceChannel *channel, gint with_tls,
                            gpointer data)
{
    struct spice_context *ctx = data;
    uiCallbackGetFd(ctx, channel);
}

static void main_channel_event(SpiceChannel *channel, SpiceChannelEvent event,
                               gpointer data)
{
    struct spice_context *ctx = data;

    switch (event) {
    case SPICE_CHANNEL_CLOSED:
    case SPICE_CHANNEL_ERROR_IO:
    case SPICE_CHANNEL_ERROR_TLS:
    case SPICE_CHANNEL_ERROR_LINK:
    case SPICE_CHANNEL_ERROR_CONNECT:
    case SPICE_CHANNEL_ERROR_AUTH:
        connection_disconnect(ctx);
        break;
    default:
        break;
    }
}

static void channel_new(SpiceSession *s, SpiceChannel *channel, gpointer data)
{
    struct spice_context *ctx = data;
    int id;

    g_object_get(channel, "channel-id", &id, NULL);
    ctx->channels++;
    SPICE_DEBUG("new channel (#%d)", id);

    g_signal_connect(channel, "open-fd",
                     G_CALLBACK(channel_open_fd), ctx);

    if (SPICE_IS_MAIN_CHANNEL(channel)) {
        SPICE_DEBUG("new main channel");
        g_signal_connect(channel, "channel-event",
                         G_CALLBACK(main_channel_event), ctx);
    }

    if (SPICE_IS_DISPLAY_CHANNEL(channel)) {
        if (ctx->display != NULL)
            return;
        SPICE_DEBUG("new display channel (#%d)", id);
        ctx->display = spice_display_new(ctx, id);
        ctx->display_channel = id;
    }

    if (SPICE_IS_PLAYBACK_CHANNEL(channel)) {
        SPICE_DEBUG("new audio channel");
        spice_audio_get(s, NULL);
    }
}

static void channel_destroy(SpiceSession *s, SpiceChannel *channel, gpointer data)
{
    //__android_log_write(ANDROID_LOG_DEBUG, TAG, "channel_destroy called");

    struct spice_context *ctx = data;
    int id;

    g_object_get(channel, "channel-id", &id, NULL);

    if (SPICE_IS_DISPLAY_CHANNEL(channel)) {
        if (ctx->display && ctx->display_channel == id) {
            SPICE_DEBUG("zap display channel (#%d)", id);
            ctx->display = NULL;
        }
    }

    ctx->channels--;
    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "Number of channels: %d", ctx->channels);
    if (ctx->channels == 0) {
        connection_destroy(ctx);
    }
}

void connection_connect(struct spice_context *ctx)
{
    g_signal_connect(ctx->session, "channel-new",
                     G_CALLBACK(channel_new), ctx);
    g_signal_connect(ctx->session, "channel-destroy",
                     G_CALLBACK(channel_destroy), ctx);
    spice_session_open_fd(ctx->session, -1);
}

void connection_disconnect(struct spice_context *ctx)
{
    if (ctx->disconnecting)
        return;
    ctx->disconnecting = true;
    spice_session_disconnect(ctx->session);
}

static void connection_destroy(struct spice_context *ctx)
{
    //__android_log_write(ANDROID_LOG_DEBUG, TAG, "connection_destroy called");
    g_object_unref(ctx->session);
    ctx->session = NULL;
    uiCallbackDisconnect(ctx);
}
