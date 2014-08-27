/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Copyright (C) 2011 Michael A. MacDonald
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

import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.InputMethodManager;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

/**
 * The {@code KeyboardControls} class displays a simple set of controls used for
 * controlling the soft keyboard. */
public class KeyboardControls extends LinearLayout {

    private ImageButton showKeyboard;
    private boolean disabled = false;

    public KeyboardControls(Context context) {
        this(context, null);
    }

    public KeyboardControls(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        showKeyboard = (ImageButton) findViewById(R.id.showKeyboard);
        showKeyboard.setOnClickListener(new View.OnClickListener() {
            /*
             * (non-Javadoc)
             * 
             * @see android.view.View.OnClickListener#onClick(android.view.View)
             */
            @Override
            public void onClick(View v) {
                InputMethodManager inputMgr = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMgr.toggleSoftInput(0, 0);
            }
        });
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        
        /* Consume all touch events so they don't get dispatched to the view
         * beneath this view.
         */
        return true;
    }
    
    public void show() {
    	if (!disabled)
    		setVisibility(View.VISIBLE);
    }
    
    public void hide() {
		setVisibility(View.GONE);
    }

    public void disable() {
    	disabled = true;
    }

    public void enable() {
    	disabled = false;
    }
    
    @Override
    public boolean hasFocus() {
        return false;
    }
}