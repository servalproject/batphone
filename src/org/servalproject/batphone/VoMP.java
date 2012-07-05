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

	public enum Codec {
		None(0x00, 0, 1),

		/*
		 * actually 2550bps, 51 bits per 20ms, but using whole byte here, so
		 * 2800bps
		 */
		Codec2_2400(0x01, 7, 20),

		/* 7 bytes per 40ms */
		Codec2_1400(0x02, 7, 40),

		/* check. 5.6kbits */
		GsmHalf(0x03, 14, 20),

		/* padded to 13.2kbit/sec */
		GsmFull(0x04, 33, 20),

		/* 8000x2bytes*0.02sec */
		Signed16(0x05, 320, 20),
		Ulaw8(0x06, 160, 20),
		Alaw8(0x07, 160, 20),
		Pcm(0x08, 320, 20),
		Dtmf(0x80, 1, 80),
		Engaged(0x81, 0, 20),
		OnHold(0x82, 0, 20),
		CallerId(0x83, 32, 0),
		CodecsISupport(0xfe, 0, 0),
		ChangeYourCodecTo(0xff, 0, 0);

		public final int code;
		public final String codeString;
		public final int blockSize;
		public final int timespan;

		Codec(int code, int blockSize, int timespan) {
			this.code = code;
			this.codeString = Integer.toString(code);
			this.blockSize = blockSize;
			this.timespan = timespan;
		}

		public static Codec getCodec(int code) {
			switch (code) {
			case 0:
			default:
				return None;
			case 0x01:
				return Codec2_2400;
			case 0x02:
				return Codec2_1400;
			case 0x03:
				return GsmHalf;
			case 0x04:
				return GsmFull;
			case 0x05:
				return Signed16;
			case 0x06:
				return Ulaw8;
			case 0x07:
				return Alaw8;
			case 0x08:
				return Pcm;
			case 0x80:
				return Dtmf;
			case 0x81:
				return Engaged;
			case 0x82:
				return OnHold;
			case 0x83:
				return CallerId;
			case 0xfe:
				return CodecsISupport;
			case 0xff:
				return ChangeYourCodecTo;
			}

		}
	}
}
