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

import android.graphics.Bitmap;
import android.graphics.Canvas;

class CompactBitmapData extends AbstractBitmapData {
    static final Bitmap.Config cfg = Bitmap.Config.ARGB_8888;
    
    class CompactBitmapDrawable extends AbstractBitmapDrawable {
        
        CompactBitmapDrawable()    {
            super(CompactBitmapData.this);
        }

        /* (non-Javadoc)
         * @see android.graphics.drawable.DrawableContainer#draw(android.graphics.Canvas)
         */
        @Override
        public void draw(Canvas canvas) {
            try {
                synchronized (mbitmap) {
                    canvas.drawBitmap(data.mbitmap, 0.0f, 0.0f, _defaultPaint);
                    canvas.drawBitmap(softCursor, cursorRect.left, cursorRect.top, _defaultPaint);
                }
            } catch (Throwable e) { }
        }
    }
    
    CompactBitmapData(SpiceCommunicator spice, RemoteCanvas c)
    {
        super(spice, c);
        bitmapwidth=framebufferwidth;
        bitmapheight=framebufferheight;
        // To please createBitmap, we ensure the size it at least 1x1.
        if (bitmapwidth  == 0) bitmapwidth  = 1;
        if (bitmapheight == 0) bitmapheight = 1;

        mbitmap = Bitmap.createBitmap(bitmapwidth, bitmapheight, cfg);
        mbitmap.setHasAlpha(false);
    }

    /* (non-Javadoc)
     * @see org.olivearchive.vmnetx.android.AbstractBitmapData#createDrawable()
     */
    @Override
    AbstractBitmapDrawable createDrawable() {
        return new CompactBitmapDrawable();
    }

    /* (non-Javadoc)
     * @see org.olivearchive.vmnetx.android.AbstractBitmapData#frameBufferSizeChanged(RfbProto)
     */
    @Override
    public void frameBufferSizeChanged () {
        framebufferwidth = spice.framebufferWidth();
        framebufferheight = spice.framebufferHeight();
        android.util.Log.i("CBM", "bitmapsize changed = ("+bitmapwidth+","+bitmapheight+")");
        if ( bitmapwidth < framebufferwidth || bitmapheight < framebufferheight ) {
            dispose();
            // Try to free up some memory.
            System.gc();
            bitmapwidth  = framebufferwidth;
            bitmapheight = framebufferheight;
            mbitmap      = Bitmap.createBitmap(bitmapwidth, bitmapheight, cfg);
            drawable     = createDrawable();
        }
    }
}
