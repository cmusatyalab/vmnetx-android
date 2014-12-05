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
                int keycode = event.getKeyCode();
                // if we got a valid keycode send it
                if (keycode > 0) {
                    spice.processVirtualKey(keycode, true);
                    spice.processVirtualKey(keycode, false);
                } else
                    return false;
                             
                return true;
            }
            
            default:
                break;                
        }
        return false;
    }
}
