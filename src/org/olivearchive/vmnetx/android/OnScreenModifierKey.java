/**
 * Copyright (C) 2014 Carnegie Mellon University
 * Copyright (C) 2012 Iordan Iordanov
 * Copyright (C) 2010 Michael A. MacDonald
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

package org.olivearchive.vmnetx.android;

import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ImageButton;

class OnScreenModifierKey {
    private final RemoteCanvas canvas;
    private final ImageButton button;
    private final int onImage;
    private final int offImage;
    private final int modifier;

    private boolean locked;

    OnScreenModifierKey(final RemoteCanvas canvas, ImageButton button,
            int onImage, int offImage, final int modifier) {
        this.canvas = canvas;
        this.button = button;
        this.onImage = onImage;
        this.offImage = offImage;
        this.modifier = modifier;

        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                boolean on = canvas.getKeyboard().onScreenModifierToggle(modifier);
                locked = false;
                setImage(on);
            }
        });

        button.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View arg0) {
                Utils.performLongPressHaptic(canvas);
                boolean on = canvas.getKeyboard().onScreenModifierToggle(modifier);
                locked = true;
                setImage(on);
                return true;
            }
        });
    }

    void reset() {
        if (!locked) {
            setImage(false);
            canvas.getKeyboard().onScreenModifierOff(modifier);
        }
    }

    private void setImage(boolean on) {
        if (on)
            button.setImageResource(onImage);
        else
            button.setImageResource(offImage);
    }
}
