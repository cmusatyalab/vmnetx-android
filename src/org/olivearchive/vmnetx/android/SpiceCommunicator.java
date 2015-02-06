/**
 * Copyright (C) 2014-2015 Carnegie Mellon University
 * Copyright (C) 2013-2014 Iordan Iordanov
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

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import org.freedesktop.gstreamer.GStreamer;

import org.olivearchive.vmnetx.android.protocol.ProtocolException;
import org.olivearchive.vmnetx.android.protocol.ViewerConnectionProcessor;

public class SpiceCommunicator {
    private final static String TAG = "SpiceCommunicator";

    private native long SpiceClientNewContext ();
    private native void SpiceClientFreeContext (long context);
    private native void SpiceClientConnect (long context, String password);
    private native void SpiceClientDisconnect (long context);
    private native void SpicePointerEvent (long context, boolean absolute, int x, int y);
    private native void SpiceButtonEvent (long context, boolean buttonDown, int button);
    private native void SpiceScrollEvent (long context, int button, int count);
    private native void SpiceKeyEvent (long context, boolean keyDown, int virtualKeyCode);
    private native void SpiceUpdateBitmap (long context, Bitmap bitmap, int x, int y, int w, int h);
    private native void SpiceForceRedraw (long context);
    private native void SpiceRequestResolution (long context, int x, int y);
    private native void SpiceSetFd (long cookie, int fd);
    
    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("spice");
    }
    
    private int modifiers = 0;
    private int buttons = 0;
    
    private final RemoteCanvas canvas;
    private final Handler handler;
    private final ConnectionInfo connection;
    private final long context;

    private boolean isInNormalProtocol = false;

    public SpiceCommunicator (Context context, RemoteCanvas canvas, Handler handler, ConnectionInfo connection) {
        this.canvas = canvas;
        this.handler = handler;
        this.connection = connection;
        this.context = SpiceClientNewContext();
        try {
            GStreamer.init(context);
        } catch (Exception e) {
            e.printStackTrace();
            canvas.displayShortToastMessage(e.getMessage());
        }
    }

    public void connect() {
        SpiceClientConnect(context, connection.getToken());
    }
    
    public void disconnect() {
        SpiceClientDisconnect(context);
    }

    protected void finalize() {
        SpiceClientFreeContext(context);
    }

    private class ConnectThread extends Thread {
        private long cookie;

        public ConnectThread(long cookie) {
            this.cookie = cookie;
        }

        public void run() {
            try {
                int fd = new ViewerConnectionProcessor(connection.getAddress(),
                                                       Integer.toString(connection.getPort()),
                                                       connection.getToken()).connect();
                SpiceSetFd(cookie, fd);
            } catch (ProtocolException e) {
                android.util.Log.e(TAG, "Get FD failed", e);
                disconnect();
            }
        }
    }

    private void sendPointerEvent (boolean absolute, int x, int y) {
        SpicePointerEvent(context, absolute, x, y);
    }

    private void sendButtonEvent (boolean buttonDown, int button) {
        SpiceButtonEvent(context, buttonDown, button);
    }

    private void sendScrollEvent(int button, int count) {
        SpiceScrollEvent(context, button, count);
    }

    private void sendKeyEvent (boolean keyDown, int virtualKeyCode) {
        SpiceKeyEvent(context, keyDown, virtualKeyCode);
    }
    
    public void updateBitmap (Bitmap bitmap, int x, int y, int w, int h) {
        SpiceUpdateBitmap(context, bitmap, x, y, w, h);
    }
    
    public void redraw() {
        SpiceForceRedraw(context);
    }

    /* Callbacks from jni */
    private void OnGetFd(long cookie) {
        new ConnectThread(cookie).start();
    }

    private void OnSettingsChanged(int width, int height) {
        canvas.OnSettingsChanged(width, height);
        isInNormalProtocol = true;
    }

    private void OnGraphicsUpdate(int x, int y, int width, int height) {
        canvas.getViewport().OnGraphicsUpdate(x, y, width, height);
    }

    private void OnMouseMode(boolean absoluteMouse) {
        canvas.OnMouseMode(absoluteMouse);
    }

    private void OnCursorConfig(boolean shown, int[] bitmap, int w, int h,
            int hotX, int hotY) {
        canvas.getViewport().OnCursorConfig(shown, bitmap, w, h, hotX, hotY);
    }

    private void OnDisconnect() {
        handler.sendEmptyMessage(Constants.SPICE_CONNECT_FAILURE);
    }

    public boolean isInNormalProtocol() {
        return isInNormalProtocol;
    }

    public void writePointerEvent(int x, int y) {
        sendPointerEvent(true, x, y);
    }

    public void writeMotionEvent(int dx, int dy) {
        sendPointerEvent(false, dx, dy);
    }

    public void updateButtons(int buttons) {
        int changes = this.buttons ^ buttons;
        int pressed = buttons & changes;
        int released = this.buttons & changes;
        this.buttons = buttons;

        updateButton(pressed, released, MotionEvent.BUTTON_PRIMARY);
        updateButton(pressed, released, MotionEvent.BUTTON_SECONDARY);
        updateButton(pressed, released, MotionEvent.BUTTON_TERTIARY);
    }

    private void updateButton(int pressed, int released, int button) {
        boolean buttonDown;
        if ((pressed & button) != 0)
            buttonDown = true;
        else if ((released & button) != 0)
            buttonDown = false;
        else
            return;
        //android.util.Log.d(TAG, (buttonDown ? "Pressing" : "Releasing") + " button: " + button);
        sendButtonEvent(buttonDown, button);
    }

    public void writeScrollEvent(int button, int count) {
        sendScrollEvent(button, count);
    }

    public void updateModifierKeys(int modifiers) {
        int changes = this.modifiers ^ modifiers;
        int pressed = modifiers & changes;
        int released = this.modifiers & changes;
        this.modifiers = modifiers;

        updateModifierKey(pressed, released,
                KeyEvent.META_CTRL_LEFT_ON, KeyEvent.KEYCODE_CTRL_LEFT);
        updateModifierKey(pressed, released,
                KeyEvent.META_CTRL_RIGHT_ON, KeyEvent.KEYCODE_CTRL_RIGHT);
        updateModifierKey(pressed, released,
                KeyEvent.META_ALT_LEFT_ON, KeyEvent.KEYCODE_ALT_LEFT);
        updateModifierKey(pressed, released,
                KeyEvent.META_ALT_RIGHT_ON, KeyEvent.KEYCODE_ALT_RIGHT);
        updateModifierKey(pressed, released,
                KeyEvent.META_META_LEFT_ON, KeyEvent.KEYCODE_META_LEFT);
        updateModifierKey(pressed, released,
                KeyEvent.META_META_RIGHT_ON, KeyEvent.KEYCODE_META_RIGHT);
        updateModifierKey(pressed, released,
                KeyEvent.META_SHIFT_LEFT_ON, KeyEvent.KEYCODE_SHIFT_LEFT);
        updateModifierKey(pressed, released,
                KeyEvent.META_SHIFT_RIGHT_ON, KeyEvent.KEYCODE_SHIFT_RIGHT);
    }

    private void updateModifierKey(int pressed, int released, int modifier,
            int keyCode) {
        boolean keyDown;
        if ((pressed & modifier) != 0)
            keyDown = true;
        else if ((released & modifier) != 0)
            keyDown = false;
        else
            return;
        //android.util.Log.d(TAG, (keyDown ? "Pressing" : "Releasing") + " modifier: " + keyCode);
        sendKeyEvent(keyDown, keyCode);
    }

    public void pressAndReleaseKey(int keyCode) {
        //android.util.Log.d(TAG, "Press+release key: " + keyCode);
        sendKeyEvent(true, keyCode);
        sendKeyEvent(false, keyCode);
    }

    public void requestResolution(int x, int y) {
        SpiceRequestResolution(context, x, y);
    }
}
