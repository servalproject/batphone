package org.servalproject.audio;

import java.io.IOException;
import java.util.LinkedList;
import java.util.ListIterator;

import org.servalproject.batphone.VoMP;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

public class JitterStream implements Runnable, AudioStream {
	static final String TAG = "AudioPlayer";

	static final int MIN_BUFFER = 5000000;
	static final int SAMPLE_RATE = 8000;
	static final int MAX_JITTER = 1500;
	boolean playing = false;

	private final Context context;

	private AudioManager am;
	private AudioPlaybackStream audioOutput;
	private AudioStream codecOutput;
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

	int lastQueuedSample = -1;

	public JitterStream(Context context) {
		this.context = context;
	}

	public void setJitterDelay(int jitterDelay) {
		recommendedJitterDelay = jitterDelay;
	}

	@Override
	public int write(AudioBuffer buff) throws IOException {
		if (buff.sampleStart == lastQueuedSample
				|| buff.sampleStart <= lastSample
				|| !playing) {
			buff.release();
			return 0;
		}

		synchronized (playList) {
			if (playList.isEmpty()
					|| buff.compareTo(playList.getFirst()) < 0) {

				// add this buffer to play *now*
				if (playList.isEmpty())
					lastQueuedSample = buff.sampleStart;

				playList.addFirst(buff);
				queueCount++;
				if (playbackThread != null)
					playbackThread.interrupt();
			} else if (buff.compareTo(playList.getLast()) > 0) {
				// yay, packets arrived in order
				lastQueuedSample = buff.sampleStart;
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
						return 0;
					case 0:
						buff.release();
						return 0;
					}
				}
				buff.release();
			}
		}
		return 0;
	}

	public synchronized void startPlaying() {
		if (playbackThread == null) {
			playbackThread = new Thread(this, "Playback");
			playing = true;
			playbackThread.start();
		}
	}

	public synchronized void prepareAudio() throws IOException {
		if (audioOutput != null)
			return;

		audioOutput = new AudioPlaybackStream(
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

					if (buff != null && buff.codec != this.codec) {
						// TODO move into run method and only choose a codec on
						// playback
						Log.v(TAG, "Codec changed to " + buff.codec);
						switch (buff.codec) {
						case Signed16:
							this.codecOutput = this.audioOutput;
							this.codec = buff.codec;
							break;
						case Alaw8:
							this.codecOutput = new DecompressULawStream(
									this.audioOutput,
									true, 360);
							this.codec = buff.codec;
							break;
						case Ulaw8:
							this.codecOutput = new DecompressULawStream(
									this.audioOutput,
									false, 360);
							this.codec = buff.codec;
							break;
						default:
							// ignore unsupported codecs
							buff.release();
							buff = null;
							break;
						}
					}

					if (buff != null) {
						int silenceGap = buff.sampleStart - lastSampleEnd;
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

						if (silenceGap < 0) {
							// sample arrived too late, we might get better
							// audio if we add a little extra latency
							playList.removeFirst();
							queueCount--;
							buff.release();
							sb.append("L");
							continue;
						}

						// TODO, don't throw away audio if nothing else we
						// have is currently good enough.
						if (jitterAdjustment < -40
								&& lastQueuedSample - buff.sampleStart - silenceGap >= 120) {
							// if our buffer is too big, drop some audio
							// but count it as played so we
							// don't immediately play silence or try to wait
							// for this "missing" audio packet to arrive

							lastSample = buff.sampleStart - silenceGap;
							int duration = codecOutput
									.sampleDurationFrames(buff)
									/ audioOutput.samplesPerMs;
							lastSampleEnd = lastSample + duration - silenceGap;
							if (silenceGap == 0) {
								playList.removeFirst();
								queueCount--;
								sb.append("F");
								buff.release();
								buff = null;
							} else {
								sb.append("D");
							}
							continue;
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
								lastSample = lastSampleEnd;
								lastSampleEnd += generateSilence;
							}
							buff = null;
						} else {
							// Lets play this buffer.
							playList.removeFirst();
							queueCount--;
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
					this.audioOutput.writeSilence(generateSilence);
					continue;
				}

				if (buff != null) {
					// write the audio sample, then check the packet queue again
					lastSample = buff.sampleStart;
					lastSampleEnd = lastSample + codecOutput.write(buff);
					sb.append(".");
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

	@Override
	public void close() throws IOException {
		playing = false;
		if (playbackThread != null)
			playbackThread.interrupt();
	}

	@Override
	public int sampleDurationFrames(AudioBuffer buff) {
		return 0;
	}

}
