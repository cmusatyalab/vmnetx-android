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

import android.widget.ImageView;

/**
 * @author Michael A. MacDonald
 * 
 * A scaling mode for the RemoteCanvas; based on ImageView.ScaleType
 */
public abstract class AbstractScaling {
    /**
     * Returns the scale factor of this scaling mode.
     * @return
     */
    float getScale() { return 1.f; }

    void zoomIn(RemoteCanvasActivity activity) {}
    void zoomOut(RemoteCanvasActivity activity) {}
    
    protected ImageView.ScaleType scaleType;
    
    protected AbstractScaling(ImageView.ScaleType scaleType)
    {
        this.scaleType = scaleType;
    }
    
    /**
     * Sets the activity's scale type to the scaling
     * @param activity
     */
    void setScaleTypeForActivity(RemoteCanvasActivity activity) {
        RemoteCanvas canvas = activity.getCanvas();
        activity.keyboardControls.hide();
        canvas.scaling = this;
        canvas.setScaleType(ImageView.ScaleType.MATRIX);
    }
    
    /**
     * True if this scale type allows panning of the image
     * @return
     */
    abstract boolean isAbleToPan();
    
    /**
     * Change the scaling and focus dynamically, as from a detected scale gesture
     * @param activity Activity containing to canvas to scale
     * @param scaleFactor Factor by which to adjust scaling
     * @param fx Focus X of center of scale change
     * @param fy Focus Y of center of scale change
     */
    public void adjust(RemoteCanvasActivity activity, float scaleFactor, float fx, float fy) { }
}
