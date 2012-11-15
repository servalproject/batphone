package org.servalproject.batphone;

import org.servalproject.R;

public class VoMP {
	/*
	 * Note that usage of this enum is deprecated, there are now enough other
	 * monitor commands to handle each call state change that we don't need to
	 * track the local or remote states ourselves, we can let servald do that
	 * work for us.
	 */
	@Deprecated
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
		Signed16(0x01, 1),
		Ulaw8(0x02, 2),
		Alaw8(0x03, 2),
		Gsm(0x04), ;

		public final int code;
		// we put this string into audio packets quite a lot, lets only pay the
		// conversion cost once.
		public final String codeString;
		public final int preference;

		Codec(int code, int preference) {
			this.code = code;
			this.codeString = Integer.toString(code);
			this.preference = preference;
		}

		Codec(int code) {
			this(code, 0);
		}

		public static Codec getCodec(int code) {
			switch (code) {
			case 0x01:
				return Signed16;
			case 0x02:
				return Ulaw8;
			case 0x03:
				return Alaw8;
			case 0x04:
				return Gsm;
			default:
				return null;
			}

		}
	}
}
