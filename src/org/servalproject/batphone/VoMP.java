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
