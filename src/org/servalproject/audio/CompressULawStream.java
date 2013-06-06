package org.servalproject.audio;

import java.io.IOException;

import org.servalproject.batphone.VoMP;

import uk.co.mmscomputing.sound.ALawCompressor;
import uk.co.mmscomputing.sound.Compressor;
import uk.co.mmscomputing.sound.uLawCompressor;

public class CompressULawStream implements AudioStream {

	/*
	 * Convert mono PCM byte stream into A-Law u-Law byte stream
	 *
	 * static AudioFormat alawformat= new
	 * AudioFormat(AudioFormat.Encoding.ALAW,8000,8,1,1,8000,false); static
	 * AudioFormat ulawformat= new
	 * AudioFormat(AudioFormat.Encoding.ULAW,8000,8,1,1,8000,false);
	 *
	 * PCM 8000.0 Hz, 16 bit, mono, SIGNED, little-endian static AudioFormat
	 * pcmformat = new AudioFormat(8000,16,1,true,false);
	 */

	static private Compressor alawcompressor = new ALawCompressor();
	static private Compressor ulawcompressor = new uLawCompressor();
	private AudioStream out;
	private Compressor compressor;
	private BufferList bufferList;
	private final VoMP.Codec codec;

	public CompressULawStream(AudioStream out, boolean useALaw, int mtu)
			throws IOException {
		this.out = out;
		compressor = (useALaw) ? alawcompressor : ulawcompressor;
		bufferList = new BufferList(mtu / 2);
		codec = (useALaw) ? VoMP.Codec.Alaw8 : VoMP.Codec.Ulaw8;
	}

	@Override
	public void close() throws IOException {
		bufferList = null;
		out = null;
	}

	@Override
	public int write(AudioBuffer buff) throws IOException {
		AudioBuffer output = bufferList.getBuffer();
		output.copyFrom(buff);
		output.codec = this.codec;
		compressor.compress(buff.buff, 0, buff.dataLen, output.buff, 0);
		output.dataLen = buff.dataLen / 2;
		int ret = this.out.write(output);
		buff.release();
		return ret;
	}

	@Override
	public int sampleDurationFrames(AudioBuffer buff) {
		return buff.dataLen;
	}
}