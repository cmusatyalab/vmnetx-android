package org.olivearchive.vmnetx.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.view.KeyEvent;

import org.olivearchive.vmnetx.android.protocol.ProtocolException;
import org.olivearchive.vmnetx.android.protocol.ViewerConnectionProcessor;
import com.gstreamer.*;

public class SpiceCommunicator {
    private final static String TAG = "SpiceCommunicator";

    private native long SpiceClientNewContext ();
    private native void SpiceClientFreeContext (long context);
    private native int  SpiceClientConnect (long context, String password);
    private native void SpiceClientDisconnect (long context);
    private native void SpiceButtonEvent (long context, int x, int y, int pointerMask);
    private native void SpiceKeyEvent (long context, boolean keyDown, int virtualKeyCode);
    private native void SpiceUpdateBitmap (long context, Bitmap bitmap, int x, int y, int w, int h);
    private native void SpiceRequestResolution (long context, int x, int y);
    private native void SpiceSetFd (long cookie, int fd);
    
    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("spice");
    }
    
    private int modifiers = 0;
    
    private RemoteCanvas canvas;
    private Handler handler;
    private ConnectionBean connection;

    private int width = 0;
    private int height = 0;
    
    private boolean wantAbsoluteMouse = false;

    private boolean isInNormalProtocol = false;
    
    private SpiceThread spicethread = null;
    private long context;

    public SpiceCommunicator (Context context, RemoteCanvas canvas, Handler handler, ConnectionBean connection) {
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
        spicethread = new SpiceThread();
        spicethread.start();
    }
    
    public void disconnect() {
        SpiceClientDisconnect(context);
        try {spicethread.join(3000);} catch (InterruptedException e) {}
    }

    protected void finalize() {
        SpiceClientFreeContext(context);
    }

    private class SpiceThread extends Thread {
        public void run() {
            SpiceClientConnect(context, connection.getToken());
            android.util.Log.d(TAG, "SpiceClientConnect returned.");

            // If we've exited SpiceClientConnect, the connection was
            // interrupted or was never established.
            handler.sendEmptyMessage(Constants.SPICE_CONNECT_FAILURE);
        }
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

    private void sendMouseEvent (int x, int y, int pointerMask) {
        SpiceButtonEvent(context, x, y, pointerMask);
    }

    private void sendKeyEvent (boolean keyDown, int virtualKeyCode) {
        SpiceKeyEvent(context, keyDown, virtualKeyCode);
    }
    
    public void updateBitmap (Bitmap bitmap, int x, int y, int w, int h) {
        SpiceUpdateBitmap(context, bitmap, x, y, w, h);
    }
    
    /* Callbacks from jni */
    private void OnGetFd(long cookie) {
        new ConnectThread(cookie).start();
    }

    private void OnSettingsChanged(int inst, int width, int height, int bpp) {
        this.width = width;
        this.height = height;
        canvas.OnSettingsChanged(width, height, bpp);
        isInNormalProtocol = true;
    }

    private void OnGraphicsUpdate(int inst, int x, int y, int width, int height) {
        canvas.OnGraphicsUpdate(x, y, width, height);
    }

    private void OnCursorConfig(boolean absoluteMouse) {
        synchronized (this) {
            wantAbsoluteMouse = absoluteMouse;
        }
        canvas.OnCursorConfig();
    }

    public int framebufferWidth() {
        return width;
    }

    public int framebufferHeight() {
        return height;
    }

    public boolean isInNormalProtocol() {
        return isInNormalProtocol;
    }

    public synchronized boolean getAbsoluteMouse() {
        return wantAbsoluteMouse;
    }

    public void writePointerEvent(int x, int y, int pointerMask) {
        sendMouseEvent(x, y, pointerMask);
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
