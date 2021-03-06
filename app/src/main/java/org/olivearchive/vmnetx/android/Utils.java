/**
 * Copyright (C) 2012 Iordan Iordanov
 * Copyright (C) 2010 Michael A. MacDonald
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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.text.Html;
import android.view.View;
import android.view.HapticFeedbackConstants;

public class Utils {

    public static void showYesNoPrompt(Context _context, String title, String message, OnClickListener onYesListener, OnClickListener onNoListener, OnCancelListener onCancelListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(_context);
        builder.setTitle(title);
        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setMessage(message);
        builder.setPositiveButton("Yes", onYesListener);
        builder.setNegativeButton("No", onNoListener);
        builder.setOnCancelListener(onCancelListener);
        boolean show = true;
        if ( _context instanceof Activity ) {
            Activity activity = (Activity) _context;
            if (activity.isFinishing()) {
                show = false;
            }
        }
        if (show)
            builder.show();
    }
    
    public static void showErrorMessage(Context _context, String message) {
        showMessage(_context, "Error!", message, android.R.drawable.ic_dialog_alert, null);
    }

    public static void showFatalErrorMessage(final Context _context, String message) {
        showMessage(_context, "Error!", message, android.R.drawable.ic_dialog_alert, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ((Activity) _context).finish();
            }
        });
    }
    
    public static void showMessage(Context _context, String title, String message, int icon, DialogInterface.OnClickListener ackHandler) {
        AlertDialog.Builder builder = new AlertDialog.Builder(_context);
        builder.setTitle(title);
        builder.setMessage(Html.fromHtml(message));
        builder.setCancelable(false);
        builder.setPositiveButton("Acknowledged", ackHandler);
        builder.setIcon(icon);
        boolean show = true;
        if ( _context instanceof Activity ) {
            Activity activity = (Activity) _context;
            if (activity.isFinishing()) {
                show = false;
            }
        }
        if (show)
            builder.show();
    }
    
    public static boolean performLongPressHaptic(View v) {
        return v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING|HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                );
    }
}
