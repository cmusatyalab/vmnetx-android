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
// CanvasView is the Activity for showing VNC Desktop.
//
package org.olivearchive.vmnetx.android;

import java.util.List;

import org.olivearchive.vmnetx.android.input.AbstractInputHandler;
import org.olivearchive.vmnetx.android.input.Panner;
import org.olivearchive.vmnetx.android.input.RemoteKeyboard;
import org.olivearchive.vmnetx.android.input.SimulatedTouchpadInputHandler;
import org.olivearchive.vmnetx.android.input.TouchMouseDragPanInputHandler;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
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
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;


public class RemoteCanvasActivity extends Activity implements OnKeyListener {
    
    private final static String TAG = "VncCanvasActivity";
    private final static String CONNECTION_KEY = "RemoteCanvasActivity.connection";
    
    AbstractInputHandler inputHandler;

    private RemoteCanvas canvas;

    private MenuItem[] inputModeMenuItems;
    private MenuItem[] scalingModeMenuItems;
    private AbstractInputHandler inputModeHandlers[];
    private ConnectionBean connection;
    private static final int inputModeIds[] = { R.id.itemInputTouchpad,
                                                R.id.itemInputDragPanZoomMouse };
    private static final int scalingModeIds[] = { R.id.itemZoomable, R.id.itemFitToScreen,
                                                  R.id.itemOneToOne};

    KeyboardControls keyboardControls;
    Panner panner;
    Handler handler;

    RelativeLayout layoutKeys;
    ImageButton    keyStow;
    ImageButton    keyCtrl;
    boolean       keyCtrlToggled;
    ImageButton    keySuper;
    boolean       keySuperToggled;
    ImageButton    keyAlt;
    boolean       keyAltToggled;
    ImageButton    keyTab;
    ImageButton    keyEsc;
    ImageButton    keyShift;
    boolean       keyShiftToggled;
    ImageButton    keyUp;
    ImageButton    keyDown;
    ImageButton    keyLeft;
    ImageButton    keyRight;
    boolean       hardKeyboardExtended;
    boolean       extraKeysHidden = false;
    int            prevBottomOffset = 0;
    
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
        if (android.os.Build.VERSION.SDK_INT >= 9) {
            android.os.StrictMode.ThreadPolicy policy = new android.os.StrictMode.ThreadPolicy.Builder().permitAll().build();
            android.os.StrictMode.setThreadPolicy(policy);
        }
        
        handler = new Handler ();
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);

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
                    port = 0;
                }
                host = host.substring(0,index);
            }
            else
            {
                port = data.getPort();
            }
            connection.setAddress(host);
            connection.setPort(port);
            List<String> path = data.getPathSegments();
            if (path.size() >= 1) {
                connection.setPassword(path.get(0));
            }
        }
    }

    void continueConnecting () {
        // TODO: Implement left-icon
        //requestWindowFeature(Window.FEATURE_LEFT_ICON);
        //setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.icon); 

        setContentView(R.layout.canvas);
        canvas = (RemoteCanvas) findViewById(R.id.vnc_canvas);
        keyboardControls = (KeyboardControls) findViewById(R.id.keyboardControls);

        // Initialize and define actions for on-screen keys.
        initializeOnScreenKeys ();
    
        canvas.initializeCanvas(connection, new Runnable() {
            public void run() {
                try { setModes(); } catch (NullPointerException e) { }
            }
        });
        
        canvas.setOnKeyListener(this);
        canvas.setFocusableInTouchMode(true);
        canvas.setDrawingCacheEnabled(false);
        
        // This code detects when the soft keyboard is up and sets an appropriate visibleHeight in vncCanvas.
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
                            keyStow.offsetTopAndBottom(offset);
                            if (prevBottomOffset != offset) { 
                                setExtraKeysVisibility(View.GONE, false);
                                canvas.invalidate();
                                keyboardControls.enable();
                            }
                        }
                    } else {
                        offset = r.bottom - rootViewHeight;
                        //  Soft Kbd up, shift the meta keys and arrows up.
                        if (layoutKeys != null) {
                            layoutKeys.offsetTopAndBottom(offset);
                            keyStow.offsetTopAndBottom(offset);
                            if (prevBottomOffset != offset) { 
                                setExtraKeysVisibility(View.VISIBLE, true);
                                canvas.invalidate();
                                keyboardControls.hide();
                                keyboardControls.disable();
                            }
                        }
                    }
                    setKeyStowDrawableAndVisibility();
                    prevBottomOffset = offset;
             }
        });

        keyboardControls.hide();
        
        panner = new Panner(this, canvas.handler);

        inputHandler = getInputHandlerById(R.id.itemInputDragPanZoomMouse);
    }

    
    private void setKeyStowDrawableAndVisibility() {
        Drawable replacer = null;
        if (layoutKeys.getVisibility() == View.GONE)
            replacer = getResources().getDrawable(R.drawable.showkeys);
        else
            replacer = getResources().getDrawable(R.drawable.hidekeys);
        keyStow.setBackgroundDrawable(replacer);

        if (connection.getExtraKeysToggleType() == Constants.EXTRA_KEYS_OFF)
            keyStow.setVisibility(View.GONE);
        else
            keyStow.setVisibility(View.VISIBLE);
    }
    
    /**
     * Initializes the on-screen keys for meta keys and arrow keys.
     */
    private void initializeOnScreenKeys () {
        
        layoutKeys = (RelativeLayout) findViewById(R.id.layoutKeys);

        keyStow = (ImageButton)    findViewById(R.id.keyStow);
        setKeyStowDrawableAndVisibility();
        keyStow.setOnClickListener(new OnClickListener () {
            @Override
            public void onClick(View arg0) {
                if (layoutKeys.getVisibility() == View.VISIBLE) {
                    extraKeysHidden = true;
                    setExtraKeysVisibility(View.GONE, false);
                } else {
                    extraKeysHidden = false;
                    setExtraKeysVisibility(View.VISIBLE, true);
                }
                layoutKeys.offsetTopAndBottom(prevBottomOffset);
                setKeyStowDrawableAndVisibility();
            }
        });

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
                int key = 111; /* KEYCODE_ESCAPE */
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
                keyCtrlToggled = false;
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
                keyCtrlToggled = true;
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
                keySuperToggled = false;
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
                keySuperToggled = true;
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
                keyAltToggled = false;
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
                keyAltToggled = true;
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
                keyShiftToggled = false;
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
                keyShiftToggled = true;
                if (on)
                    keyShift.setImageResource(R.drawable.shifton);
                else
                    keyShift.setImageResource(R.drawable.shiftoff);
                return true;
            }
        });
        
        // TODO: Evaluate whether I should instead be using:
        // vncCanvas.sendMetaKey(MetaKeyBean.keyArrowLeft);

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
        if (!keyCtrlToggled) {
            keyCtrl.setImageResource(R.drawable.ctrloff);
            canvas.getKeyboard().onScreenCtrlOff();
        }
        if (!keyAltToggled) {
            keyAlt.setImageResource(R.drawable.altoff);
            canvas.getKeyboard().onScreenAltOff();
        }
        if (!keySuperToggled) {
            keySuper.setImageResource(R.drawable.superoff);
            canvas.getKeyboard().onScreenSuperOff();
        }
        if (!keyShiftToggled) {
            keyShift.setImageResource(R.drawable.shiftoff);
            canvas.getKeyboard().onScreenShiftOff();
        }
    }

    
    /**
     * Sets the visibility of the extra keys appropriately.
     */
    private void setExtraKeysVisibility (int visibility, boolean forceVisible) {
        Configuration config = getResources().getConfiguration();
        //Log.e(TAG, "Hardware kbd hidden: " + Integer.toString(config.hardKeyboardHidden));
        //Log.e(TAG, "Any keyboard hidden: " + Integer.toString(config.keyboardHidden));
        //Log.e(TAG, "Keyboard type: " + Integer.toString(config.keyboard));

        boolean makeVisible = forceVisible;
        if (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO)
            makeVisible = true;

        if (!extraKeysHidden && makeVisible && 
            connection.getExtraKeysToggleType() == Constants.EXTRA_KEYS_ON) {
            layoutKeys.setVisibility(View.VISIBLE);
            layoutKeys.invalidate();
            return;
        }
        
        if (visibility == View.GONE) {
            layoutKeys.setVisibility(View.GONE);
            layoutKeys.invalidate();
        }
    }
    
    /*
     * TODO: REMOVE THIS AS SOON AS POSSIBLE.
     * onPause: This is an ugly hack for the Playbook, because the Playbook hides the keyboard upon unlock.
     * This causes the visible height to remain less, as if the soft keyboard is still up. This hack must go 
     * away as soon as the Playbook doesn't need it anymore.
     */
    @Override
    protected void onPause(){
        super.onPause();
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(canvas.getWindowToken(), 0);
        } catch (NullPointerException e) { }
    }

    /*
     * TODO: REMOVE THIS AS SOON AS POSSIBLE.
     * onResume: This is an ugly hack for the Playbook which hides the keyboard upon unlock. This causes the visible
     * height to remain less, as if the soft keyboard is still up. This hack must go away as soon
     * as the Playbook doesn't need it anymore.
     */
    @Override
    protected void onResume(){
        super.onResume();
        Log.i(TAG, "onResume called.");
        try {
            canvas.postInvalidateDelayed(600);
        } catch (NullPointerException e) { }
    }
    
    /**
     * Set modes on start to match what is specified in the ConnectionBean;
     * scaling, input mode
     */
    void setModes() {
        AbstractInputHandler handler = getInputHandlerByName(connection.getInputMode());
        AbstractScaling.getByScaleType(connection.getScaleMode()).setScaleTypeForActivity(this);
        this.inputHandler = handler;
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onPrepareDialog(int, android.app.Dialog, android.os.Bundle)
     */
    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        super.onPrepareDialog(id, dialog, args);
        if (dialog instanceof ConnectionSettable)
            ((ConnectionSettable) dialog).setConnection(connection);
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
        canvas.scaling.setScaleTypeForActivity(RemoteCanvasActivity.this);
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
        
        try {
            setExtraKeysVisibility(View.GONE, false);
            
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
    protected void onStop() {
        super.onStop();
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
            getMenuInflater().inflate(R.menu.vnccanvasactivitymenu, menu);

            menu.findItem(canvas.scaling.getId()).setChecked(true);
    
            Menu inputMenu = menu.findItem(R.id.itemInputMode).getSubMenu();
            inputModeMenuItems = new MenuItem[inputModeIds.length];
            for (int i = 0; i < inputModeIds.length; i++) {
                inputModeMenuItems[i] = inputMenu.findItem(inputModeIds[i]);
            }
            updateInputMenu();
            
            Menu scalingMenu = menu.findItem(R.id.itemScaling).getSubMenu();
            scalingModeMenuItems = new MenuItem[scalingModeIds.length];
            for (int i = 0; i < scalingModeIds.length; i++) {
                scalingModeMenuItems[i] = scalingMenu.findItem(scalingModeIds[i]);
            }
            updateScalingMenu();
            
            // Set the text of the Extra Keys menu item appropriately.
            if (connection.getExtraKeysToggleType() == Constants.EXTRA_KEYS_ON)
                menu.findItem(R.id.itemExtraKeys).setTitle(R.string.extra_keys_disable);
            else
                menu.findItem(R.id.itemExtraKeys).setTitle(R.string.extra_keys_enable);
            
    /*        menu.findItem(R.id.itemFollowMouse).setChecked(
                    connection.getFollowMouse());
            menu.findItem(R.id.itemFollowPan).setChecked(connection.getFollowPan());
     */
    /* TODO: This is how one detects long-presses on menu items. However, getActionView is not available in Android 2.3...
            menu.findItem(R.id.itemExtraKeys).getActionView().setOnLongClickListener(new OnLongClickListener () {
    
                @Override
                public boolean onLongClick(View arg0) {
                    Toast.makeText(arg0.getContext(), "Long Press Detected.", Toast.LENGTH_LONG).show();
                    return false;
                }
                
            });
    */
        } catch (NullPointerException e) { }
        return true;
    }

    /**
     * Change the scaling mode sub-menu to reflect available scaling modes.
     */
    void updateScalingMenu() {
        try {
            for (MenuItem item : scalingModeMenuItems) {
                // If the entire framebuffer is NOT contained in the bitmap, fit-to-screen is meaningless.
                if (item.getItemId() == R.id.itemFitToScreen) {
                    if ( canvas != null && canvas.bitmapData != null &&
                         (canvas.bitmapData.bitmapheight != canvas.bitmapData.framebufferheight ||
                          canvas.bitmapData.bitmapwidth  != canvas.bitmapData.framebufferwidth) )
                        item.setEnabled(false);
                    else
                        item.setEnabled(true);
                } else
                    item.setEnabled(true);
            }
        } catch (NullPointerException e) { }
    }    
    
    /**
     * Change the input mode sub-menu to reflect change in scaling
     */
    void updateInputMenu() {
        try {
            for (MenuItem item : inputModeMenuItems) {
                item.setEnabled(canvas.scaling.isValidInputMode(item.getItemId()));
                if (getInputHandlerById(item.getItemId()) == inputHandler)
                    item.setChecked(true);
            }
        } catch (NullPointerException e) { }
    }

    /**
     * If id represents an input handler, return that; otherwise return null
     * 
     * @param id
     * @return
     */
    AbstractInputHandler getInputHandlerById(int id) {
        if (inputModeHandlers == null) {
            inputModeHandlers = new AbstractInputHandler[inputModeIds.length];
        }
        for (int i = 0; i < inputModeIds.length; ++i) {
            if (inputModeIds[i] == id) {
                if (inputModeHandlers[i] == null) {
                    switch (id) {
                    case R.id.itemInputDragPanZoomMouse:
                        inputModeHandlers[i] = new TouchMouseDragPanInputHandler(this, canvas);
                        break;
                    case R.id.itemInputTouchpad:
                        inputModeHandlers[i] = new SimulatedTouchpadInputHandler(this, canvas);
                        break;
                    }
                }
                return inputModeHandlers[i];
            }
        }
        return null;
    }

    void clearInputHandlers() {
        if (inputModeHandlers == null)
            return;

        for (int i = 0; i < inputModeIds.length; ++i) {
            inputModeHandlers[i] = null;
        }
        inputModeHandlers = null;
    }
    
    AbstractInputHandler getInputHandlerByName(String name) {
        AbstractInputHandler result = null;
        for (int id : inputModeIds) {
            AbstractInputHandler handler = getInputHandlerById(id);
            if (handler.getName().equals(name)) {
                result = handler;
                break;
            }
        }
        if (result == null) {
            result = getInputHandlerById(R.id.itemInputDragPanZoomMouse);
        }
        return result;
    }
    
    int getModeIdFromHandler(AbstractInputHandler handler) {
        for (int id : inputModeIds) {
            if (handler == getInputHandlerById(id))
                return id;
        }
        return R.id.itemInputDragPanZoomMouse;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        canvas.getKeyboard().setAfterMenu(true);
        switch (item.getItemId()) {
            // Following sets one of the scaling options
        case R.id.itemZoomable:
        case R.id.itemOneToOne:
        case R.id.itemFitToScreen:
            AbstractScaling.getById(item.getItemId()).setScaleTypeForActivity(this);
            item.setChecked(true);
            return true;
        case R.id.itemDisconnect:
            canvas.closeConnection();
            finish();
            return true;
        case R.id.itemCtrlAltDel:
            canvas.getKeyboard().sendMetaKey(MetaKeyBean.keyCtrlAltDel);
            return true;
/*        case R.id.itemFollowMouse:
            boolean newFollow = !connection.getFollowMouse();
            item.setChecked(newFollow);
            connection.setFollowMouse(newFollow);
            if (newFollow) {
                vncCanvas.panToMouse();
            }
            return true;
        case R.id.itemFollowPan:
            boolean newFollowPan = !connection.getFollowPan();
            item.setChecked(newFollowPan);
            connection.setFollowPan(newFollowPan);
            return true;
*/
        case R.id.itemExtraKeys:
            if (connection.getExtraKeysToggleType() == Constants.EXTRA_KEYS_ON) {
                connection.setExtraKeysToggleType(Constants.EXTRA_KEYS_OFF);
                item.setTitle(R.string.extra_keys_enable);
                setExtraKeysVisibility(View.GONE, false);
            } else {
                connection.setExtraKeysToggleType(Constants.EXTRA_KEYS_ON);
                item.setTitle(R.string.extra_keys_disable);
                setExtraKeysVisibility(View.VISIBLE, false);
                extraKeysHidden = false;
            }
            setKeyStowDrawableAndVisibility();
            return true;
        default:
            AbstractInputHandler input = getInputHandlerById(item.getItemId());
            if (input != null) {
                inputHandler = input;
                connection.setInputMode(input.getName());
                if (input.getName().equals(SimulatedTouchpadInputHandler.TOUCHPAD_MODE)) {
                    connection.setFollowMouse(true);
                    connection.setFollowPan(true);
                } else {
                    connection.setFollowMouse(false);
                    connection.setFollowPan(false);
                }

                item.setChecked(true);
                return true;
            }
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
        keyboardControls = null;
        panner = null;
        clearInputHandlers();
        inputHandler = null;
        System.gc();
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent evt) {

        boolean consumed = false;

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (evt.getAction() == KeyEvent.ACTION_DOWN)
                return super.onKeyDown(keyCode, evt);
            else
                return super.onKeyUp(keyCode, evt);
        }

        try {
            if (evt.getAction() == KeyEvent.ACTION_DOWN || evt.getAction() == KeyEvent.ACTION_MULTIPLE) {
                consumed = inputHandler.onKeyDown(keyCode, evt);
            } else if (evt.getAction() == KeyEvent.ACTION_UP){
                consumed = inputHandler.onKeyUp(keyCode, evt);
            }
            resetOnScreenKeys (keyCode);
        } catch (NullPointerException e) { }

        return consumed;
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onTrackballEvent(android.view.MotionEvent)
     */
    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        try {
            // If we are using the Dpad as arrow keys, don't send the event to the inputHandler.
            if (connection.getUseDpadAsArrows())
                return false;
            return inputHandler.onTrackballEvent(event);
        } catch (NullPointerException e) { }
        return super.onTrackballEvent(event);
    }

    // Send touch events or mouse events like button clicks to be handled.
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            return inputHandler.onTouchEvent(event);
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
                return inputHandler.onTouchEvent(event);
            } catch (NullPointerException e) { }
        }
        return super.onGenericMotionEvent(event);
    }

    long hideKeyboardControlsAfterMs;
    static final long KEYBOARD_CONTROLS_HIDE_DELAY_MS = 2500;
    HideKeyboardControlsRunnable hideKeyboardControlsInstance = new HideKeyboardControlsRunnable();

    public void stopPanner() {
        panner.stop ();
    }
    
    public void showKeyboardControls(boolean force) {
        if (force || keyboardControls.getVisibility() != View.VISIBLE) {
            keyboardControls.show();
            hideKeyboardControlsAfterMs = SystemClock.uptimeMillis() + KEYBOARD_CONTROLS_HIDE_DELAY_MS;
            canvas.handler.postAtTime(hideKeyboardControlsInstance, hideKeyboardControlsAfterMs + 10);
        }
    }

    private class HideKeyboardControlsRunnable implements Runnable {
        public void run() {
            if (SystemClock.uptimeMillis() >= hideKeyboardControlsAfterMs) {
                keyboardControls.hide();
            }
        }
    }
    
    public ConnectionBean getConnection() {
        return connection;
    }
    
    // Returns whether we are using D-pad/Trackball to send arrow key events.
    public boolean getUseDpadAsArrows() {
        return connection.getUseDpadAsArrows();
    }
    
    // Returns whether the D-pad should be rotated to accommodate BT keyboards paired with phones.
    public boolean getRotateDpad() {
        return connection.getRotateDpad();
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

    public void setCanvas(RemoteCanvas vncCanvas) {
        this.canvas = vncCanvas;
    }
    
    public Panner getPanner() {
        return panner;
    }

    public void setPanner(Panner panner) {
        this.panner = panner;
    }
}