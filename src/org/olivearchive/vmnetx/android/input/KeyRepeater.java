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
    
    public KeyRepeater (RemoteKeyboard keyboard, Handler handler) {
        this.keyboard = keyboard;
        this.handler = handler;
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
