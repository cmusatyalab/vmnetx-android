package org.olivearchive.vmnetx.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.view.KeyEvent;

import org.olivearchive.vmnetx.android.input.KeyboardMapper;
import org.olivearchive.vmnetx.android.input.RemotePointer;
import org.olivearchive.vmnetx.android.protocol.ProtocolException;
import org.olivearchive.vmnetx.android.protocol.ViewerConnectionProcessor;
import com.gstreamer.*;

public class SpiceCommunicator implements KeyboardMapper.KeyProcessingListener {
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
    
    private int metaState = 0;
    
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
            android.util.Log.e(TAG, "SpiceClientConnect returned.");

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

    public void setIsInNormalProtocol(boolean state) {
        isInNormalProtocol = state;        
    }
    
    public boolean isInNormalProtocol() {
        return isInNormalProtocol;
    }

    public synchronized boolean getAbsoluteMouse() {
        return wantAbsoluteMouse;
    }

    public void writePointerEvent(int x, int y, int pointerMask) {
        if ((pointerMask & RemotePointer.PTRFLAGS_DOWN) != 0)
            sendModifierKeys(true);
        sendMouseEvent(x, y, pointerMask);
        if ((pointerMask & RemotePointer.PTRFLAGS_DOWN) == 0)
            sendModifierKeys(false);
    }

    private void sendModifierKeys (boolean keyDown) {        
        if ((metaState & KeyEvent.META_CTRL_LEFT_ON) != 0) {
            //android.util.Log.e("SpiceCommunicator", "Sending L-CTRL: " + KeyEvent.KEYCODE_CTRL_LEFT);
            sendKeyEvent(keyDown, KeyEvent.KEYCODE_CTRL_LEFT);
        }
        if ((metaState & KeyEvent.META_CTRL_RIGHT_ON) != 0) {
            //android.util.Log.e("SpiceCommunicator", "Sending R-CTRL: " + KeyEvent.KEYCODE_CTRL_RIGHT);
            sendKeyEvent(keyDown, KeyEvent.KEYCODE_CTRL_RIGHT);
        }
        if ((metaState & KeyEvent.META_ALT_LEFT_ON) != 0) {
            //android.util.Log.e("SpiceCommunicator", "Sending L-ALT: " + KeyEvent.KEYCODE_ALT_LEFT);
            sendKeyEvent(keyDown, KeyEvent.KEYCODE_ALT_LEFT);
        }
        if ((metaState & KeyEvent.META_ALT_RIGHT_ON) != 0) {
            //android.util.Log.e("SpiceCommunicator", "Sending R-ALT: " + KeyEvent.KEYCODE_ALT_RIGHT);
            sendKeyEvent(keyDown, KeyEvent.KEYCODE_ALT_RIGHT);
        }
        if ((metaState & KeyEvent.META_META_LEFT_ON) != 0) {
            //android.util.Log.e("SpiceCommunicator", "Sending L-META: " + KeyEvent.KEYCODE_META_LEFT);
            sendKeyEvent(keyDown, KeyEvent.KEYCODE_META_LEFT);
        }
        if ((metaState & KeyEvent.META_META_RIGHT_ON) != 0) {
            //android.util.Log.e("SpiceCommunicator", "Sending R-META: " + KeyEvent.KEYCODE_META_RIGHT);
            sendKeyEvent(keyDown, KeyEvent.KEYCODE_META_RIGHT);
        }
        if ((metaState & KeyEvent.META_SHIFT_LEFT_ON) != 0) {
            //android.util.Log.e("SpiceCommunicator", "Sending L-SHIFT: " + KeyEvent.KEYCODE_SHIFT_LEFT);
            sendKeyEvent(keyDown, KeyEvent.KEYCODE_SHIFT_LEFT);
        }
        if ((metaState & KeyEvent.META_SHIFT_RIGHT_ON) != 0) {
            //android.util.Log.e("SpiceCommunicator", "Sending R-SHIFT: " + KeyEvent.KEYCODE_SHIFT_RIGHT);
            sendKeyEvent(keyDown, KeyEvent.KEYCODE_SHIFT_RIGHT);
        }
    }
    
    public void writeKeyEvent(int key, int metaState, boolean down) {
        // Not used for actually sending keyboard events, but rather to record the current metastate.
        // The key event is sent to the KeyboardMapper from RemoteKeyboard, and
        // when processed through the keyboard mapper, it ends up in one of the KeyProcessingListener
        // methods defined here.
        this.metaState = metaState;
    }

    // ****************************************************************************
    // KeyboardMapper.KeyProcessingListener implementation
    @Override
    public void processVirtualKey(int virtualKeyCode, boolean keyDown) {

        if (keyDown)
            sendModifierKeys (true);
        
        //android.util.Log.e("SpiceCommunicator", "Sending VK key: " + virtualKeyCode + ". Is it down: " + down);
        sendKeyEvent(keyDown, virtualKeyCode);
        
        if (!keyDown)
            sendModifierKeys (false);
        
    }

    @Override
    public void processUnicodeKey(int unicodeKey) {
        boolean addShift = false;
        int keyToSend = -1;
        int tempMeta = 0;
        
        // Workarounds for some pesky keys.
        if (unicodeKey == 64) {
            addShift = true;
            keyToSend = 0x32;
        } else if (unicodeKey == 42) {
                addShift = true;
                keyToSend = 0x38;
        } else if (unicodeKey == 47) {
            keyToSend = 0xBF;
        } else if (unicodeKey == 63) {
            addShift = true;            
            keyToSend = 0xBF;
        }
        
        if (keyToSend != -1) {
            tempMeta = metaState;
            if (addShift) {
                metaState = metaState |  KeyEvent.META_SHIFT_LEFT_ON;
            }
            processVirtualKey(keyToSend, true);
            processVirtualKey(keyToSend, false);
            metaState = tempMeta;
        } else
            android.util.Log.e("SpiceCommunicator", "Unsupported unicode key that needs to be mapped: " + unicodeKey);
    }

    public void requestResolution(int x, int y) {
        SpiceRequestResolution(context, x, y);
    }
}
