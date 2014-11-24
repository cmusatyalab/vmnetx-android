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

//
// RemoteCanvasActivity is the Activity for showing SPICE Desktop.
//
package org.olivearchive.vmnetx.android;

import org.olivearchive.vmnetx.android.input.AbsoluteMouseHandler;
import org.olivearchive.vmnetx.android.input.GestureHandler;
import org.olivearchive.vmnetx.android.input.RelativeMouseHandler;
import org.olivearchive.vmnetx.android.input.RemoteKeyboard;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.View.OnKeyListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;


public class RemoteCanvasActivity extends Activity implements OnKeyListener {
    //private final static String TAG = "RemoteCanvasActivity";
    private final static String CONNECTION_KEY = "RemoteCanvasActivity.connection";
    
    private GestureHandler gestureHandler;

    private RemoteCanvas canvas;

    private ConnectionBean connection;

    private Handler handler;

    private MenuItem keyboardMenuItem;

    private RelativeLayout layoutKeys;
    private ImageButton keyCtrl;
    private boolean keyCtrlLocked;
    private ImageButton keySuper;
    private boolean keySuperLocked;
    private ImageButton keyAlt;
    private boolean keyAltLocked;
    private ImageButton keyTab;
    private ImageButton keyEsc;
    private ImageButton keyShift;
    private boolean keyShiftLocked;
    private ImageButton keyUp;
    private ImageButton keyDown;
    private ImageButton keyLeft;
    private ImageButton keyRight;
    private int prevBottomOffset = 0;
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (icicle != null)
            connection = (ConnectionBean) icicle.getSerializable(CONNECTION_KEY);

        initialize();
        continueConnecting();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
        icicle.putSerializable(CONNECTION_KEY, connection);
    }

    void initialize () {
        android.os.StrictMode.ThreadPolicy policy = new android.os.StrictMode.ThreadPolicy.Builder().permitAll().build();
        android.os.StrictMode.setThreadPolicy(policy);
        
        handler = new Handler ();
        
        if (connection == null) {
            Intent i = getIntent();
            connection = new ConnectionBean();

            Uri data = i.getData();
            if (data == null || !data.getScheme().equals("vmnetx")) {
                Utils.showFatalErrorMessage(this, getString(R.string.error_connection_type_not_supported));
            }

            String host = data.getHost();
            // This should not happen according to Uri contract, but bug introduced in Froyo (2.2)
            // has made this parsing of host necessary
            int index = host.indexOf(':');
            int port;
            if (index != -1)
            {
                try
                {
                    port = Integer.parseInt(host.substring(index + 1));
                }
                catch (NumberFormatException nfe)
                {
                    port = -1;
                }
                host = host.substring(0,index);
            }
            else
            {
                port = data.getPort();
            }
            connection.setAddress(host);
            if (port != -1)
                connection.setPort(port);
            String path = data.getPath();
            if (path != null) {
                // drop leading '/'
                connection.setToken(path.substring(1));
            }
        }
    }

    void continueConnecting () {
        setContentView(R.layout.canvas);
        canvas = (RemoteCanvas) findViewById(R.id.remoteCanvas);

        // Initialize and define actions for on-screen keys.
        initializeOnScreenKeys ();
    
        canvas.initializeCanvas(connection, new Runnable() {
            public void run() {
                try { setModes(); } catch (NullPointerException e) { }
            }
        });
        
        canvas.setOnKeyListener(this);
        canvas.setFocusableInTouchMode(true);
        
        // This code detects when the soft keyboard is up and sets an appropriate visibleHeight in canvas.
        // When the keyboard is gone, it resets visibleHeight and pans zero distance to prevent us from being
        // below the desktop image (if we scrolled all the way down when the keyboard was up).
        // TODO: Move this into a separate thread, and post the visibility changes to the handler.
        //       to avoid occupying the UI thread with this.
        final View rootView = ((ViewGroup)findViewById(android.R.id.content)).getChildAt(0);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                    Rect r = new Rect();

                    rootView.getWindowVisibleDisplayFrame(r);

                    // To avoid setting the visible height to a wrong value after an screen unlock event
                    // (when r.bottom holds the width of the screen rather than the height due to a rotation)
                    // we make sure r.top is zero (i.e. there is no notification bar and we are in full-screen mode)
                    // It's a bit of a hack.
                    if (r.top == 0) {
                        if (canvas.bitmapData != null) {
                            canvas.setVisibleHeight(r.bottom);
                            canvas.pan(0,0);
                        }
                    }
                    
                    // Enable/show the keyboard controls if the keyboard is gone, and disable/hide otherwise.
                    // We detect the keyboard if more than 19% of the screen is covered.
                    int offset = 0;
                    int rootViewHeight = rootView.getHeight();
                    if (r.bottom > rootViewHeight*0.81) {
                        offset = rootViewHeight - r.bottom;
                        // Soft Kbd gone, shift the meta keys and arrows down.
                        if (layoutKeys != null) {
                            layoutKeys.offsetTopAndBottom(offset);
                            if (prevBottomOffset != offset) { 
                                canvas.invalidate();
                            }
                        }
                    } else {
                        offset = r.bottom - rootViewHeight;
                        //  Soft Kbd up, shift the meta keys and arrows up.
                        if (layoutKeys != null) {
                            layoutKeys.offsetTopAndBottom(offset);
                            if (prevBottomOffset != offset) { 
                                canvas.invalidate();
                            }
                        }
                    }
                    prevBottomOffset = offset;
             }
        });

        gestureHandler = new AbsoluteMouseHandler(this, canvas);
    }

    /**
     * Initializes the on-screen keys for meta keys and arrow keys.
     */
    private void initializeOnScreenKeys () {
        
        layoutKeys = (RelativeLayout) findViewById(R.id.layoutKeys);

        // Define action of tab key and meta keys.
        keyTab = (ImageButton) findViewById(R.id.keyTab);
        keyTab.setOnTouchListener(new OnTouchListener () {
            @Override
            public boolean onTouch(View arg0, MotionEvent e) {
                RemoteKeyboard k = canvas.getKeyboard();
                int key = KeyEvent.KEYCODE_TAB;
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    Utils.performLongPressHaptic(canvas);
                    keyTab.setImageResource(R.drawable.tabon);
                    k.repeatKeyEvent(key, new KeyEvent(e.getAction(), key));
                    return true;
                } else if (e.getAction() == MotionEvent.ACTION_UP) {
                    keyTab.setImageResource(R.drawable.taboff);
                    resetOnScreenKeys (0);
                    k.stopRepeatingKeyEvent();
                    return true;
                }
                return false;
            }
        });

        keyEsc = (ImageButton) findViewById(R.id.keyEsc);
        keyEsc.setOnTouchListener(new OnTouchListener () {
            @Override
            public boolean onTouch(View arg0, MotionEvent e) {
                RemoteKeyboard k = canvas.getKeyboard();
                int key = KeyEvent.KEYCODE_ESCAPE;
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    Utils.performLongPressHaptic(canvas);
                    keyEsc.setImageResource(R.drawable.escon);
                    k.repeatKeyEvent(key, new KeyEvent(e.getAction(), key));
                    return true;
                } else if (e.getAction() == MotionEvent.ACTION_UP) {
                    keyEsc.setImageResource(R.drawable.escoff);
                    resetOnScreenKeys (0);
                    k.stopRepeatingKeyEvent();
                    return true;
                }
                return false;
            }
        });

        keyCtrl = (ImageButton) findViewById(R.id.keyCtrl);
        keyCtrl.setOnClickListener(new OnClickListener () {
            @Override
            public void onClick(View arg0) {
                boolean on = canvas.getKeyboard().onScreenCtrlToggle();
                keyCtrlLocked = false;
                if (on)
                    keyCtrl.setImageResource(R.drawable.ctrlon);
                else
                    keyCtrl.setImageResource(R.drawable.ctrloff);
            }
        });
        
        keyCtrl.setOnLongClickListener(new OnLongClickListener () {
            @Override
            public boolean onLongClick(View arg0) {
                Utils.performLongPressHaptic(canvas);
                boolean on = canvas.getKeyboard().onScreenCtrlToggle();
                keyCtrlLocked = true;
                if (on)
                    keyCtrl.setImageResource(R.drawable.ctrlon);
                else
                    keyCtrl.setImageResource(R.drawable.ctrloff);
                return true;
            }
        });

        keySuper = (ImageButton) findViewById(R.id.keySuper);
        keySuper.setOnClickListener(new OnClickListener () {
            @Override
            public void onClick(View arg0) {
                boolean on = canvas.getKeyboard().onScreenSuperToggle();
                keySuperLocked = false;
                if (on)
                    keySuper.setImageResource(R.drawable.superon);
                else
                    keySuper.setImageResource(R.drawable.superoff);
            }
        });

        keySuper.setOnLongClickListener(new OnLongClickListener () {
            @Override
            public boolean onLongClick(View arg0) {
                Utils.performLongPressHaptic(canvas);
                boolean on = canvas.getKeyboard().onScreenSuperToggle();
                keySuperLocked = true;
                if (on)
                    keySuper.setImageResource(R.drawable.superon);
                else
                    keySuper.setImageResource(R.drawable.superoff);
                return true;
            }
        });

        keyAlt = (ImageButton) findViewById(R.id.keyAlt);
        keyAlt.setOnClickListener(new OnClickListener () {
            @Override
            public void onClick(View arg0) {
                boolean on = canvas.getKeyboard().onScreenAltToggle();
                keyAltLocked = false;
                if (on)
                    keyAlt.setImageResource(R.drawable.alton);
                else
                    keyAlt.setImageResource(R.drawable.altoff);
            }
        });
        
        keyAlt.setOnLongClickListener(new OnLongClickListener () {
            @Override
            public boolean onLongClick(View arg0) {
                Utils.performLongPressHaptic(canvas);
                boolean on = canvas.getKeyboard().onScreenAltToggle();
                keyAltLocked = true;
                if (on)
                    keyAlt.setImageResource(R.drawable.alton);
                else
                    keyAlt.setImageResource(R.drawable.altoff);
                return true;
            }
        });
        
        keyShift = (ImageButton) findViewById(R.id.keyShift);
        keyShift.setOnClickListener(new OnClickListener () {
            @Override
            public void onClick(View arg0) {
                boolean on = canvas.getKeyboard().onScreenShiftToggle();
                keyShiftLocked = false;
                if (on)
                    keyShift.setImageResource(R.drawable.shifton);
                else
                    keyShift.setImageResource(R.drawable.shiftoff);
            }
        });
        
        keyShift.setOnLongClickListener(new OnLongClickListener () {
            @Override
            public boolean onLongClick(View arg0) {
                Utils.performLongPressHaptic(canvas);
                boolean on = canvas.getKeyboard().onScreenShiftToggle();
                keyShiftLocked = true;
                if (on)
                    keyShift.setImageResource(R.drawable.shifton);
                else
                    keyShift.setImageResource(R.drawable.shiftoff);
                return true;
            }
        });
        
        // Define action of arrow keys.
        keyUp = (ImageButton) findViewById(R.id.keyUpArrow);
        keyUp.setOnTouchListener(new OnTouchListener () {
            @Override
            public boolean onTouch(View arg0, MotionEvent e) {
                RemoteKeyboard k = canvas.getKeyboard();
                int key = KeyEvent.KEYCODE_DPAD_UP;
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    Utils.performLongPressHaptic(canvas);
                    keyUp.setImageResource(R.drawable.upon);
                    k.repeatKeyEvent(key, new KeyEvent(e.getAction(), key));
                    return true;
                } else if (e.getAction() == MotionEvent.ACTION_UP) {
                    keyUp.setImageResource(R.drawable.upoff);
                    resetOnScreenKeys (0);
                    k.stopRepeatingKeyEvent();
                    return true;
                }
                return false;
            }
        });

        keyDown = (ImageButton) findViewById(R.id.keyDownArrow);
        keyDown.setOnTouchListener(new OnTouchListener () {
            @Override
            public boolean onTouch(View arg0, MotionEvent e) {
                RemoteKeyboard k = canvas.getKeyboard();
                int key = KeyEvent.KEYCODE_DPAD_DOWN;
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    Utils.performLongPressHaptic(canvas);
                    keyDown.setImageResource(R.drawable.downon);
                    k.repeatKeyEvent(key, new KeyEvent(e.getAction(), key));
                    return true;
                } else if (e.getAction() == MotionEvent.ACTION_UP) {
                    keyDown.setImageResource(R.drawable.downoff);
                    resetOnScreenKeys (0);
                    k.stopRepeatingKeyEvent();
                    return true;
                }
                return false;
            }
        });

        keyLeft = (ImageButton) findViewById(R.id.keyLeftArrow);
        keyLeft.setOnTouchListener(new OnTouchListener () {
            @Override
            public boolean onTouch(View arg0, MotionEvent e) {
                RemoteKeyboard k = canvas.getKeyboard();
                int key = KeyEvent.KEYCODE_DPAD_LEFT;
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    Utils.performLongPressHaptic(canvas);
                    keyLeft.setImageResource(R.drawable.lefton);
                    k.repeatKeyEvent(key, new KeyEvent(e.getAction(), key));
                    return true;
                } else if (e.getAction() == MotionEvent.ACTION_UP) {
                    keyLeft.setImageResource(R.drawable.leftoff);
                    resetOnScreenKeys (0);
                    k.stopRepeatingKeyEvent();
                    return true;
                }
                return false;
            }
        });

        keyRight = (ImageButton) findViewById(R.id.keyRightArrow);
        keyRight.setOnTouchListener(new OnTouchListener () {
            @Override
            public boolean onTouch(View arg0, MotionEvent e) {
                RemoteKeyboard k = canvas.getKeyboard();
                int key = KeyEvent.KEYCODE_DPAD_RIGHT;
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    Utils.performLongPressHaptic(canvas);
                    keyRight.setImageResource(R.drawable.righton);
                    k.repeatKeyEvent(key, new KeyEvent(e.getAction(), key));
                    return true;    
                } else if (e.getAction() == MotionEvent.ACTION_UP) {
                    keyRight.setImageResource(R.drawable.rightoff);
                    resetOnScreenKeys (0);
                    k.stopRepeatingKeyEvent();
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * Resets the state and image of the on-screen keys.
     */
    private void resetOnScreenKeys (int keyCode) {
        // Do not reset on-screen keys if keycode is SHIFT.
        switch (keyCode) {
        case KeyEvent.KEYCODE_SHIFT_LEFT:
        case KeyEvent.KEYCODE_SHIFT_RIGHT: return;
        }
        if (!keyCtrlLocked) {
            keyCtrl.setImageResource(R.drawable.ctrloff);
            canvas.getKeyboard().onScreenCtrlOff();
        }
        if (!keyAltLocked) {
            keyAlt.setImageResource(R.drawable.altoff);
            canvas.getKeyboard().onScreenAltOff();
        }
        if (!keySuperLocked) {
            keySuper.setImageResource(R.drawable.superoff);
            canvas.getKeyboard().onScreenSuperOff();
        }
        if (!keyShiftLocked) {
            keyShift.setImageResource(R.drawable.shiftoff);
            canvas.getKeyboard().onScreenShiftOff();
        }
    }

    /**
     * Set up scaling and input modes
     */
    void setModes() {
        // Update VM name first, since the later methods may throw
        // NullPointerException which the caller will swallow
        String vmName = canvas.getVMName();
        if (vmName != null) {
            setTitle(vmName);
        }

        canvas.scaling.updateForCanvas(canvas);

        boolean absoluteMouse = canvas.getAbsoluteMouse();
        if (absoluteMouse &&
                !(gestureHandler instanceof AbsoluteMouseHandler)) {
            gestureHandler = new AbsoluteMouseHandler(this, canvas);
        } else if (!absoluteMouse &&
                !(gestureHandler instanceof RelativeMouseHandler)) {
            gestureHandler = new RelativeMouseHandler(this, canvas);
        }
    }

    private void updateKeyboardMenuItem(Configuration config) {
        boolean softKeyboardEnabled = (config.hardKeyboardHidden !=
                Configuration.HARDKEYBOARDHIDDEN_NO);
        keyboardMenuItem.setEnabled(softKeyboardEnabled);
        keyboardMenuItem.setVisible(softKeyboardEnabled);
    }

    /**
     * This runnable fixes things up after a rotation.
     */
    private Runnable rotationCorrector = new Runnable() {
        public void run() {
            try { correctAfterRotation (); } catch (NullPointerException e) { }
        }
    };

    /**
     * This function is called by the rotationCorrector runnable
     * to fix things up after a rotation.
     */
    private void correctAfterRotation () {
        // Its quite common to see NullPointerExceptions here when this function is called
        // at the point of disconnection. Hence, we catch and ignore the error.
        float oldScale = canvas.scaling.getScale();
        int x = canvas.absoluteXPosition;
        int y = canvas.absoluteYPosition;
        canvas.scaling.updateForCanvas(canvas);
        float newScale = canvas.scaling.getScale();
        canvas.scaling.adjust(this, oldScale/newScale, 0, 0);
        newScale = canvas.scaling.getScale();
        if (newScale <= oldScale) {
            canvas.absoluteXPosition = x;
            canvas.absoluteYPosition = y;
            canvas.scrollToAbsolute();
        }
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        updateKeyboardMenuItem(newConfig);

        try {
            // Correct a few times just in case. There is no visual effect.
            handler.postDelayed(rotationCorrector, 300);
            handler.postDelayed(rotationCorrector, 600);
            handler.postDelayed(rotationCorrector, 1200);
        } catch (NullPointerException e) { }
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            canvas.postInvalidateDelayed(800);
        } catch (NullPointerException e) { }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        try {
            canvas.postInvalidateDelayed(1000);
        } catch (NullPointerException e) { }
    }

    /** {@inheritDoc} */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        try {
            getMenuInflater().inflate(R.menu.canvas, menu);
            keyboardMenuItem = menu.findItem(R.id.itemKeyboard);
            updateKeyboardMenuItem(getResources().getConfiguration());
        } catch (NullPointerException e) { }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.itemKeyboard:
            InputMethodManager inputMgr = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMgr.toggleSoftInput(0, 0);
            break;
        case R.id.itemExtraKeys:
            if (layoutKeys.getVisibility() == View.VISIBLE) {
                layoutKeys.setVisibility(View.GONE);
            } else {
                layoutKeys.setVisibility(View.VISIBLE);
            }
            layoutKeys.invalidate();
            layoutKeys.offsetTopAndBottom(prevBottomOffset);
            break;
        case R.id.itemDisconnect:
            Utils.showYesNoPrompt(this, getString(R.string.disconnect_prompt_title), getString(R.string.disconnect_prompt), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    canvas.closeConnection();
                    finish();
                }
            }, null, null);
            return true;
        case R.id.itemCtrlAltDel:
            canvas.getKeyboard().sendCtrlAltDel();
            return true;
        case R.id.itemRestart:
            Utils.showYesNoPrompt(this, getString(R.string.restart_prompt_title), getString(R.string.restart_prompt), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    canvas.restartVM();
                }
            }, null, null);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (canvas != null)
            canvas.closeConnection();
        canvas = null;
        connection = null;
        gestureHandler = null;
        System.gc();
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent evt) {

        boolean consumed = false;

        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_BACK)
            return false;

        try {
            switch (evt.getAction()) {
            case KeyEvent.ACTION_DOWN:
            case KeyEvent.ACTION_MULTIPLE:
            case KeyEvent.ACTION_UP:
                RemoteKeyboard keyboard = canvas.getKeyboard();
                consumed = keyboard.processLocalKeyEvent(keyCode, evt);
                break;
            }
            resetOnScreenKeys (keyCode);
        } catch (NullPointerException e) { }

        return consumed;
    }

    // Send touch events or mouse events like button clicks to be handled.
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            return gestureHandler.onTouchEvent(event);
        } catch (NullPointerException e) { }
        return super.onTouchEvent(event);
    }

    // Send e.g. mouse events like hover and scroll to be handled.
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        // Ignore TOOL_TYPE_FINGER events that come from the touchscreen with y == 0.0
        // which cause pointer jumping trouble for some users.
        if (! (event.getY() == 0.0f &&
               event.getSource() == InputDevice.SOURCE_TOUCHSCREEN &&
               event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) ) {
            try {
                return gestureHandler.onTouchEvent(event);
            } catch (NullPointerException e) { }
        }
        return super.onGenericMotionEvent(event);
    }

    public float getSensitivity() {
        // TODO: Make this a slider config option.
        return 2.0f;
    }
    
    public boolean getAccelerationEnabled() {
        // TODO: Make this a config option.
        return true;
    }

    public RemoteCanvas getCanvas() {
        return canvas;
    }

    public void setCanvas(RemoteCanvas canvas) {
        this.canvas = canvas;
    }
}
