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
import android.widget.ImageView.ScaleType;

/**
 * @author Michael A. MacDonald
 */
class FitToScreenScaling extends AbstractScaling {
    
    static final String TAG = "FitToScreenScaling";
    
    private Matrix matrix;
    private int canvasXOffset;
    private int canvasYOffset;
    private float scaling;
    
    /**
     * @param id
     * @param scaleType
     */
    public FitToScreenScaling() {
        super(R.id.itemFitToScreen, ScaleType.FIT_CENTER);
        matrix = new Matrix();
        scaling = 0;
    }

    /* (non-Javadoc)
     * @see org.olivearchive.vmnetx.android.AbstractScaling#isAbleToPan()
     */
    @Override
    boolean isAbleToPan() {
        return false;
    }

    /**
     * Call after scaling and matrix have been changed to resolve scrolling
     * @param activity
     */
    private void resolveZoom(RemoteCanvasActivity activity)
    {
        activity.getCanvas().scrollToAbsolute();
    }
    
    /* (non-Javadoc)
     * @see org.olivearchive.vmnetx.android.AbstractScaling#getScale()
     */
    @Override
    float getScale() {
        return scaling;
    }

    private void resetMatrix()
    {
        matrix.reset();
        matrix.preTranslate(canvasXOffset, canvasYOffset);
    }

    /* (non-Javadoc)
     * @see org.olivearchive.vmnetx.android.AbstractScaling#setScaleTypeForActivity(org.olivearchive.vmnetx.android.RemoteCanvasActivity, org.olivearchive.vmnetx.android.ConnectionBean)
     */
    @Override
    void setScaleTypeForActivity(RemoteCanvasActivity activity, ConnectionBean connection) {
        super.setScaleTypeForActivity(activity, connection);
        RemoteCanvas canvas = activity.getCanvas();
        canvas.absoluteXPosition = 0;
        canvas.absoluteYPosition = 0;
        canvasXOffset = -canvas.getCenteredXOffset();
        canvasYOffset = -canvas.getCenteredYOffset();
        canvas.computeShiftFromFullToView ();
        scaling = canvas.bitmapData.getMinimumScale();
        resetMatrix();
        matrix.postScale(scaling, scaling);
        canvas.setImageMatrix(matrix);
        resolveZoom(activity);
        canvas.pan(0, 0);
    }
}
