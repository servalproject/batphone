package org.servalproject.audio;

import org.servalproject.batphone.VoMP;

import uk.co.mmscomputing.sound.ALawCompressor;
import uk.co.mmscomputing.sound.Compressor;
import uk.co.mmscomputing.sound.uLawCompressor;

public class ULawCodec extends Codec {

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
	private Compressor compressor;
	private BufferList bufferList;
	private final VoMP.Codec codec;

	public ULawCodec(boolean useALaw) {
		compressor = (useALaw) ? alawcompressor : ulawcompressor;
		codec = (useALaw) ? VoMP.Codec.Alaw8 : VoMP.Codec.Ulaw8;
		bufferList = new BufferList(codec.audioBufferSize() / 2);
	}

	@Override
	public void close() {
		bufferList = null;
		compressor = null;
	}


	@Override
	public void open() {
	}

	@Override
	public AudioBuffer encode(AudioBuffer source) {
		AudioBuffer output = bufferList.getBuffer();
		output.copyFrom(source);
		output.codec = this.codec;
		compressor.compress(source.buff, 0, source.dataLen, output.buff, 0);
		output.dataLen = source.dataLen / 2;
		return output;
	}

	@Override
	public AudioBuffer decode(AudioBuffer source) {
		AudioBuffer output = bufferList.getBuffer();
		output.copyFrom(source);
		output.codec = VoMP.Codec.Signed16;
		compressor.compress(source.buff, 0, source.dataLen, output.buff, 0);
		output.dataLen = source.dataLen * 2;
		return output;
	}
}