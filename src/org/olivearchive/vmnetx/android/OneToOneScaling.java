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
class OneToOneScaling extends AbstractScaling {

    static final String TAG = "OneToOneScaling";

    private Matrix matrix;
    private int canvasXOffset;
    private int canvasYOffset;

    /**
     * @param id
     * @param scaleType
     */
    public OneToOneScaling() {
        super(R.id.itemOneToOne,ScaleType.CENTER);
        matrix = new Matrix();
    }
    
    /* (non-Javadoc)
     * @see org.olivearchive.vmnetx.android.AbstractScaling#isAbleToPan()
     */
    @Override
    boolean isAbleToPan() {
        return true;
    }

    /**
     * Call after scaling and matrix have been changed to resolve scrolling
     * @param activity
     */
    private void resolveZoom(RemoteCanvas canvas)
    {
        canvas.scrollToAbsolute();
        //activity.vncCanvas.pan(0,0);
    }
    
    /* (non-Javadoc)
     * @see org.olivearchive.vmnetx.android.AbstractScaling#getScale()
     */
    @Override
    float getScale() {
        return 1;
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
        canvasXOffset = -canvas.getCenteredXOffset();
        canvasYOffset = -canvas.getCenteredYOffset();
        canvas.computeShiftFromFullToView ();
        resetMatrix();
        matrix.postScale(1, 1);
        canvas.setImageMatrix(matrix);
        resolveZoom(canvas);
        //activity.vncCanvas.pan(0, 0);
    }
}
