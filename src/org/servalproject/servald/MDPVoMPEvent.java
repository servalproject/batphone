package org.servalproject.servald;


public class MDPVoMPEvent extends MDPMessage {
	int call_session_token;
	long audio_sample_endtime;
	long audio_sample_starttime;
	long last_activity;
	public static final int VOMPEVENT_RINGING = (1<<0);
	public static final int VOMPEVENT_CALLENDED =(1<<1);
	public static final int VOMPEVENT_CALLREJECT =(1<<2);
	public static final int VOMPEVENT_HANGUP =VOMPEVENT_CALLREJECT;
	public static final int VOMPEVENT_TIMEOUT =(1<<3);
	public static final int VOMPEVENT_ERROR =(1<<4);
	public static final int VOMPEVENT_AUDIOSTREAMING =(1<<5);
	public static final int VOMPEVENT_DIAL =(1<<6);
	public static final int VOMPEVENT_REGISTERINTEREST =(1<<7);
	public static final int VOMPEVENT_WITHDRAWINTEREST =(1<<8);
	public static final int VOMPEVENT_CALLCREATED =(1<<9);
	public static final int VOMPEVENT_PICKUP =(1<<10);
	public static final int VOMPEVENT_CALLINFO =(1<<11);
	public static final int VOMPEVENT_AUDIOPACKET =(1<<12);
	int flags;
	short audio_sample_bytes;
	char audio_sample_codec;
	char local_state;
	char remote_state;
	  /* list of codecs the registering party is willing to support
	       (for VOMPEVENT_REGISTERINTEREST) */
	byte[] supported_codecs = new byte[257];

	byte[] local_did = new byte[64];
	byte[] remote_did = new byte[64];
	SubscriberId local_sid;
	SubscriberId remote_sid;
	public static final int VOMP_MAX_CALLS = 16;

	int[] other_calls_sessions = new int[VOMP_MAX_CALLS];
	byte[] other_calls_states = new byte[VOMP_MAX_CALLS];

	public static final int MAX_AUDIO_BYTES =1024;
	byte[] audio_bytes = new byte[MAX_AUDIO_BYTES];

	public MDPVoMPEvent() {
		this.packetType = MDP_VOMPEVENT;
	}

	public MDPVoMPEvent(byte[] m) {
		this();
	}

	@Override
	public byte[] toByteArray() {
		// Pack everything into raw[], and then use super to flatten that.
		putInt(this.raw, 0, call_session_token);
		return super.toByteArray();
	}
}
