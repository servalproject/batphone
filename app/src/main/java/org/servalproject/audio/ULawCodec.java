package org.servalproject.audio;

import org.servalproject.batphone.VoMP;

import uk.co.mmscomputing.sound.ALawCompressor;
import uk.co.mmscomputing.sound.ALawDecompressor;
import uk.co.mmscomputing.sound.Compressor;
import uk.co.mmscomputing.sound.Decompressor;
import uk.co.mmscomputing.sound.ULawDecompressor;
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

	static private Compressor alawCompressor = new ALawCompressor();
	static private Compressor ulawCompressor = new uLawCompressor();
	static private Decompressor alawDecompressor = new ALawDecompressor();
	static private Decompressor ulawDecompressor = new ULawDecompressor();
	private Compressor compressor;
	private Decompressor decompressor;
	private BufferList compressBuffers;
	private BufferList decompressBuffers;
	private final VoMP.Codec codec;

	public ULawCodec(boolean useALaw) {
		compressor = (useALaw) ? alawCompressor : ulawCompressor;
		decompressor = (useALaw) ? alawDecompressor : ulawDecompressor;
		codec = (useALaw) ? VoMP.Codec.Alaw8 : VoMP.Codec.Ulaw8;
		compressBuffers = new BufferList(codec.maxBufferSize() / 2);
		decompressBuffers = new BufferList();
	}

	@Override
	public void close() {
		compressBuffers = null;
		decompressBuffers = null;
		compressor = null;
		decompressor = null;
	}

	@Override
	public AudioBuffer encode(AudioBuffer source) {
		AudioBuffer output = compressBuffers.getBuffer();
		output.copyFrom(source);
		output.codec = this.codec;
		compressor.compress(source.buff, 0, source.dataLen, output.buff, 0);
		output.dataLen = source.dataLen / 2;
		return output;
	}

	@Override
	public AudioBuffer decode(AudioBuffer source) {
		AudioBuffer output = decompressBuffers.getBuffer();
		output.copyFrom(source);
		output.codec = VoMP.Codec.Signed16;
		decompressor.decompress(source.buff, 0, source.dataLen, output.buff, 0);
		output.dataLen = source.dataLen * 2;
		return output;
	}

	@Override
	public int sampleLength(AudioBuffer buff) {
		return buff.dataLen;
	}
}