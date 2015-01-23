/**
 * Copyright (C) 2012 Iordan Iordanov
 * Copyright (C) 2009 Michael A. MacDonald
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

package org.olivearchive.vmnetx.android;

import android.graphics.Matrix;
import android.widget.ImageView;

/**
 * @author Michael A. MacDonald
 * 
 * A scaling mode for the RemoteCanvas
 */
public class Scaling {
    static final String TAG = "Scaling";

    private final RemoteCanvas canvas;
    private final Matrix matrix = new Matrix();

    private int canvasXOffset;
    private int canvasYOffset;
    private float scaling = 1;
    private float minimumScale;

    public Scaling(RemoteCanvas canvas) {
        this.canvas = canvas;
    }

    /**
     * Returns the scale factor of this scaling mode.
     * @return
     */
    float getScale() {
        return scaling;
    }

    private void setScale(float scale) {
        scaling = scale;
        matrix.reset();
        matrix.preTranslate(canvasXOffset, canvasYOffset);
        matrix.postScale(scaling, scaling);
        canvas.setImageMatrix(matrix);
        canvas.scrollToAbsolute(true);
    }

    /**
     * Update state from canvas
     * @param activity
     */
    void update() {
        canvas.setScaleType(ImageView.ScaleType.MATRIX);
        canvas.computeShiftFromFullToView();
        canvasXOffset = -canvas.getCenteredXOffset();
        canvasYOffset = -canvas.getCenteredYOffset();

        boolean zoomedOut = (scaling <= minimumScale);
        minimumScale = canvas.getMinimumScale();
        if (zoomedOut) {
            // We were fully zoomed out; stay that way
            setScale(minimumScale);
        } else {
            setScale(Math.max(scaling, minimumScale));
        }
    }

    /**
     * Change the scaling and focus dynamically, as from a detected scale gesture
     * @param activity Activity containing to canvas to scale
     * @param scaleFactor Factor by which to adjust scaling
     * @param fx Focus X of center of scale change
     * @param fy Focus Y of center of scale change
     */
    public void adjust(float scaleFactor, float fx, float fy) {
        float oldScale;
        float newScale = scaleFactor * scaling;
        if (scaleFactor < 1)
        {
            if (newScale < minimumScale)
            {
                newScale = minimumScale;
            }
        }
        else
        {
            if (newScale > 4)
            {
                newScale = 4;
            }
        }

        // ax is the absolute x of the focus
        int xPan = canvas.getAbsoluteX();
        float ax = (fx / scaling) + xPan;
        float newXPan = (scaling * xPan - scaling * ax + newScale * ax)/newScale;
        int yPan = canvas.getAbsoluteY();
        float ay = (fy / scaling) + yPan;
        float newYPan = (scaling * yPan - scaling * ay + newScale * ay)/newScale;

        // Here we do snapping to 1:1. If we are approaching scale = 1, we snap to it.
        oldScale = scaling;
        if ( (newScale > 0.90f && newScale < 1.00f) ||
             (newScale > 1.00f && newScale < 1.10f) ) {
            newScale = 1.f;
            // Only if oldScale is outside the snap region, do we inform the user.
            if (oldScale < 0.90f || oldScale > 1.10f)
                canvas.displayShortToastMessage(R.string.snap_one_to_one);
        }

        setScale(newScale);

        // Only if we have actually scaled do we pan.
        if (oldScale != newScale) {
            canvas.pan((int)(newXPan - xPan), (int)(newYPan - yPan));
        }
    }
}
