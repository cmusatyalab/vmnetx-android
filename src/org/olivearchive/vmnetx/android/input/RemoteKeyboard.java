package org.olivearchive.vmnetx.android.input;

import android.os.Handler;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import org.olivearchive.vmnetx.android.SpiceCommunicator;

public class RemoteKeyboard {
    private static final String TAG = "RemoteKeyboard";

    private final SpiceCommunicator spice;
    private final KeyRepeater keyRepeater;
    private final ModifierKeyState modifiers;
    // State of the on-screen modifier key buttons
    private final ModifierKeyState.DeviceState onScreenButtons;

    public RemoteKeyboard (SpiceCommunicator s, Handler h) {
        spice = s;
        keyRepeater = new KeyRepeater (this, h);
        modifiers = new ModifierKeyState();
        onScreenButtons = modifiers.getOnScreenButtonState();
    }

    public boolean processLocalKeyEvent(KeyEvent evt) {
        int keyCode = evt.getKeyCode();
        //android.util.Log.d(TAG, evt.toString() + " " + keyCode);

        if (spice != null && spice.isInNormalProtocol()) {
            boolean down = (evt.getAction() == KeyEvent.ACTION_DOWN) ||
                           (evt.getAction() == KeyEvent.ACTION_MULTIPLE);
            int modifier = keyCodeToModifierMask(keyCode);
            if (modifier != 0) {
                ModifierKeyState.DeviceState state =
                        modifiers.getDeviceState(evt.getDeviceId());
                if (down)
                    state.press(modifier);
                else
                    state.release(modifier);
                updateModifierKeys();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                String s = evt.getCharacters();
                if (s != null) {
                    for (int i = 0; i < s.length(); i++) {
                        //android.util.Log.d(TAG, "Sending unicode: " + s.charAt(i));
                        sendUnicode(s.charAt(i));
                    }
                }
                return true;
            } else if (evt.getAction() == KeyEvent.ACTION_DOWN) {
                spice.pressAndReleaseKey(keyCode);
                return true;
            }
        }
        return false;
    }

    public void repeatKeyEvent(KeyEvent event) {
        keyRepeater.start(event);
    }

    public void stopRepeatingKeyEvent() {
        keyRepeater.stop();
    }

    public void sendCtrlAltDel() {
        spice.updateModifierKeys(KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_ALT_LEFT_ON);
        spice.pressAndReleaseKey(KeyEvent.KEYCODE_FORWARD_DEL);
        updateModifierKeys();
    }
    
    /**
     * Toggles on-screen modifier key.
     * @return true if key enabled, false otherwise.
     */
    public boolean onScreenModifierToggle(int modifier) {
        boolean keyDown = onScreenButtons.toggle(modifier);
        updateModifierKeys();
        return keyDown;
    }
    
    /**
     * Turns off on-screen modifier key.
     */
    public void onScreenModifierOff(int modifier) {
        onScreenButtons.release(modifier);
        updateModifierKeys();
    }

    public void clearOnScreenModifiers() {
        onScreenButtons.clear();
        updateModifierKeys();
    }
    
    private void updateModifierKeys() {
        spice.updateModifierKeys(modifiers.getModifiers());
    }

    /**
     * Tries to convert a unicode character to a KeyEvent and if successful sends with keyEvent().
     * @param unicodeChar
     */
    private void sendUnicode(char unicodeChar) {
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
                processLocalKeyEvent(evt);
            }
        } else {
            android.util.Log.w(TAG, "Could not use any keymap to generate KeyEvent for unicode: " + unicodeChar);
        }
    }

    private int keyCodeToModifierMask(int keyCode) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_ALT_LEFT:
            return KeyEvent.META_ALT_LEFT_ON;
        case KeyEvent.KEYCODE_ALT_RIGHT:
            return KeyEvent.META_ALT_RIGHT_ON;
        case KeyEvent.KEYCODE_CTRL_LEFT:
            return KeyEvent.META_CTRL_LEFT_ON;
        case KeyEvent.KEYCODE_CTRL_RIGHT:
            return KeyEvent.META_CTRL_RIGHT_ON;
        case KeyEvent.KEYCODE_META_LEFT:
            return KeyEvent.META_META_LEFT_ON;
        case KeyEvent.KEYCODE_META_RIGHT:
            return KeyEvent.META_META_RIGHT_ON;
        case KeyEvent.KEYCODE_SHIFT_LEFT:
            return KeyEvent.META_SHIFT_LEFT_ON;
        case KeyEvent.KEYCODE_SHIFT_RIGHT:
            return KeyEvent.META_SHIFT_RIGHT_ON;
        default:
            return 0;
        }
    }
}
