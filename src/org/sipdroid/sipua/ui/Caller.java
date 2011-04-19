package org.sipdroid.sipua.ui;

/*
 * Copyright (C) 2009 The Sipdroid Open Source Project
 * 
 * This file is part of Sipdroid (http://www.sipdroid.org)
 * 
 * Sipdroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

import org.sipdroid.sipua.SipdroidEngine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class Caller extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
	        String intentAction = intent.getAction();
	        String number = getResultData();
	        
	        if (intentAction.equals(Intent.ACTION_NEW_OUTGOING_CALL) && number != null)
	        {
 				if (number.endsWith("+")) 
    			{
    				number = number.substring(0,number.length()-1);
    				setResultData(number);
    				return;
    			}

	        	if (!SipdroidEngine.isRegistered()) return;
        		Log.i("SipDroid","outgoing call");
    	        
				setResultData(null);
		        intent = new Intent(Intent.ACTION_CALL,
		                Uri.fromParts("sip", Uri.decode(number), null));
		        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		        context.startActivity(intent);					
	        }
	    }
		
}
