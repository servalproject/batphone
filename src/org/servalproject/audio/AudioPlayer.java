package org.servalproject.audio;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Stack;

import org.servalproject.batphone.VoMP;

import uk.co.mmscomputing.sound.CodecOutputStream;
import uk.co.mmscomputing.sound.DecompressOutputStream;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

public class AudioPlayer implements Runnable {
	static final String TAG = "AudioPlayer";

	static final int MIN_BUFFER = 5000000;
	static final int SAMPLE_RATE = 8000;
	static final int MAX_JITTER = 1500;
	boolean playing = false;

	private final Context context;

	private AudioManager am;
	private AudioOutputStream audioOutput;
	public final Oslec echoCanceler;
	private CodecOutputStream codecOutput;
	private VoMP.Codec codec;

	private int playbackLatency;
	private int lastSample = -1;
	private int lastSampleEnd = -1;
	private int recommendedJitterDelay;
	Thread playbackThread;

	// Add packets (primarily) to the the start of the list, play them from the
	// end
	// assuming that packet re-ordering is rare we shouldn't have to traverse
	// the list very much to add a packet.
	LinkedList<AudioBuffer> playList = new LinkedList<AudioBuffer>();
	int queueCount = 0;
	Stack<AudioBuffer> reuseList = new Stack<AudioBuffer>();

	int lastQueuedSample = -1;

	private static final int AUDIO_MTU = 1200;
	class AudioBuffer implements Comparable<AudioBuffer> {
		final byte buff[];
		int dataLen;
		int sampleStart;
		int sampleEnd;
		long received;
		int thisDelay;

		public AudioBuffer() {
			this.buff = new byte[AUDIO_MTU];
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

	public AudioPlayer(Oslec echoCanceler, Context context) {
		this.context = context;
		this.echoCanceler = echoCanceler;
	}

	public int receivedAudio(int local_session, int start_time,
			int jitter_delay, int this_delay, VoMP.Codec codec, InputStream in,
			int byteCount)
			throws IOException {

		int ret = 0;

		if (!playing) {
			// Log.v(TAG,
			// "Dropping audio as we are not currently playing");
			return 0;
		}
		recommendedJitterDelay = jitter_delay;

		if (this.codec == null) {
			// TODO move into run method and only choose a codec on playback
			switch (codec) {
			case Signed16:
				this.codecOutput = this.audioOutput;
				break;
			case Alaw8:
				this.codecOutput = new DecompressOutputStream(
						this.audioOutput,
						true);
				break;
			case Ulaw8:
				this.codecOutput = new DecompressOutputStream(
						this.audioOutput,
						false);
				break;
			default:
				// ignore unsupported codecs
				return 0;
			}
			this.codec = codec;
			Log.v(TAG, "Set codec to " + codec);

			for (int i = 0; i <= 50; i++)
				reuseList.push(new AudioBuffer());

		} else if (codec != this.codec)
			throw new IOException("Changing codecs from " + this.codec + " to "
					+ codec + " mid call is not supported");

		if (start_time == lastQueuedSample || start_time <= lastSample) {
			// Log.v(TAG, "Ignoring buffer");
			return 0;
		}

		if (byteCount > AUDIO_MTU)
			throw new IOException("Incoming buffer is too long");

		AudioBuffer buff;
		if (reuseList.size() > 0)
			buff = reuseList.pop();
		else
			buff = new AudioBuffer();

		{
			int read = 0;
			while (read < byteCount) {
				int actualRead = in.read(buff.buff, read, byteCount - read);
				if (ret < 0)
					throw new EOFException();
				read += actualRead;
			}
		}

		int duration = this.codecOutput.sampleDurationFrames(buff.buff, 0,
				byteCount) / 8;
		ret = byteCount;
		buff.dataLen = byteCount;
		buff.sampleStart = start_time;
		buff.sampleEnd = start_time + duration - 1;
		buff.received = SystemClock.elapsedRealtime();
		buff.thisDelay = this_delay;
		synchronized (playList) {
			if (playList.isEmpty()
					|| buff.compareTo(playList.getFirst()) < 0) {

				// add this buffer to play *now*
				if (playList.isEmpty())
					lastQueuedSample = start_time;

				playList.addFirst(buff);
				queueCount++;
				if (playbackThread != null)
					playbackThread.interrupt();
			} else if (buff.compareTo(playList.getLast()) > 0) {
				// yay, packets arrived in order
				lastQueuedSample = start_time;
				queueCount++;
				playList.addLast(buff);
			} else {
				// find where to insert this item
				ListIterator<AudioBuffer> i = playList.listIterator();
				while (i.hasNext()) {
					AudioBuffer compare = i.next();
					switch (buff.compareTo(compare)) {
					case -1:
						i.previous();
						queueCount++;
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
				this.echoCanceler,
				AudioManager.STREAM_VOICE_CALL,
				SAMPLE_RATE,
				AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_16BIT,
				8 * 60 * 2);
		// NULL???
		am = (AudioManager) context
				.getSystemService(Context.AUDIO_SERVICE);

		codecOutput = audioOutput;
	}

	public synchronized void cleanup() {
		if (audioOutput == null || playbackThread != null)
			return;

		try {
			codecOutput.close();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		playList.clear();
		reuseList.clear();
		if (echoCanceler != null)
			echoCanceler.enabled(false);
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

		am.setSpeakerphoneOn(false);
		audioOutput.play();

		lastSample = -1;
		lastSampleEnd = -1;
		StringBuilder sb = new StringBuilder();

		Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

		while (playing) {
			try {
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

					if (!playList.isEmpty())
						buff = playList.getFirst();

					if (buff != null) {
						int silenceGap = buff.sampleStart - (lastSampleEnd + 1);
						long playbackDelay = SystemClock.elapsedRealtime()
								- buff.received + buff.thisDelay;

						int jitterAdjustment = (int) (recommendedJitterDelay - playbackDelay);

						if (sb.length() >= 128) {
							Log.v(TAG,
									"wr; "
											+ this.audioOutput.writtenAudio()
											+ ", upl; "
											+ this.audioOutput
													.unplayedFrameCount()
											+ ", jitter; "
											+ recommendedJitterDelay
											+ ", actual; " + playbackDelay
											+ ", len; " + queueCount
											+ ", " + sb.toString());
							sb.setLength(0);
						}

						if (silenceGap > 0 && lastSampleEnd != -1) {
							// try to wait until the last possible moment before
							// giving up and playing the next buffer we have
							if (audioRunsOutAt <= now) {
								sb.append("M");
								generateSilence = silenceGap;
								if (generateSilence > 20)
									generateSilence = 20;
								// pretend we really did play the missing
								// audio once we've waited long enough.
								lastSample = lastSampleEnd + 1;
								lastSampleEnd += generateSilence;
							}
							buff = null;
						} else {
							// we either need to play it or skip it, so remove
							// it
							// from the queue
							playList.removeFirst();
							queueCount--;

							if (silenceGap < 0) {
								// sample arrived too late, we might get better
								// audio if we add a little extra latency
								reuseList.push(buff);
								sb.append("L");
								continue;
							}

							// TODO, don't throw away audio if nothing else we
							// have is currently good enough.
							if (jitterAdjustment < -40
									&& lastQueuedSample - buff.sampleStart >= 120) {
								// if our buffer is too big, drop some audio
								// but count it as played so we
								// don't immediately play silence or try to wait
								// for this "missing" audio packet to arrive

								sb.append("F");
								lastSample = buff.sampleStart;
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
					continue;
				}

				if (buff != null) {
					// write the audio sample, then check the packet queue again
					lastSample = buff.sampleStart;
					lastSampleEnd = buff.sampleEnd;
					this.codecOutput.write(buff.buff, 0, buff.dataLen);
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
		playbackThread = null;
		cleanup();
		if (sb.length() > 0)
			Log.v(TAG, sb.toString());

	}

}
