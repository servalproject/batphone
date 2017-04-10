package org.servalproject.audio;

import java.io.IOException;
import java.util.LinkedList;
import java.util.ListIterator;

import org.servalproject.batphone.VoMP;

import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

public class JitterStream extends AudioStream implements Runnable {
	static final String TAG = "AudioPlayer";

	static final int MIN_BUFFER = 5000000;
	static final int MAX_JITTER = 1500;
	boolean playing = false;

	private AudioStream output;
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

	public JitterStream(AudioStream output) {
		this.output = output;
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
						// add before this item
						i.previous();
						queueCount++;
						i.add(buff);
						return 0;
					case 0:
						Log.v(TAG, "Dropping duplicate audio");
						buff.release();
						return 0;
					}
				}
				// should be unreachable?
				Log.v(TAG, "???");
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

	private synchronized void cleanup() {
		if (output == null || playbackThread != null)
			return;

		try {
			output.close();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		playList.clear();
		output = null;
	}

	@Override
	public void run() {

		lastSample = -1;
		lastSampleEnd = -1;
		StringBuilder sb = new StringBuilder();

		Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

		long playbackDelay = 0;
		while (playing) {
			try {
				if (sb.length() >= 128) {
					Log.v(TAG,
							"last; " + lastSampleEnd +
									", upl; "
									+ playbackLatency
									+ ", jitter; "
									+ recommendedJitterDelay
									+ ", actual; "
									+ playbackDelay
									+ ", len; " + queueCount
									+ ", " + sb.toString());
					sb.setLength(0);
				}

				AudioBuffer buff = null;
				long now = 0;
				int generateSilence = 0;
				boolean missing = true;
				long audioRunsOutAt;

				synchronized (playList) {

					now = System.nanoTime();
					playbackLatency = this.output.getBufferDuration();
					// work out when we must make a decision about playing some
					// extra silence
					audioRunsOutAt = now
							- MIN_BUFFER
							+ playbackLatency * 1000;

					if (!playList.isEmpty())
						buff = playList.getFirst();

					if (buff != null) {
						int silenceGap = buff.sampleStart - lastSampleEnd;
						playbackDelay = SystemClock.elapsedRealtime()
								- buff.received + buff.thisDelay;

						int jitterAdjustment = (int) (recommendedJitterDelay - playbackDelay);

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
							int duration = output.sampleDurationMs(buff);
							lastSampleEnd = lastSample + duration;
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
							missing = false;
						}

					}
				}

				// Now that we've worked out what to do, we can block this
				// thread
				// (outside of the above synchronized code block)

				if (generateSilence > 0) {
					this.output.missed(generateSilence, missing);
					continue;
				}

				if (buff != null) {
					// write the audio sample, then check the packet queue again
					lastSample = buff.sampleStart;
					lastSampleEnd = lastSample + output.write(buff);
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
}
