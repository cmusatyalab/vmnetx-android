/**
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

package org.olivearchive.vmnetx.android.input;

import org.olivearchive.vmnetx.android.RemoteCanvas;
import org.olivearchive.vmnetx.android.RemoteCanvasActivity;

import android.graphics.PointF;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Input handlers delegate to this class to handle keystrokes; this detects keystrokes
 * from the DPad and uses them to perform mouse actions; other keystrokes are passed to
 * RemoteCanvasActivity.defaultKeyXXXHandler
 * 
 * @author Iordan Iordanov
 * @author Michael A. MacDonald
 *
 */
class DPadMouseKeyHandler {
    private RemoteCanvas canvas;
    private boolean rotateDpad      = false;
    private RemoteKeyboard keyboard;

    DPadMouseKeyHandler(RemoteCanvasActivity activity, Handler handler, boolean rotate)
    {
        canvas = activity.getCanvas();
        rotateDpad      = rotate;
    }

    public boolean onKeyDown(int keyCode, KeyEvent evt) {
        keyboard = canvas.getKeyboard();

        // If we are instructed to rotate the Dpad at 90 degrees, reassign KeyCodes.
        if (rotateDpad) {
            switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                keyCode = KeyEvent.KEYCODE_DPAD_UP;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
                break;
            }
        }

        // Pass the event to the default handler.
        return keyboard.processLocalKeyEvent(keyCode, evt);
    }

    public boolean onKeyUp(int keyCode, KeyEvent evt) {
        return keyboard.processLocalKeyEvent(keyCode, evt);
    }
}
