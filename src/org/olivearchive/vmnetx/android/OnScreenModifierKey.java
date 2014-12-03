package org.olivearchive.vmnetx.android;

import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ImageButton;

class OnScreenModifierKey {
    private final RemoteCanvas canvas;
    private final ImageButton button;
    private final int onImage;
    private final int offImage;
    private final int modifier;

    private boolean locked;

    OnScreenModifierKey(final RemoteCanvas canvas, ImageButton button,
            int onImage, int offImage, final int modifier) {
        this.canvas = canvas;
        this.button = button;
        this.onImage = onImage;
        this.offImage = offImage;
        this.modifier = modifier;

        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                boolean on = canvas.getKeyboard().onScreenModifierToggle(modifier);
                locked = false;
                setImage(on);
            }
        });

        button.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View arg0) {
                Utils.performLongPressHaptic(canvas);
                boolean on = canvas.getKeyboard().onScreenModifierToggle(modifier);
                locked = true;
                setImage(on);
                return true;
            }
        });
    }

    void reset() {
        if (!locked) {
            setImage(false);
            canvas.getKeyboard().onScreenModifierOff(modifier);
        }
    }

    private void setImage(boolean on) {
        if (on)
            button.setImageResource(onImage);
        else
            button.setImageResource(offImage);
    }
}
