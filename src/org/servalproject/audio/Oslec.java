package org.servalproject.audio;

import java.io.IOException;

import android.os.SystemClock;
import android.util.Log;

// pass this class blocks of transmitted audio, then when audio is received it will attempt to detect echo's and remove them.
public class Oslec {
	private int echoCanState;
	private int size = 128;
	public static final int BLOCK_SIZE = 128;

	private static final int
			ECHO_CAN_USE_ADAPTION = 0x01,
			ECHO_CAN_USE_NLP = 0x02,
			ECHO_CAN_USE_CNG = 0x04,
			ECHO_CAN_USE_CLIP = 0x08,
			ECHO_CAN_USE_SUPPRESSOR = 0x10,
			ECHO_CAN_USE_TX_HPF = 0x20,
			ECHO_CAN_USE_RX_HPF = 0x40,
			ECHO_CAN_DISABLE = 0x80;

	static {
		System.loadLibrary("oslec");
	}

	private static native int echoCanInit(int len, int adaption_mode);

	private static native int echoCanUpdate(int echoCanState,
			byte tx[], int txOffset,
			byte rx[], int rxOffset,
			int length);

	private static native void echoCanFree(int echoCanState);

	public Oslec() {
		enabled(true);
	}

	public void enabled(boolean enable) {
		boolean enabled = echoCanState != 0;
		if (enabled == enable)
			return;

		if (enable) {
			Log.v("Oslec", "Initialising echo canceller");
			echoCanState = echoCanInit(size,
							ECHO_CAN_USE_ADAPTION |
									ECHO_CAN_USE_RX_HPF |
									ECHO_CAN_USE_NLP |
									ECHO_CAN_DISABLE);
		} else {
			Log.v("Oslec", "Closing echo canceller");
			echoCanFree(echoCanState);
			echoCanState = 0;
			freeList = null;
			txHead = txTail = null;
		}
	}

	public boolean toggle() {
		boolean enabled = echoCanState != 0;
		enabled(!enabled);
		return !enabled;
	}

	private AudioBuffer freeList;
	private AudioBuffer txHead, txTail;

	private class AudioBuffer {
		byte buff[];
		int processed;
		int len;
		long txTime;
		AudioBuffer next;

		AudioBuffer(int size) {
			this.buff = new byte[size];
		}
	}

	// remember blocks of audio that we have transmitted
	public void txAudio(byte buffer[], int offset, int count) {
		while (count > 0 && echoCanState != 0) {
			AudioBuffer test, buff;

			// by not changing the freeList variable in this thread, we don't
			// need to synchronize
			test = freeList;
			if (test == null || test.next == null) {
				buff = new AudioBuffer(count < BLOCK_SIZE ? BLOCK_SIZE : count);
			} else {
				buff = test.next;
				test.next = buff.next;
				buff.next = null;
			}

			buff.txTime = SystemClock.elapsedRealtime();
			buff.len = count > buff.buff.length ? buff.buff.length : count;
			System.arraycopy(buffer, offset, buff.buff, 0, buff.len);
			count -= buff.len;
			offset += buff.len;

			if (txTail == null) {
				txHead = txTail = buff;
			} else {
				txTail = txTail.next = buff;
			}
		}
	}

	// process audio we have received, and attempt to eliminate any echos
	public boolean rxAudio(byte[] buffer, int offset, int count, int lag)
			throws IOException {
		int processed = 0;
		boolean modified = false;
		long now = SystemClock.elapsedRealtime();

		while (processed < count && echoCanState != 0) {
			// never empty the txHead list, that way we never need to
			// synchronize
			while (txHead != null && txHead.next != null
					&& txHead.processed >= txHead.len) {
				AudioBuffer h = txHead;
				h.processed = 0;
				h.len = 0;
				txHead = h.next;
				h.next = freeList;
				freeList = h;
			}

			AudioBuffer audio = txHead;

			while (audio != null) {
				if (audio.processed < audio.len)
					break;

				audio = audio.next;
			}

			// I believe we need to make sure that the timestamp of the audio we
			// transmitted is >= the timestamp of the start of the audio
			// buffer, and <= the size of the echo cancellation window.

			// for the moment, just deal with known lag in the record buffer.
			if (audio != null && audio.txTime > now - lag) {
				audio = null;
			}

			int len = count - processed;
			byte txBuff[] = null;
			int txOffset = 0;

			if (audio != null) {
				if (audio.len -audio.processed < len)
					len = audio.len -audio.processed;
				txBuff = audio.buff;
				txOffset = audio.processed;
			}

			int ret = echoCanUpdate(echoCanState,
					txBuff, txOffset,
					buffer, offset + processed,
					len);

			if (ret < 0)
				throw new IOException("Echo cancellation failed");
			if (ret > 0)
				modified = true;

			processed += len;

			if (audio != null) {
				audio.processed+=len;
			}
		}
		return modified;
	}
}
