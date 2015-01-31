/* -*- Mode: C; c-basic-offset: 4; indent-tabs-mode: nil -*- */
/*
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
#ifndef __SPICE_WIDGET_PRIV_H__
#define __SPICE_WIDGET_PRIV_H__

G_BEGIN_DECLS

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "android-service.h"
#include "android-spice-widget.h"
#include "spice-common.h"
#include "spice-gtk-session.h"

#define SPICE_DISPLAY_GET_PRIVATE(obj)                                  \
    (G_TYPE_INSTANCE_GET_PRIVATE((obj), SPICE_TYPE_DISPLAY, SpiceDisplayPrivate))

struct _SpiceDisplayPrivate {
    gint                    channel_id;
    gint                    monitor_id;

    enum SpiceSurfaceFmt    format;
    gint                    width, height, stride;
    gpointer                data_origin; /* the original display image data */
    gpointer                data; /* converted if necessary to 32 bits */

    bool                    convert;

    struct spice_context    *ctx;
    SpiceSession            *session;
    SpiceMainChannel        *main;
    SpiceChannel            *display;
    SpiceCursorChannel      *cursor;
    SpiceInputsChannel      *inputs;

    int                     mouse_button_mask;

    uint32_t                key_state[512 / 32];
};

G_END_DECLS

#endif
