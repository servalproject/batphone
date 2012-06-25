package org.servalproject.batphone;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Stack;

import uk.co.mmscomputing.sound.DecompressOutputStream;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Process;
import android.util.Log;

public class AudioPlayer implements Runnable {
	static final String TAG = "AudioPlayer";

	static final int MIN_BUFFER = 20000000;
	static final int SAMPLE_RATE = 8000;
	static final int MIN_QUEUE_LEN = 160;

	boolean playing = false;

	private final Context context;

	private AudioManager am;
	private AudioOutputStream audioOutput;
	private OutputStream codecOutput;
	private VoMP.Codec codec;

	private int oldAudioMode;
	private int playbackLatency;
	private int lastSampleEnd;
	Thread playbackThread;

	// Add packets (primarily) to the the start of the list, play them from the
	// end
	// assuming that packet re-ordering is rare we shouldn't have to traverse
	// the list very much to add a packet.
	LinkedList<AudioBuffer> playList = new LinkedList<AudioBuffer>();
	Stack<AudioBuffer> reuseList = new Stack<AudioBuffer>();

	int lastQueuedSampleEnd = 0;

	class AudioBuffer implements Comparable<AudioBuffer> {
		final byte buff[];
		int dataLen;
		int sampleStart;
		int sampleEnd;

		public AudioBuffer(int buffSize) {
			this.buff = new byte[buffSize];
		}

		@Override
		public int compareTo(AudioBuffer arg0) {
			if (0 < arg0.sampleStart - this.sampleStart)
				return -1;
			else if (this.sampleStart == arg0.sampleStart)
				return 0;
			return 1;
		}
	}

	public AudioPlayer(Context context) {
		this.context = context;
	}

	public int receivedAudio(int local_session, int start_time,
			int end_time, VoMP.Codec codec, InputStream in, int byteCount)
			throws IOException {

		int ret = 0;

		if (!playing) {
			// Log.v(TAG,
			// "Dropping audio as we are not currently playing");
			return 0;
		}

		if (this.codec == null) {
			// TODO move into run method and only choose a codec on playback
			switch (codec) {
			case Pcm:
				this.codecOutput = this.audioOutput;
				break;
			case Alaw8:
				this.codecOutput = new DecompressOutputStream(this.audioOutput,
						true);
				break;
			case Ulaw8:
				this.codecOutput = new DecompressOutputStream(this.audioOutput,
						false);
				break;
			default:
				// ignore unsupported codecs
				return 0;
			}
			this.codec = codec;

		} else if (codec != this.codec)
			throw new IOException("Changing codecs mid call is not supported");

		if (end_time == lastQueuedSampleEnd || end_time <= lastSampleEnd) {
			// Log.v(TAG, "Ignoring buffer");
			return 0;
		}

		if (byteCount > this.codec.blockSize)
			throw new IOException("Incoming buffer is too long for codec");

		AudioBuffer buff;
		if (reuseList.size() > 0)
			buff = reuseList.pop();
		else
			buff = new AudioBuffer(this.codec.blockSize);

		{
			int read = 0;
			while (read < byteCount) {
				int actualRead = in.read(buff.buff, read, byteCount - read);
				if (ret < 0)
					throw new EOFException();
				read += actualRead;
			}
		}

		ret = byteCount;
		buff.dataLen = byteCount;
		buff.sampleStart = start_time;
		buff.sampleEnd = end_time;

		synchronized (playList) {
			if (playList.isEmpty()
					|| buff.compareTo(playList.getFirst()) < 0) {

				// add this buffer to play *now*
				if (playList.isEmpty())
					lastQueuedSampleEnd = end_time;

				playList.addFirst(buff);
				if (playbackThread != null)
					playbackThread.interrupt();
			} else if (buff.compareTo(playList.getLast()) > 0) {
				// yay, packets arrived in order
				lastQueuedSampleEnd = end_time;
				playList.addLast(buff);
			} else {
				// find where to insert this item
				ListIterator<AudioBuffer> i = playList.listIterator();
				while (i.hasNext()) {
					AudioBuffer compare = i.next();
					switch (buff.compareTo(compare)) {
					case -1:
						i.previous();
						i.add(buff);
						return ret;
					case 0:
						reuseList.push(buff);
						return ret;
					}
				}
				reuseList.push(buff);
			}
		}
		return ret;
	}

	public synchronized void startPlaying() {
		if (playbackThread == null) {
			playbackThread = new Thread(this, "Playback");
			playing = true;
			playbackThread.start();
		}
	}

	public synchronized void stopPlaying() {
		playing = false;
		if (playbackThread != null)
			playbackThread.interrupt();

	}

	public synchronized void prepareAudio() throws IOException {
		if (audioOutput != null)
			return;

		audioOutput = new AudioOutputStream(
				AudioManager.STREAM_VOICE_CALL,
				SAMPLE_RATE,
				AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_16BIT,
				8 * 60 * 2);

		am = (AudioManager) context
				.getSystemService(Context.AUDIO_SERVICE);
	}

	public synchronized void cleanup() {
		if (audioOutput == null)
			return;

		try {
			audioOutput.close();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		audioOutput = null;
		codecOutput = null;
		am = null;
	}

	@Override
	public void run() {
		try {
			prepareAudio();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}

		oldAudioMode = am.getMode();
		am.setMode(AudioManager.MODE_IN_CALL);
		am.setSpeakerphoneOn(false);
		audioOutput.play();
		try {
			audioOutput.fillSilence();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}

		lastSampleEnd = 0;
		StringBuilder sb = new StringBuilder();

		int smallestQueue = 0;
		int largestQueue = 0;

		// wait for an initial buffer of audio before playback starts
		int waitForBuffer = 300;

		Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

		while (playing) {
			try {
				if (sb.length() >= 128) {
					Log.v(TAG,
							smallestQueue + " " + largestQueue + " "
									+ sb.toString());
					sb.setLength(0);
				}

				AudioBuffer buff = null;
				long now = 0;
				int generateSilence = 0;
				long audioRunsOutAt;

				synchronized (playList) {

					now = System.nanoTime();
					playbackLatency = this.audioOutput.unplayedFrameCount();

					// work out when we must make a decision about playing some
					// extra silence
					audioRunsOutAt = now
							- MIN_BUFFER
							+ (long) (playbackLatency * 1000000.0 / SAMPLE_RATE);

					// calculate an absolute maximum delay based on our maximum
					// extra latency
					int queuedLengthInMs = lastQueuedSampleEnd - lastSampleEnd;
					if (queuedLengthInMs < smallestQueue)
						smallestQueue = queuedLengthInMs;

					if (queuedLengthInMs > largestQueue)
						largestQueue = queuedLengthInMs;

					if (queuedLengthInMs < waitForBuffer) {
						// After a buffer underflow, wait until we have some
						// more
						// buffer before restarting playback
					} else {
						waitForBuffer = -1;
						if (!playList.isEmpty())
							buff = playList.getFirst();
					}

					if (buff != null) {
						int silenceGap = buff.sampleStart - (lastSampleEnd + 1);

						if (silenceGap > 0) {

							// try to wait until the last possible moment before
							// giving up and playing the buffer we have
							if (audioRunsOutAt <= now) {
								sb.append("M");
								generateSilence = silenceGap;
								lastSampleEnd = buff.sampleStart - 1;
							}
							buff = null;
						} else {
							// we either need to play it or skip it, so remove
							// it
							// from the queue
							playList.removeFirst();

							if (silenceGap < 0) {
								// sample arrived too late, we might get better
								// audio if we add a little extra latency
								reuseList.push(buff);
								sb.append("L");
								continue;
							}

							if (smallestQueue > MIN_QUEUE_LEN) {
								// if we don't need the buffer, drop some audio
								// but count it as played so we
								// don't immediately play silence or try to wait
								// for
								// this "missing" audio packet to arrive

								// TODO shrink each buffer instead of dropping a
								// whole one?

								sb.append("F");
								smallestQueue -= (buff.sampleEnd + 1 - buff.sampleStart);
								lastSampleEnd = buff.sampleEnd;
								reuseList.push(buff);
								continue;
							}
						}
					} else {
						// this thread can sleep for a while to wait for more
						// audio

						// But if we've got nothing else to play, we should play
						// some silence to increase our latency buffer
						if (audioRunsOutAt <= now) {

							// write silence until we have 60ms of audio
							// buffered?
							waitForBuffer = 60;
							sb.append("X");
							generateSilence = 20;
						}

					}
				}

				if (generateSilence > 0) {
					// write some audio silence, then check the packet queue
					// again
					// (8 samples per millisecond)
					this.audioOutput.writeSilence(generateSilence * 8);
					smallestQueue += 2;
					largestQueue -= 5;
					sb.append("{" + generateSilence + "}");
					continue;
				}

				if (buff != null) {
					// write the audio sample, then check the packet queue again
					lastSampleEnd = buff.sampleEnd;
					this.codecOutput.write(buff.buff, 0, buff.dataLen);
					smallestQueue += 2;
					largestQueue -= 5;
					sb.append(".");
					synchronized (playList) {
						reuseList.push(buff);
					}
					continue;
				}

				// check the clock again, then wait only until our audio buffer
				// is
				// getting close to empty
				now = System.nanoTime();
				long waitFor = audioRunsOutAt - now;
				if (waitFor <= 0)
					continue;
				sb.append(" ");
				long waitMs = waitFor / 1000000;
				int waitNs = (int) (waitFor - waitMs * 1000000);

				try {
					Thread.sleep(waitMs, waitNs);
				} catch (InterruptedException e) {
				}
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
		am.setMode(oldAudioMode);
		cleanup();
		playList.clear();
		reuseList.clear();
		playbackThread = null;
	}

}
