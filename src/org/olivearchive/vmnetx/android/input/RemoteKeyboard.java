package org.olivearchive.vmnetx.android.input;

import android.content.Context;
import android.os.Handler;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import org.olivearchive.vmnetx.android.SpiceCommunicator;
import org.olivearchive.vmnetx.android.RemoteCanvas;

public class RemoteKeyboard {
    public final static int SCAN_ESC = 1;
    public final static int SCAN_LEFTCTRL = 29;
    public final static int SCAN_RIGHTCTRL = 97;
    public final static int SCAN_F1 = 59;
    public final static int SCAN_F2 = 60;
    public final static int SCAN_F3 = 61;
    public final static int SCAN_F4 = 62;
    public final static int SCAN_F5 = 63;
    public final static int SCAN_F6 = 64;
    public final static int SCAN_F7 = 65;
    public final static int SCAN_F8 = 66;
    public final static int SCAN_F9 = 67;
    public final static int SCAN_F10 = 68;
    //public final static int SCAN_HOME = 102;
    //public final static int SCAN_END = 107;
    
    // Useful shortcuts for modifier masks.
    public final static int CTRL_MASK  = KeyEvent.META_SYM_ON;
    public final static int SHIFT_MASK = KeyEvent.META_SHIFT_ON;
    public final static int ALT_MASK   = KeyEvent.META_ALT_ON;
    public final static int SUPER_MASK = 8;
    public final static int META_MASK  = 0;
    
    private SpiceCommunicator spice;
    private Context context;
    private KeyboardMapper keyboardMapper;
    private KeyRepeater keyRepeater;

    // Variable holding the state of any pressed hardware meta keys (Ctrl, Alt...)
    private int hardwareMetaState = 0;
    
    // Variable holding the state of the on-screen buttons for meta keys (Ctrl, Alt...)
    private int onScreenMetaState = 0;
    
    // Variable holding the state of the last metaState resulting from a button press.
    private int lastDownMetaState = 0;
    
    // Variable used for BB10 workarounds
    private boolean bb = false;
    
    // This variable tells us whether we need to skip junk characters for
    // SDK >= 16 and LatinIME next time a multi-character event comes along.
    public boolean skippedJunkChars = true;

    public RemoteKeyboard (SpiceCommunicator s, RemoteCanvas c, Handler h) {
        spice = s;
        keyRepeater = new KeyRepeater (this, h);
        
        if (android.os.Build.MODEL.contains("BlackBerry") ||
            android.os.Build.BRAND.contains("BlackBerry") || 
            android.os.Build.MANUFACTURER.contains("BlackBerry")) {
            bb = true;
        }

        context = c.getContext();

        keyboardMapper = new KeyboardMapper();
        keyboardMapper.init(context);
        keyboardMapper.setKeyProcessingListener((KeyboardMapper.KeyProcessingListener) s);
    }

    public boolean processLocalKeyEvent(int keyCode, KeyEvent evt) {
        return processLocalKeyEvent (keyCode, evt, 0);
    }
    
    public boolean processLocalKeyEvent(int keyCode, KeyEvent evt, int additionalMetaState) {
        //android.util.Log.e(TAG, evt.toString() + " " + keyCode);

        if (spice != null && spice.isInNormalProtocol()) {
            boolean down = (evt.getAction() == KeyEvent.ACTION_DOWN) ||
                           (evt.getAction() == KeyEvent.ACTION_MULTIPLE);
            
            if (keyCode == KeyEvent.KEYCODE_MENU)
                return true;                           // Ignore menu key

            // Detect whether this event is coming from a default hardware keyboard.
            boolean defaultHardwareKbd = (evt.getDeviceId() == 0);
            if (!down) {
                switch (evt.getScanCode()) {
                case SCAN_LEFTCTRL:
                case SCAN_RIGHTCTRL:
                    hardwareMetaState &= ~CTRL_MASK;
                    break;
                }
                
                switch(keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    hardwareMetaState &= ~CTRL_MASK;
                    break;
                case KeyEvent.KEYCODE_ALT_LEFT:
                    // Leaving KeyEvent.KEYCODE_ALT_LEFT for symbol input on hardware keyboards.
                    if (!defaultHardwareKbd)
                        hardwareMetaState &= ~ALT_MASK;
                    break;
                case KeyEvent.KEYCODE_ALT_RIGHT:
                    hardwareMetaState &= ~ALT_MASK;
                    break;
                }
            } else {
                // Look for standard scan-codes from hardware keyboards
                switch (evt.getScanCode()) {
                case SCAN_LEFTCTRL:
                case SCAN_RIGHTCTRL:
                    hardwareMetaState |= CTRL_MASK;
                    break;
                }
                
                switch(keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    hardwareMetaState |= CTRL_MASK;
                    break;
                case KeyEvent.KEYCODE_ALT_LEFT:
                    // Leaving KeyEvent.KEYCODE_ALT_LEFT for symbol input on hardware keyboards.
                    if (!defaultHardwareKbd)
                        hardwareMetaState |= ALT_MASK;
                    break;
                case KeyEvent.KEYCODE_ALT_RIGHT:
                    hardwareMetaState |= ALT_MASK;
                    break;
                }
            }

            // Update the meta-state with writeKeyEvent.
            int metaState = onScreenMetaState|hardwareMetaState|additionalMetaState|convertEventMetaState(evt);
            spice.writeKeyEvent(keyCode, metaState, down);
            if (down) {
                lastDownMetaState = metaState;
            } else {
                lastDownMetaState = 0;
            }
            
            if (keyCode == 0 /*KEYCODE_UNKNOWN*/) {
                String s = evt.getCharacters();
                if (s != null) {
                    int numchars = s.length();
                    int i = numJunkCharactersToSkip (numchars, evt);
                    for (; i < numchars; i++) {
                        //android.util.Log.e(TAG, "Sending unicode: " + s.charAt(i));
                        sendUnicode (s.charAt(i), metaState);
                    }
                }
                return true;
            } else {
                // Send the key to be processed through the KeyboardMapper.
                return keyboardMapper.processAndroidKeyEvent(evt);
            }
        } else {
            return false;
        }
    }

    public void repeatKeyEvent(int keyCode, KeyEvent event) { keyRepeater.start(keyCode, event); }

    public void stopRepeatingKeyEvent() { keyRepeater.stop(); }

    public void sendCtrlAltDel() {
        int savedMetaState = onScreenMetaState|hardwareMetaState;
        // Update the metastate
        spice.writeKeyEvent(0, CTRL_MASK | ALT_MASK, false);
        keyboardMapper.processAndroidKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, 112));
        keyboardMapper.processAndroidKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, 112));
        spice.writeKeyEvent(0, savedMetaState, false);
    }
    
    /**
     * Toggles on-screen Ctrl mask. Returns true if result is Ctrl enabled, false otherwise.
     * @return true if on false otherwise.
     */
    public boolean onScreenCtrlToggle()    {
        // If we find Ctrl on, turn it off. Otherwise, turn it on.
        if (onScreenMetaState == (onScreenMetaState | CTRL_MASK)) {
            onScreenCtrlOff();
            return false;
        }
        else {
            onScreenMetaState = onScreenMetaState | CTRL_MASK;
            return true;
        }
    }
    
    /**
     * Turns off on-screen Ctrl.
     */
    public void onScreenCtrlOff()    {
        onScreenMetaState = onScreenMetaState & ~CTRL_MASK;
    }
    
    /**
     * Toggles on-screen Alt mask.  Returns true if result is Alt enabled, false otherwise.
     * @return true if on false otherwise.
     */
    public boolean onScreenAltToggle() {
        // If we find Alt on, turn it off. Otherwise, turn it on.
        if (onScreenMetaState == (onScreenMetaState | ALT_MASK)) {
            onScreenAltOff();
            return false;
        }
        else {
            onScreenMetaState = onScreenMetaState | ALT_MASK;
            return true;
        }
    }

    /**
     * Turns off on-screen Alt.
     */
    public void onScreenAltOff()    {
        onScreenMetaState = onScreenMetaState & ~ALT_MASK;
    }

    /**
     * Toggles on-screen Super mask.  Returns true if result is Super enabled, false otherwise.
     * @return true if on false otherwise.
     */
    public boolean onScreenSuperToggle() {
        // If we find Super on, turn it off. Otherwise, turn it on.
        if (onScreenMetaState == (onScreenMetaState | SUPER_MASK)) {
            onScreenSuperOff();
            return false;
        }
        else {
            onScreenMetaState = onScreenMetaState | SUPER_MASK;
            return true;
        }
    }
    
    /**
     * Turns off on-screen Super.
     */
    public void onScreenSuperOff() {
        onScreenMetaState = onScreenMetaState & ~SUPER_MASK;        
    }
    
    /**
     * Toggles on-screen Shift mask.  Returns true if result is Shift enabled, false otherwise.
     * @return true if on false otherwise.
     */
    public boolean onScreenShiftToggle() {
        // If we find Super on, turn it off. Otherwise, turn it on.
        if (onScreenMetaState == (onScreenMetaState | SHIFT_MASK)) {
            onScreenShiftOff();
            return false;
        }
        else {
            onScreenMetaState = onScreenMetaState | SHIFT_MASK;
            return true;
        }
    }

    /**
     * Turns off on-screen Shift.
     */
    public void onScreenShiftOff() {
        onScreenMetaState = onScreenMetaState & ~SHIFT_MASK;
    }

    public int getMetaState () {
        return onScreenMetaState|lastDownMetaState;
    }
    
    public void clearMetaState () {
        onScreenMetaState = 0;
    }
    
    /**
     * Tries to convert a unicode character to a KeyEvent and if successful sends with keyEvent().
     * @param unicodeChar
     * @param metaState
     */
    public void sendUnicode (char unicodeChar, int additionalMetaState) {
        KeyCharacterMap fullKmap    = KeyCharacterMap.load(KeyCharacterMap.FULL);
        KeyCharacterMap virtualKmap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        char[] s = new char[1];
        s[0] = unicodeChar;
        
        KeyEvent[] events = fullKmap.getEvents(s);
        // Failing with the FULL keymap, try the VIRTUAL_KEYBOARD one.
        if (events == null) {
            events = virtualKmap.getEvents(s);
        }
        
        if (events != null) {
            for (int i = 0; i < events.length; i++) {
                KeyEvent evt = events[i];
                processLocalKeyEvent(evt.getKeyCode(), evt, additionalMetaState);
            }
        } else {
            android.util.Log.e("RemoteKeyboard", "Could not use any keymap to generate KeyEvent for unicode: " + unicodeChar);
        }
    }
    
    /**
     * Converts event meta state to our meta state.
     * @param event
     * @return
     */
    private int convertEventMetaState (KeyEvent event) {
        int metaState = 0;
        int eventMetaState = event.getMetaState();
        int altMask = KeyEvent.META_ALT_RIGHT_ON;
        // Detect whether this event is coming from a default hardware keyboard.
        // We have to leave KeyEvent.KEYCODE_ALT_LEFT for symbol input on a default hardware keyboard.
        boolean defaultHardwareKbd = (event.getDeviceId() == 0);
        if (!bb && !defaultHardwareKbd) {
            altMask = KeyEvent.META_ALT_MASK;
        }
        
        // Add shift, ctrl, alt, and super to metaState if necessary.
        if ((eventMetaState & 0x000000c1 /*KeyEvent.META_SHIFT_MASK*/) != 0)
            metaState |= SHIFT_MASK;
        if ((eventMetaState & 0x00007000 /*KeyEvent.META_CTRL_MASK*/) != 0)
            metaState |= CTRL_MASK;
        if ((eventMetaState & altMask) !=0)
            metaState |= ALT_MASK;
        if ((eventMetaState & 0x00070000 /*KeyEvent.META_META_MASK*/) != 0)
            metaState |= SUPER_MASK;
        return metaState;
    }
    
    
    /**
     * Used to calculate how many junk characters to skip.
     * @param numchars
     * @param evt
     * @return
     */
    private int numJunkCharactersToSkip (int numchars, KeyEvent evt) {
        int i = 0;
        if (!skippedJunkChars) {
            if (numchars == 10000) {
                // We received the event not because the user typed something but
                // because of another reason (for example lock/unlock screen).
                i = numchars;
            } else {
                // The user has typed at least one char, so we need to skip just the junk
                // characters, so skip backward until we hit the first junk character.
                for (i = Math.max(numchars - 2, 0); i > 0 ; i--) {
                    if (evt.getCharacters().charAt(i) == '%') {
                        i = i + 1;
                        break;
                    }
                }
                skippedJunkChars = true;
            }
        }
        return i;
    }
}
