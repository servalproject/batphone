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
		Signed16(0x01, 1, 8000, 20),
		Ulaw8(0x02, 2, 8000, 20),
		Alaw8(0x03, 2, 8000, 20),
		Gsm(0x04, 0, 8000, 20),
		Codec2_1200(0x05, 0, 8000, 40),
		Codec2_3200(0x06, 0, 8000, 20),
		Opus(0x07, 3, 8000, 20) {
			@Override
			public int maxBufferSize() {
				return 2 * 60 * (sampleRate / 1000);
			}
		},
		;

		public final int code;
		// we put this string into audio packets quite a lot, lets only pay the
		// conversion cost once.
		public final String codeString;
		public final int preference;
		public final int sampleRate;
		public final int sampleDuration;

		Codec(int code, int preference, int sampleRate, int sampleDuration) {
			this.code = code;
			this.codeString = Integer.toString(code);
			this.preference = preference;
			this.sampleRate = sampleRate;
			this.sampleDuration = sampleDuration;
		}

		public int audioBufferSize() {
			return 2 * sampleDuration * (sampleRate / 1000);
		}

		static final int MAX_DURATION = 120;

		public int maxBufferSize() {
			return 2 * MAX_DURATION * (sampleRate / 1000);
		}

		public boolean isSupported() {
			return preference > 0;
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
			case 0x05:
				return Codec2_1200;
			case 0x06:
				return Codec2_3200;
			case 0x07:
				return Opus;
			default:
				return null;
			}
		}

	}
}
