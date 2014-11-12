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

    private Matrix matrix;
    private int canvasXOffset;
    private int canvasYOffset;
    private float scaling;
    private float minimumScale;

    public Scaling() {
        matrix = new Matrix();
        scaling = 1;
    }

    /**
     * Returns the scale factor of this scaling mode.
     * @return
     */
    float getScale() {
        return scaling;
    }

    /**
     * Update state from canvas
     * @param activity
     */
    void updateForCanvas(RemoteCanvas canvas) {
        canvas.setScaleType(ImageView.ScaleType.MATRIX);
        canvasXOffset = -canvas.getCenteredXOffset();
        canvasYOffset = -canvas.getCenteredYOffset();
        canvas.computeShiftFromFullToView ();
        minimumScale = canvas.getMinimumScale();
        scaling = minimumScale;
        resetMatrix();
        matrix.postScale(scaling, scaling);
        canvas.setImageMatrix(matrix);
        resolveZoom(canvas);
    }

    /**
     * Change the scaling and focus dynamically, as from a detected scale gesture
     * @param activity Activity containing to canvas to scale
     * @param scaleFactor Factor by which to adjust scaling
     * @param fx Focus X of center of scale change
     * @param fy Focus Y of center of scale change
     */
    public void adjust(RemoteCanvasActivity activity, float scaleFactor, float fx, float fy) {
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

        RemoteCanvas canvas = activity.getCanvas();
        // ax is the absolute x of the focus
        int xPan = canvas.absoluteXPosition;
        float ax = (fx / scaling) + xPan;
        float newXPan = (scaling * xPan - scaling * ax + newScale * ax)/newScale;
        int yPan = canvas.absoluteYPosition;
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

        resetMatrix();
        scaling = newScale;
        matrix.postScale(scaling, scaling);
        canvas.setImageMatrix(matrix);
        resolveZoom(canvas);

        // Only if we have actually scaled do we pan.
        if (oldScale != newScale) {
            canvas.pan((int)(newXPan - xPan), (int)(newYPan - yPan));
        }
    }

    /**
     * Call after scaling and matrix have been changed to resolve scrolling
     * @param activity
     */
    private void resolveZoom(RemoteCanvas canvas)
    {
        canvas.scrollToAbsolute();
    }

    private void resetMatrix()
    {
        matrix.reset();
        matrix.preTranslate(canvasXOffset, canvasYOffset);
    }
}
