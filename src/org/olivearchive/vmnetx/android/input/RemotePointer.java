/**
 * Copyright (C) 2014-2015 Carnegie Mellon University
 * Copyright (C) 2012-2014 Iordan Iordanov
 * Copyright (C) 2010 Michael A. MacDonald
 * Copyright (C) 2004 Horizon Wimba.  All Rights Reserved.
 * Copyright (C) 2001-2003 HorizonLive.com, Inc.  All Rights Reserved.
 * Copyright (C) 2001,2002 Constantin Kaplinsky.  All Rights Reserved.
 * Copyright (C) 2000 Tridia Corporation.  All Rights Reserved.
 * Copyright (C) 1999 AT&T Laboratories Cambridge.  All Rights Reserved.
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

package org.olivearchive.vmnetx.android.input;

import org.olivearchive.vmnetx.android.RemoteCanvas;
import org.olivearchive.vmnetx.android.SpiceCommunicator;
import org.olivearchive.vmnetx.android.Viewport;

public class RemotePointer {
    @SuppressWarnings("unused")
    private static final String TAG = "RemotePointer";

    public static final int BUTTON_SCROLL_UP	= 4;
    public static final int BUTTON_SCROLL_DOWN	= 5;

    private final RemoteCanvas canvas;
    private final SpiceCommunicator spice;
    private final ModifierState buttons;

    /**
     * Indicates where the mouse pointer is located.
     */
    private int mouseX, mouseY;

    public RemotePointer (SpiceCommunicator s, RemoteCanvas c) {
        spice = s;
        canvas = c;
        buttons = new ModifierState();
    }
    
    public int getX() {
        return mouseX;
    }

    public int getY() {
        return mouseY;
    }

    public boolean processPointerEvent(int x, int y) {
        if (spice.isInNormalProtocol()) {
            Viewport viewport = canvas.getViewport();
            viewport.invalidateMousePosition();
            mouseX = x;
            mouseY = y;
            if (mouseX < 0)
                mouseX = 0;
            else if (mouseX >= viewport.getImageWidth())
                mouseX = viewport.getImageWidth()  - 1;
            if (mouseY < 0)
                mouseY = 0;
            else if (mouseY >= viewport.getImageHeight())
                mouseY = viewport.getImageHeight() - 1;
            viewport.invalidateMousePosition();

            spice.writePointerEvent(mouseX, mouseY);
            return true;
        }
        return false;
    }

    public boolean processMotionEvent(int dx, int dy) {
        if (spice.isInNormalProtocol()) {
            spice.writeMotionEvent(dx, dy);
            return true;
        }
        return false;
    }

    public boolean processButtonEvent(int deviceID, int buttonState) {
        buttons.getDeviceState(deviceID).set(buttonState);
        if (spice.isInNormalProtocol()) {
            spice.updateButtons(buttons.getModifiers());
            return true;
        }
        return false;
    }

    public boolean processScrollEvent(int button, int count) {
        if (spice.isInNormalProtocol()) {
            spice.writeScrollEvent(button, count);
            return true;
        }
        return false;
    }
}
