package org.servalproject.audio;

import java.io.IOException;

import org.servalproject.batphone.VoMP;

import uk.co.mmscomputing.sound.ALawDecompressor;
import uk.co.mmscomputing.sound.Decompressor;
import uk.co.mmscomputing.sound.ULawDecompressor;

public class DecompressULawStream implements AudioStream {
	private final AudioStream out;
	private final Decompressor decompressor;
	private BufferList bufferList;

	public DecompressULawStream(AudioStream out, boolean useALaw, int mtu) {
		this.out = out;
		this.decompressor = (useALaw) ? new ALawDecompressor()
				: new ULawDecompressor();
		this.bufferList = new BufferList(mtu);
	}

	@Override
	public void close() throws IOException {
		out.close();
	}

	@Override
	public int write(AudioBuffer buff) throws IOException {
		AudioBuffer output = bufferList.getBuffer();
		output.copyFrom(buff);
		output.codec = VoMP.Codec.Signed16;
		decompressor.decompress(buff.buff, 0, buff.dataLen, output.buff, 0);
		output.dataLen = buff.dataLen * 2;
		int ret = out.write(output);
		buff.release();
		return ret;
	}

	@Override
	public int sampleDurationFrames(AudioBuffer buff) {
		return buff.dataLen;
	}
}
