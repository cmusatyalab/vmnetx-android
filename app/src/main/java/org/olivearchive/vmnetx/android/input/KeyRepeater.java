/**
 * Copyright (C) 2014-2015 Carnegie Mellon University
 * Copyright (C) 2013 Iordan Iordanov
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

package org.olivearchive.vmnetx.android.input;

import android.os.Handler;
import android.view.KeyEvent;

class KeyRepeater implements Runnable {
    private final int DELAY_INITIAL = 400;
    private final int DELAY_DEFAULT = 100;

    private final RemoteKeyboard keyboard;
    private final Handler handler;

    private KeyEvent event = null;
    private boolean starting = false;
    
    public KeyRepeater(RemoteKeyboard keyboard) {
        this.keyboard = keyboard;
        this.handler = new Handler();
    }
    
    public void start (KeyEvent event) {
        stop();
        this.event = event;
        // This is here in order to ensure the key event is sent over at least once.
        // Otherwise with very quick repeated sending of events, the removeCallbacks
        // call causes events to be deleted before they've been sent out even once.
        keyboard.processLocalKeyEvent(KeyEvent.changeAction(event, KeyEvent.ACTION_DOWN));
        keyboard.processLocalKeyEvent(KeyEvent.changeAction(event, KeyEvent.ACTION_UP));
        starting = true;
        handler.post(this);
    }
    
    public void stop () {
        handler.removeCallbacks(this);
    }
    
    @Override
    public void run() {
        int delay = DELAY_DEFAULT;
        if (starting) {
            starting = false;
            delay = DELAY_INITIAL;
        } else {
            keyboard.processLocalKeyEvent(KeyEvent.changeAction(event, KeyEvent.ACTION_DOWN));
            keyboard.processLocalKeyEvent(KeyEvent.changeAction(event, KeyEvent.ACTION_UP));
        }
        
        handler.postDelayed(this, delay);
    }
}
