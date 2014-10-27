package org.servalproject.batphone;

public class VoMP {
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
