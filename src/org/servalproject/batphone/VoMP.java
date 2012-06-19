package org.servalproject.batphone;

import org.servalproject.R;

public class VoMP {
	public enum State {
		NoSuchCall(R.string.outgoing_call, 0),
		NoCall(R.string.outgoing_call, 1),
		CallPrep(R.string.outgoing_call, 2),
		RingingOut(R.string.outgoing_call, 3),
		RingingIn(R.string.incoming_call, 4),
		InCall(R.string.in_call, 5),
		CallEnded(R.string.call_ended, 6),
		Error(R.string.call_ended, 99);

		public final int code;
		public final int displayResource;

		State(int displayResource, int code) {
			this.displayResource = displayResource;
			this.code = code;
		}

		public static State getState(int value) {
			switch (value) {
			case 0:
				return NoSuchCall;
			case 1:
				return NoCall;
			case 2:
				return CallPrep;
			case 3:
				return RingingOut;
			case 4:
				return RingingIn;
			case 5:
				return InCall;
			case 6:
				return CallEnded;
			default:
			case 99:
				return Error;
			}
		}
	}

	public static final int MAX_AUDIO_BYTES = 1024;

	public static final int VOMP_CODEC_NONE = 0x00;
	public static final int VOMP_CODEC_CODEC2_2400 = 0x01;
	public static final int VOMP_CODEC_CODEC2_1400 = 0x02;
	public static final int VOMP_CODEC_GSMHALF = 0x03;
	public static final int VOMP_CODEC_GSMFULL = 0x04;
	public static final int VOMP_CODEC_16SIGNED = 0x05;
	public static final int VOMP_CODEC_8ULAW = 0x06;
	public static final int VOMP_CODEC_8ALAW = 0x07;
	public static final int VOMP_CODEC_PCM = 0x08;
	public static final int VOMP_CODEC_DTMF = 0x80;
	public static final int VOMP_CODEC_ENGAGED = 0x81;
	public static final int VOMP_CODEC_ONHOLD = 0x82;
	public static final int VOMP_CODEC_CALLERID = 0x83;
	public static final int VOMP_CODEC_CODECSISUPPORT = 0xfe;
	public static final int VOMP_CODEC_CHANGEYOURCODECTO = 0xff;

	public static int vompCodecBlockSize(int c)
	{
		switch (c) {
		case VOMP_CODEC_NONE:
			return 0;
		case VOMP_CODEC_CODEC2_2400:
			return 7; /*
					 * actually 2550bps, 51 bits per 20ms, but using whole byte
					 * here, so 2800bps
					 */
		case VOMP_CODEC_CODEC2_1400:
			return 7; /* per 40ms */
		case VOMP_CODEC_GSMHALF:
			return 14; /* check. 5.6kbits */
		case VOMP_CODEC_GSMFULL:
			return 33; /* padded to 13.2kbit/sec */
		case VOMP_CODEC_16SIGNED:
			return 320; /* 8000x2bytes*0.02sec */
		case VOMP_CODEC_8ULAW:
			return 160;
		case VOMP_CODEC_8ALAW:
			return 160;
		case VOMP_CODEC_PCM:
			return 320;
		case VOMP_CODEC_DTMF:
			return 1;
		case VOMP_CODEC_ENGAGED:
			return 0;
		case VOMP_CODEC_ONHOLD:
			return 0;
		case VOMP_CODEC_CALLERID:
			return 32;
		}
		return -1;
	}

	public static int vompCodecTimespan(int c)
	{
		switch (c) {
		case VOMP_CODEC_NONE:
			return 1;
		case VOMP_CODEC_CODEC2_2400:
			return 20;
		case VOMP_CODEC_CODEC2_1400:
			return 40;
		case VOMP_CODEC_GSMHALF:
			return 20;
		case VOMP_CODEC_GSMFULL:
			return 20;
		case VOMP_CODEC_16SIGNED:
			return 20;
		case VOMP_CODEC_8ULAW:
			return 20;
		case VOMP_CODEC_8ALAW:
			return 20;
		case VOMP_CODEC_PCM:
			return 20;
		case VOMP_CODEC_DTMF:
			return 80;
		case VOMP_CODEC_ENGAGED:
			return 20;
		case VOMP_CODEC_ONHOLD:
			return 20;
		case VOMP_CODEC_CALLERID:
			return 0;
		}
		return -1;
	}
}
