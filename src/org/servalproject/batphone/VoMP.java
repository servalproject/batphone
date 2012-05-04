package org.servalproject.batphone;

public class VoMP {
	public static final int STATE_NOSUCHCALL = 0;
	public static final int STATE_NOCALL = 1;
	public static final int STATE_CALLPREP = 2;
	public static final int STATE_RINGINGOUT = 3;
	public static final int STATE_RINGINGIN = 4;
	public static final int STATE_INCALL = 5;
	public static final int STATE_CALLENDED = 6;

	public static final int MAX_AUDIO_BYTES = 1024;

	public static String describeState(int state) {
		switch (state) {
		case STATE_NOSUCHCALL:
			return "No such call";
		case STATE_NOCALL:
			return "No call";
		case STATE_CALLPREP:
			return "Call Preparation";
		case STATE_RINGINGOUT:
			return "Ringing remote party";
		case STATE_RINGINGIN:
			return "Remote party is calling";
		case STATE_INCALL:
			return "In call";
		case STATE_CALLENDED:
			return "Call ended";
		default:
			return "Unknown call state #" + state;
		}
	}
}
