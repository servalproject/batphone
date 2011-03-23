/*
 * Copyright (C) 2009 The Sipdroid Open Source Project
 * Copyright (C) 2005 Luca Veltri - University of Parma - Italy
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

package org.sipdroid.sipua;

import org.zoolu.tools.Configure;

/**
 * UserProfile maintains the user configuration
 */
public class UserAgentProfile extends Configure {
	public final String from_url = "4000@127.0.0.1";
	/**
	 * Contact URL. If not defined (default), it is formed by
	 * sip:local_user@host_address:host_port
	 */
	public String contact_url = null;
	public final String username = "4000";
	public String realm = "127.0.0.1";
	public final String realm_orig = "127.0.0.1";
	public final String passwd = "";
	public final boolean pub = true;
	
	/**
	 * Path for the 'ua.jar' lib, used to retrive various UA media (gif, wav,
	 * etc.) By default, it is used the "lib/ua.jar" folder
	 */
	public static String ua_jar = "lib/ua.jar";

	/** Whether registering with the registrar server */
	public boolean do_register = false;
	/** Whether unregistering the contact address */
	public boolean do_unregister = false;
	/**
	 * Whether unregistering all contacts beafore registering the contact
	 * address
	 */
	public boolean do_unregister_all = false;
	/** Expires time (in seconds). */
	public int expires = 3600;

	/**
	 * Rate of keep-alive packets sent toward the registrar server (in
	 * milliseconds). Set keepalive_time=0 to disable the sending of keep-alive
	 * datagrams.
	 */
	public long keepalive_time = 0;

	/**
	 * Automatic call a remote user secified by the 'call_to' value. Use value
	 * 'NONE' for manual calls (or let it undefined).
	 */
	public String call_to = null;
	/**
	 * Automatic answer time in seconds; time<0 corresponds to manual answer
	 * mode.
	 */
	public int accept_time = -1;
	/**
	 * Automatic hangup time (call duartion) in seconds; time<=0 corresponds to
	 * manual hangup mode.
	 */
	public int hangup_time = -1;
	/**
	 * Automatic call transfer time in seconds; time<0 corresponds to no auto
	 * transfer mode.
	 */
	public int transfer_time = -1;
	/**
	 * Automatic re-inviting time in seconds; time<0 corresponds to no auto
	 * re-invite mode.
	 */
	public int re_invite_time = -1;

	/**
	 * Redirect incoming call to the secified url. Use value 'NONE' for not
	 * redirecting incoming calls (or let it undefined).
	 */
	public String redirect_to = null;

	/**
	 * Transfer calls to the secified url. Use value 'NONE' for not transferring
	 * calls (or let it undefined).
	 */
	public String transfer_to = null;

	/** No offer in the invite */
	public boolean no_offer = false;
	/** Do not use prompt */
	public boolean no_prompt = false;

	/** Whether using audio */
	public boolean audio = true; // modified
	/** Whether using video */
	public boolean video = true; // modified

	/** Whether playing in receive only mode */
	public boolean recv_only = false;
	/** Whether playing in send only mode */
	public boolean send_only = false;
	/** Whether playing a test tone in send only mode */
	public boolean send_tone = false;
	/** Audio file to be played */
	public String send_file = null;
	/** Audio file to be recorded */
	public String recv_file = null;

	/** Audio port */
	public int audio_port = 21000;
	public int[] audio_codecs = {3, 8, 0};
	public int dtmf_avp = 101; // zero means no use of outband DTMF
	/** Audio sample rate */
	public int audio_sample_rate = 8000;
	/** Audio sample size */
	public int audio_sample_size = 1;
	/** Audio frame size */
	public int audio_frame_size = 160;

	/** Video port */
	public int video_port = 21070;
	/** Video avp */
	public int video_avp = 103;
}
