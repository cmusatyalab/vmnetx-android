/*
   Taken from:
   Android FreeRDP JNI Wrapper

   Copyright 2013 Thinstuff Technologies GmbH, Author: Martin Fleisz

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. 
   If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package com.iiordanov.bVNC;

public interface UIEventListener
{
	void OnSettingsChanged(int width, int height, int bpp);
	boolean OnAuthenticate(StringBuilder username, StringBuilder domain, StringBuilder password);
	boolean OnVerifiyCertificate(String subject, String issuer, String fingerprint);
	void OnGraphicsUpdate(int x, int y, int width, int height);		
	void OnGraphicsResize(int width, int height, int bpp);		
}
