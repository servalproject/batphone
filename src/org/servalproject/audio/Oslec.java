package org.servalproject.audio;

import java.io.IOException;

import android.util.Log;

public class Oslec {
	private int echoCanState;

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
			byte playback[], int playOffset,
			byte record[], int recordOffset,
			byte dest[], int destOffset,
			int length);

	private static native void echoCanFree(int echoCanState);

	public Oslec() {
		Log.v("Oslec", "Initialising echo canceller");
		echoCanState = echoCanInit(128,
						ECHO_CAN_USE_ADAPTION);
	}

	// must not be called while a process is being called.
	public void close() {
		echoCanFree(echoCanState);
		echoCanState = 0;
		freeList = null;
		recordHead = recordTail = null;
	}

	private AudioBuffer freeList;
	private AudioBuffer recordHead, recordTail;

	private class AudioBuffer {
		byte buff[];
		int processed;
		int len;
		AudioBuffer next;

		AudioBuffer(int size) {
			this.buff = new byte[size];
		}
	}

	// queue blocks of recorded audio
	// TODO refactor audio recording so we can reuse the same buffer?
	public void recordedAudio(byte buffer[], int offset, int count) {
		while (count > 0 && echoCanState != 0) {
			AudioBuffer test, buff;

			// by not changing the freeList variable in this thread, we don't
			// need to synchronize
			test = freeList;
			if (test == null || test.next == null) {
				buff = new AudioBuffer(count);
			} else {
				buff = test.next;
				test.next = buff.next;
				buff.next = null;
			}

			buff.len = count > buff.buff.length ? buff.buff.length : count;
			System.arraycopy(buffer, offset, buff.buff, 0, buff.len);
			count -= buff.len;
			offset += buff.len;

			if (recordTail == null) {
				recordHead = recordTail = buff;
			} else {
				recordTail = recordTail.next = buff;
			}
		}
	}

	// process audio we are about to play, against recorded audio we have queued
	public void process(byte[] buffer, int offset, int count, byte[] echoBuffer)
			throws IOException {
		int processed = 0;
		AudioBuffer recordBuff = null;
		while (processed < count && echoCanState != 0) {
			// never empty the recordHead list, that way we never need to
			// synchronize
			if (recordBuff == null && recordHead != null
					&& recordHead.next != null) {
				recordBuff = recordHead;
			}

			int len = count - processed;
			byte buff[] = null;
			int recordOffset=0;

			if (recordBuff != null) {
				if (recordBuff.len -recordBuff.processed < len)
					len = recordBuff.len -recordBuff.processed;
				buff = recordBuff.buff;
				recordOffset = recordBuff.processed;
			}

			if (echoCanUpdate(echoCanState,
					buffer, offset + processed,
					buff, recordOffset,
					echoBuffer, processed,
					len) < 0)
				throw new IOException("Echo cancellation failed");

			processed += len;

			if (recordBuff != null) {
				recordBuff.processed+=len;

				if (recordBuff.processed>=recordBuff.len) {
					recordBuff.processed=0;
					recordBuff.len=0;

					recordHead = recordBuff.next;
					recordBuff.next = freeList;
					freeList = recordBuff;

					recordBuff = null;
				}
			}
		}
	}
}
