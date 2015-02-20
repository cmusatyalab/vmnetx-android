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

static void connection_destroy(spice_connection *conn);

static void channel_open_fd(SpiceChannel *channel, gint with_tls,
                            gpointer data)
{
    spice_connection *conn = data;
    uiCallbackGetFd(conn->ctx, channel);
}

static void main_channel_event(SpiceChannel *channel, SpiceChannelEvent event,
                               gpointer data)
{
    spice_connection *conn = data;

    switch (event) {
    case SPICE_CHANNEL_CLOSED:
    case SPICE_CHANNEL_ERROR_IO:
    case SPICE_CHANNEL_ERROR_TLS:
    case SPICE_CHANNEL_ERROR_LINK:
    case SPICE_CHANNEL_ERROR_CONNECT:
    case SPICE_CHANNEL_ERROR_AUTH:
        connection_disconnect(conn);
        break;
    default:
        break;
    }
}

static void channel_new(SpiceSession *s, SpiceChannel *channel, gpointer data)
{
    spice_connection *conn = data;
    int id;

    g_object_get(channel, "channel-id", &id, NULL);
    conn->channels++;
    SPICE_DEBUG("new channel (#%d)", id);

    g_signal_connect(channel, "open-fd",
                     G_CALLBACK(channel_open_fd), conn);

    if (SPICE_IS_MAIN_CHANNEL(channel)) {
        SPICE_DEBUG("new main channel");
        g_signal_connect(channel, "channel-event",
                         G_CALLBACK(main_channel_event), conn);
    }

    if (SPICE_IS_DISPLAY_CHANNEL(channel)) {
        if (conn->display != NULL)
            return;
        SPICE_DEBUG("new display channel (#%d)", id);
        conn->display = spice_display_new(conn->ctx, conn->session, id);
        conn->display_channel = id;
        conn->ctx->display = conn->display;
    }

    if (SPICE_IS_PLAYBACK_CHANNEL(channel)) {
        SPICE_DEBUG("new audio channel");
        spice_audio_get(s, NULL);
    }
}

static void channel_destroy(SpiceSession *s, SpiceChannel *channel, gpointer data)
{
    //__android_log_write(ANDROID_LOG_DEBUG, TAG, "channel_destroy called");

    spice_connection *conn = data;
    int id;

    g_object_get(channel, "channel-id", &id, NULL);

    if (SPICE_IS_DISPLAY_CHANNEL(channel)) {
        if (conn->display && conn->display_channel == id) {
            SPICE_DEBUG("zap display channel (#%d)", id);
            conn->display = NULL;
        }
    }

    conn->channels--;
    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "Number of channels: %d", conn->channels);
    if (conn->channels > 0) {
        return;
    }

    connection_destroy(conn);
}

spice_connection *connection_new(struct spice_context *ctx)
{
    spice_connection *conn;

    conn = g_new0(spice_connection, 1);
    conn->ctx = ctx;
    conn->session = spice_session_new();
    g_signal_connect(conn->session, "channel-new",
                     G_CALLBACK(channel_new), conn);
    g_signal_connect(conn->session, "channel-destroy",
                     G_CALLBACK(channel_destroy), conn);
    return conn;
}

void connection_connect(spice_connection *conn)
{
    conn->disconnecting = false;
    spice_session_open_fd(conn->session, -1);
}

void connection_disconnect(spice_connection *conn)
{
    if (conn->disconnecting)
        return;
    conn->disconnecting = true;
    spice_session_disconnect(conn->session);
}

static void connection_destroy(spice_connection *conn)
{
    struct spice_context *ctx = conn->ctx;
    //__android_log_write(ANDROID_LOG_DEBUG, TAG, "connection_destroy called");
    g_object_unref(conn->session);
    free(conn);
    uiCallbackDisconnect(ctx);
}
