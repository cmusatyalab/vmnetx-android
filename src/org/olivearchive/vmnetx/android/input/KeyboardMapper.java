/*
   Android Keyboard Mapping

   Copyright 2013 Thinstuff Technologies GmbH, Author: Martin Fleisz

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. 
   If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/


package org.olivearchive.vmnetx.android.input;

import android.view.KeyEvent;

import org.olivearchive.vmnetx.android.SpiceCommunicator;

class KeyboardMapper {

    private SpiceCommunicator spice;

    KeyboardMapper(SpiceCommunicator spice) {
        this.spice = spice;
    }

    public boolean processAndroidKeyEvent(KeyEvent event) {
        switch(event.getAction())
        {
            // we only process down events
            case KeyEvent.ACTION_UP:
            {
                return false;
            }            
            
            case KeyEvent.ACTION_DOWN:
            {    
                // if a modifier is pressed we will send a key event (if possible) so that key combinations will be
                // recognized correctly. Otherwise we will send the unicode key. At the end we will reset all modifiers
                // and notify SpiceCommunicator.
                int keycode = event.getKeyCode();
                // if we got a valid keycode send it - except for letters/numbers if a modifier is active
                if (keycode > 0 && (event.getMetaState() & (KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON | KeyEvent.META_SYM_ON)) == 0) {
                    spice.processVirtualKey(keycode, true);
                    spice.processVirtualKey(keycode, false);
                } else if (event.isShiftPressed() && keycode != 0) {
                    spice.processVirtualKey(KeyEvent.KEYCODE_SHIFT_LEFT, true);
                    spice.processVirtualKey(keycode, true);
                    spice.processVirtualKey(keycode, false);
                    spice.processVirtualKey(KeyEvent.KEYCODE_SHIFT_LEFT, false);
                } else if (event.getUnicodeChar() != 0)
                    spice.processUnicodeKey(event.getUnicodeChar());
                else
                    return false;
                             
                return true;
            }

            case KeyEvent.ACTION_MULTIPLE:
            {
                String str = event.getCharacters();
                for(int i = 0; i < str.length(); i++)
                    spice.processUnicodeKey(str.charAt(i));
                return true;
            }
            
            default:
                break;                
        }
        return false;
    }
}
