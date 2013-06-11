/**
 *
 */
package org.servalproject.audio;

import java.io.IOException;

import org.servalproject.batphone.VoMP;

import android.util.Log;

public class TranscodeStream extends AudioStream {
	private final AudioStream out;
	private final boolean encode;
	VoMP.Codec codec;
	Codec encoder;

	public static TranscodeStream getEncoder(AudioStream out, VoMP.Codec codec) {
		return new TranscodeStream(out, codec);
	}

	public static TranscodeStream getDecoder(AudioStream out) {
		return new TranscodeStream(out, null);
	}

	private TranscodeStream(AudioStream out, VoMP.Codec codec) {
		this.out = out;
		encode = (codec != null);
		if (!encode)
			createCodec(codec);
	}

	@Override
	public void close() throws IOException {
		if (out != null)
			out.close();

		if (encoder != null)
			encoder.close();
	}

	private void createCodec(VoMP.Codec codec) {
		switch (codec) {
		case Signed16:
			this.encoder = null;
			break;
		case Ulaw8:
			this.encoder = new ULawCodec(false);
			break;
		case Alaw8:
			this.encoder = new ULawCodec(true);
			break;
		default:
			throw new IllegalStateException("Unsupported codec " + codec);
		}
		this.codec = codec;
	}

	@Override
	public int write(AudioBuffer buff) throws IOException {
		AudioBuffer output;
		if (encode) {
			if (encoder == null)
				output = buff;
			else {
				output = encoder.encode(buff);
				buff.release();
			}
		} else {
			if (buff.codec != this.codec) {
				if (encoder != null)
					encoder.close();
				createCodec(buff.codec);
				Log.v("Transcoder", "Codec changed to " + buff.codec);
			}
			if (encoder == null)
				output = buff;
			else {
				output = encoder.decode(buff);
				buff.release();
			}
		}
		return out.write(output);
	}

}
