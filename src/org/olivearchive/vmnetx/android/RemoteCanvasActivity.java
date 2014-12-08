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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.View.OnKeyListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.RelativeLayout;


public class RemoteCanvasActivity extends Activity implements OnKeyListener {
    //private final static String TAG = "RemoteCanvasActivity";
    private final static String CONNECTION_KEY = "RemoteCanvasActivity.connection";
    
    private GestureHandler gestureHandler;

    private RemoteCanvas canvas;

    private ConnectionBean connection;

    private MenuItem keyboardMenuItem;

    private RelativeLayout layoutKeys;
    private OnScreenModifierKey keyCtrl;
    private OnScreenModifierKey keySuper;
    private OnScreenModifierKey keyAlt;
    private OnScreenModifierKey keyShift;
    
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
        
        gestureHandler = new AbsoluteMouseHandler(this, canvas);
    }

    /**
     * Initializes the on-screen keys for meta keys and arrow keys.
     */
    private void initializeOnScreenKeys () {
        
        layoutKeys = (RelativeLayout) findViewById(R.id.layoutKeys);

        // Define action of tab and escape keys.
        initializeOnScreenRegularKey(R.id.keyTab,
                R.drawable.tabon, R.drawable.taboff,
                KeyEvent.KEYCODE_TAB);
        initializeOnScreenRegularKey(R.id.keyEsc,
                R.drawable.escon, R.drawable.escoff,
                KeyEvent.KEYCODE_ESCAPE);

        // Define action of modifier keys.
        keyCtrl = new OnScreenModifierKey(canvas,
                (ImageButton) findViewById(R.id.keyCtrl),
                R.drawable.ctrlon, R.drawable.ctrloff,
                KeyEvent.META_CTRL_LEFT_ON);
        keySuper = new OnScreenModifierKey(canvas,
                (ImageButton) findViewById(R.id.keySuper),
                R.drawable.superon, R.drawable.superoff,
                KeyEvent.META_META_LEFT_ON);
        keyAlt = new OnScreenModifierKey(canvas,
                (ImageButton) findViewById(R.id.keyAlt),
                R.drawable.alton, R.drawable.altoff,
                KeyEvent.META_ALT_LEFT_ON);
        keyShift = new OnScreenModifierKey(canvas,
                (ImageButton) findViewById(R.id.keyShift),
                R.drawable.shifton, R.drawable.shiftoff,
                KeyEvent.META_SHIFT_LEFT_ON);
        
        // Define action of arrow keys.
        initializeOnScreenRegularKey(R.id.keyUpArrow,
                R.drawable.upon, R.drawable.upoff,
                KeyEvent.KEYCODE_DPAD_UP);
        initializeOnScreenRegularKey(R.id.keyDownArrow,
                R.drawable.downon, R.drawable.downoff,
                KeyEvent.KEYCODE_DPAD_DOWN);
        initializeOnScreenRegularKey(R.id.keyLeftArrow,
                R.drawable.lefton, R.drawable.leftoff,
                KeyEvent.KEYCODE_DPAD_LEFT);
        initializeOnScreenRegularKey(R.id.keyRightArrow,
                R.drawable.righton, R.drawable.rightoff,
                KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    private void initializeOnScreenRegularKey(int viewId,
            final int onImage, final int offImage, final int keyCode) {
        final ImageButton button = (ImageButton) findViewById(viewId);
        button.setOnTouchListener(new OnTouchListener () {
            @Override
            public boolean onTouch(View arg0, MotionEvent e) {
                RemoteKeyboard k = canvas.getKeyboard();
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    Utils.performLongPressHaptic(canvas);
                    button.setImageResource(onImage);
                    k.repeatKeyEvent(new KeyEvent(e.getAction(), keyCode));
                    return true;    
                } else if (e.getAction() == MotionEvent.ACTION_UP) {
                    button.setImageResource(offImage);
                    resetOnScreenKeys(0);
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
        case KeyEvent.KEYCODE_SHIFT_RIGHT:
            return;
        }
        keyCtrl.reset();
        keyAlt.reset();
        keySuper.reset();
        keyShift.reset();
    }

    /**
     * Set up scaling and input modes
     */
    void setModes() {
        // Update VM name first, since the later methods may throw
        // NullPointerException which the caller will swallow
        updateTitle();

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

    @TargetApi(21)
    private void updateTitle() {
        String vmName = canvas.getVMName();
        if (vmName != null) {
            setTitle(vmName);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setTaskDescription(
                        new ActivityManager.TaskDescription(vmName));
            }
        }
    }

    private void updateKeyboardMenuItem(Configuration config) {
        boolean softKeyboardEnabled = (config.hardKeyboardHidden !=
                Configuration.HARDKEYBOARDHIDDEN_NO);
        keyboardMenuItem.setEnabled(softKeyboardEnabled);
        keyboardMenuItem.setVisible(softKeyboardEnabled);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateKeyboardMenuItem(newConfig);
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
            return true;
        case R.id.itemExtraKeys:
            if (layoutKeys.getVisibility() == View.VISIBLE) {
                layoutKeys.setVisibility(View.GONE);
            } else {
                layoutKeys.setVisibility(View.VISIBLE);
            }
            layoutKeys.invalidate();
            return true;
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

        // Ignore keys that should be handled by the platform
        switch (keyCode) {
        case KeyEvent.KEYCODE_BACK:
        case KeyEvent.KEYCODE_BRIGHTNESS_DOWN:
        case KeyEvent.KEYCODE_BRIGHTNESS_UP:
        case KeyEvent.KEYCODE_CAMERA:
        case KeyEvent.KEYCODE_MENU:
        case KeyEvent.KEYCODE_MUTE:
        case KeyEvent.KEYCODE_NUM:
        case KeyEvent.KEYCODE_SYM:
        case KeyEvent.KEYCODE_VOLUME_DOWN:
        case KeyEvent.KEYCODE_VOLUME_MUTE:
        case KeyEvent.KEYCODE_VOLUME_UP:
            return false;
        }

        try {
            switch (evt.getAction()) {
            case KeyEvent.ACTION_DOWN:
            case KeyEvent.ACTION_MULTIPLE:
            case KeyEvent.ACTION_UP:
                RemoteKeyboard keyboard = canvas.getKeyboard();
                consumed = keyboard.processLocalKeyEvent(evt);
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
