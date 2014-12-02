package org.olivearchive.vmnetx.android.input;

import android.os.Handler;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import org.olivearchive.vmnetx.android.SpiceCommunicator;

public class RemoteKeyboard {
    private SpiceCommunicator spice;
    private KeyboardMapper keyboardMapper;
    private KeyRepeater keyRepeater;

    // Variable holding the state of any pressed hardware meta keys (Ctrl, Alt...)
    private int hardwareMetaState = 0;
    
    // Variable holding the state of the on-screen buttons for meta keys (Ctrl, Alt...)
    private int onScreenMetaState = 0;
    
    public RemoteKeyboard (SpiceCommunicator s, Handler h) {
        spice = s;
        keyRepeater = new KeyRepeater (this, h);
        
        keyboardMapper = new KeyboardMapper();
        keyboardMapper.setKeyProcessingListener((KeyboardMapper.KeyProcessingListener) s);
    }

    public boolean processLocalKeyEvent(KeyEvent evt) {
        return processLocalKeyEvent(evt, 0);
    }
    
    private boolean processLocalKeyEvent(KeyEvent evt, int additionalMetaState) {
        int keyCode = evt.getKeyCode();
        //android.util.Log.e(TAG, evt.toString() + " " + keyCode);

        if (spice != null && spice.isInNormalProtocol()) {
            boolean down = (evt.getAction() == KeyEvent.ACTION_DOWN) ||
                           (evt.getAction() == KeyEvent.ACTION_MULTIPLE);
            int modifier = keyCodeToModifierMask(keyCode);
            if (modifier != 0) {
                if (down)
                    hardwareMetaState |= modifier;
                else
                    hardwareMetaState &= ~modifier;
            }

            // Update the meta-state with writeKeyEvent.
            int metaState = onScreenMetaState|hardwareMetaState|additionalMetaState|convertEventMetaState(evt);
            spice.writeKeyEvent(keyCode, metaState, down);
            
            if (keyCode == 0 /*KEYCODE_UNKNOWN*/) {
                String s = evt.getCharacters();
                if (s != null) {
                    for (int i = 0; i < s.length(); i++) {
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

    public void repeatKeyEvent(KeyEvent event) {
        keyRepeater.start(event);
    }

    public void stopRepeatingKeyEvent() {
        keyRepeater.stop();
    }

    public void sendCtrlAltDel() {
        int savedMetaState = onScreenMetaState|hardwareMetaState;
        // Update the metastate
        spice.writeKeyEvent(0, KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON, false);
        keyboardMapper.processAndroidKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL));
        keyboardMapper.processAndroidKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_FORWARD_DEL));
        spice.writeKeyEvent(0, savedMetaState, false);
    }
    
    /**
     * Toggles on-screen modifier key.
     * @return true if key enabled, false otherwise.
     */
    public boolean onScreenModifierToggle(int modifier) {
        onScreenMetaState ^= modifier;
        return (onScreenMetaState | modifier) != 0;
    }
    
    /**
     * Turns off on-screen modifier key.
     */
    public void onScreenModifierOff(int modifier) {
        onScreenMetaState = onScreenMetaState & ~modifier;
    }

    public void clearOnScreenModifiers() {
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
                processLocalKeyEvent(evt, additionalMetaState);
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
        
        // Add shift, ctrl, alt, and super to metaState if necessary.
        if ((eventMetaState & 0x000000c1 /*KeyEvent.META_SHIFT_MASK*/) != 0)
            metaState |= KeyEvent.META_SHIFT_ON;
        if ((eventMetaState & 0x00007000 /*KeyEvent.META_CTRL_MASK*/) != 0)
            metaState |= KeyEvent.META_CTRL_ON;
        if ((eventMetaState & KeyEvent.META_ALT_MASK) !=0)
            metaState |= KeyEvent.META_ALT_ON;
        if ((eventMetaState & 0x00070000 /*KeyEvent.META_META_MASK*/) != 0)
            metaState |= KeyEvent.META_META_ON;
        return metaState;
    }

    private int keyCodeToModifierMask(int keyCode) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_ALT_LEFT:
        case KeyEvent.KEYCODE_ALT_RIGHT:
            return KeyEvent.META_ALT_ON;
        case KeyEvent.KEYCODE_CTRL_LEFT:
        case KeyEvent.KEYCODE_CTRL_RIGHT:
            return KeyEvent.META_CTRL_ON;
        case KeyEvent.KEYCODE_META_LEFT:
        case KeyEvent.KEYCODE_META_RIGHT:
            return KeyEvent.META_META_ON;
        case KeyEvent.KEYCODE_SHIFT_LEFT:
        case KeyEvent.KEYCODE_SHIFT_RIGHT:
            return KeyEvent.META_SHIFT_ON;
        default:
            return 0;
        }
    }
}
