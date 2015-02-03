/* -*- Mode: C; c-basic-offset: 4; indent-tabs-mode: nil -*- */
/*
   Copyright (C) 2013 Iordan Iordanov

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
#ifndef _ANDROID_SPICY_H
#define _ANDROID_SPICY_H

#include <glib/gi18n.h>

#include <sys/stat.h>
#include "glib-compat.h"
#include "spice-audio.h"
#include "spice-common.h"
#include "spice-cmdline.h"
#include "android-service.h"
#include "android-spice-widget.h"

typedef struct spice_connection spice_connection;

typedef struct _SpiceWindow SpiceWindow;
typedef struct _SpiceWindowClass SpiceWindowClass;

struct _SpiceWindow {
    GObject          object;
    spice_connection *conn;
    gint             id;
    gint             monitor_id;
    SpiceDisplay      *spice;
};

struct _SpiceWindowClass
{
  GObjectClass parent_class;
};

#define CHANNELID_MAX 4
#define MONITORID_MAX 4

// FIXME: turn this into an object, get rid of fixed wins array, use
// signals to replace the various callback that iterate over wins array
struct spice_connection {
    struct spice_context *ctx;
    SpiceSession     *session;
    SpiceMainChannel *main;
    SpiceWindow     *wins[CHANNELID_MAX * MONITORID_MAX];
    SpiceAudio       *audio;
    int              channels;
    int              disconnecting;
};

spice_connection *connection_new(struct spice_context *ctx);
void connection_connect(spice_connection *conn);
void connection_disconnect(spice_connection *conn);

/* ------------------------------------------------------------------ */

#endif /* _ANDROID_SPICY_H */
