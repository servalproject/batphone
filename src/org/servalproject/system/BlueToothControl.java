package org.servalproject.system;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servaldna.AbstractExternalInterface;
import org.servalproject.servaldna.ChannelSelector;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by jeremy on 9/02/15.
 */
public class BlueToothControl extends AbstractExternalInterface{
	private final BluetoothAdapter adapter;
	private final String myAddress;
	private boolean continuousScan = false; // TODO make sure we don't scan during a connection attempt as this can kill the connection!
	private boolean connecting = false;
	private int state;
	private int scanMode;
	private String currentName;
	private String originalName;
	private Date lastScan;
	private HashMap<String, PeerState> peers = new HashMap<String, PeerState>();
	private Listener secureListener, insecureListener;
	private final int MTU = 1400;
	private static final String TAG = "BlueToothControl";
	private static final String SERVAL_PREFIX = "Serval:";
	private final ServalBatPhoneApplication app;

	// chosen by fair dice roll (otherwise known as UUID.randomUUID())
	private static final UUID SECURE_UUID = UUID.fromString("85d832c2-b7e9-4166-a65f-695b925485aa");
	private static final UUID INSECURE_UUID = UUID.fromString("4db52983-2c1b-454e-a8ba-e8fb4ae59eeb");

	private static void close(Closeable c){
		try {
			if (c==null)
				return;
			c.close();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	public static BlueToothControl getBlueToothControl(Context context, ChannelSelector selector, int loopbackMdpPort) throws IOException {
		BluetoothAdapter a = null;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2){
			BluetoothManager m = (BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
			a = m.getAdapter();
		}else{
			a = BluetoothAdapter.getDefaultAdapter();
		}
		if (a==null) return null;
		return new BlueToothControl(selector, loopbackMdpPort, a);
	}

	private BlueToothControl(ChannelSelector selector, int loopbackMdpPort, BluetoothAdapter a) throws IOException {
		super(selector, loopbackMdpPort);
		adapter = a;
		myAddress = adapter.getAddress();
		state = adapter.getState();
		scanMode = adapter.getScanMode();
		originalName = adapter.getName();
		app = ServalBatPhoneApplication.context;
		listen();
	}

	private void up(){
		if (app.isMainThread()){
			app.runOnBackgroundThread(new Runnable() {
				@Override
				public void run() {
					up();
				}
			});
			return;
		}
		try {
			StringBuilder sb = new StringBuilder();
			// MTU = (248 - 7)/8*7
			sb	.append("socket_type=EXTERNAL\n")
				.append("prefer_unicast=1\n")
				.append("broadcast.tick_ms=120000\n")
				.append("broadcast.reachable_timeout_ms=15000\n")
				.append("broadcast.route=off\n")
				.append("broadcast.mtu=210\n")
				.append("broadcast.packet_interval=5000000\n")
				.append("unicast.tick_ms=5000\n")
				.append("unicast.reachable_timeout_ms=15000\n");
			if (!isDiscoverable())
				sb.append("broadcast.send=0\n");
			up(sb.toString());
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private void listen(){
		if (!adapter.isEnabled())
			return;
		if (app.isMainThread()){
			app.runOnBackgroundThread(new Runnable() {
				@Override
				public void run() {
					listen();
				}
			});
			return;
		}
		String app_name = app.getString(R.string.app_name);
		BluetoothServerSocket secure = null;
		try {
			secure = adapter.listenUsingRfcommWithServiceRecord(app_name, SECURE_UUID);
			secureListener = new Listener("BluetoothSL", secure);
			secureListener.start();
			Log.v(TAG, "Listening for; "+SECURE_UUID);
		} catch(IOException e){
			Log.e(TAG, e.getMessage(), e);
		}
		BluetoothServerSocket insecure = null;
		if (Build.VERSION.SDK_INT >=10) {
			try {
				insecure = adapter.listenUsingInsecureRfcommWithServiceRecord(app_name, INSECURE_UUID);
				insecureListener = new Listener("BluetoothISL", insecure);
				insecureListener.start();
				Log.v(TAG, "Listening for; " + INSECURE_UUID);
			} catch(IOException e){
				Log.e(TAG, e.getMessage(), e);
			}
		}
		up();
		if (this.continuousScan && !connecting)
			this.startDiscovery();
	}

	private void stopListening(){
		if (app.isMainThread()){
			app.runOnBackgroundThread(new Runnable() {
				@Override
				public void run() {
					stopListening();
				}
			});
			return;
		}
		try {
			down();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		if (secureListener != null) {
			secureListener.close();
			secureListener = null;
		}
		if (insecureListener != null) {
			insecureListener.close();
			insecureListener = null;
		}
		Log.v(TAG, "Stopped listening");
	}

	private class Connector implements Runnable{
		private final BluetoothSocket socket;
		private final PeerState peer;

		Connector(PeerState peer){
			this.peer=peer;
			this.socket=peer.socket;
		}

		@Override
		public void run() {
			try{
				connecting = true;
				// TODO block other connections and scans while we are connecting.
				cancelDiscovery();
				Log.v(TAG, "Connecting to " + peer.device.getAddress());
				socket.connect();

				new Thread(peer.reader, "Reader"+peer.device.getAddress()).start();
				new Thread(peer.writer, "Writer"+peer.device.getAddress()).start();
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
				peer.disconnect(socket);
			} finally{
				connecting = false;
				if (continuousScan)
					startDiscovery();
			}
		}
	}

	private class PeerReader implements Runnable{
		private final BluetoothSocket socket;
		private final PeerState peer;
		private boolean running = true;
		private Thread thread;

		PeerReader(PeerState peer){
			this.socket = peer.socket;
			this.peer = peer;
		}

		public void close(){
			if (!running)
				return;
			running = false;
			Thread t = thread;
			if (t!=null)
				t.interrupt();
		}

		@Override
		public void run() {
			thread = Thread.currentThread();
			try {
				InputStream in = socket.getInputStream();
				byte buff[] = new byte[MTU+2];
				int offset=0;
				while(running){
					if (offset>=2){
						int msgLen = (buff[0]&0xFF) | ((buff[1]&0xFF) << 8);
						if (msgLen > buff.length -2 || msgLen<0)
							throw new IllegalStateException(msgLen+" is greater than the link MTU");
						if (offset >= msgLen+2) {
							receivedPacket(peer.addrBytes, buff, 2, msgLen);
							if (offset > msgLen+2)
								System.arraycopy(buff, msgLen+2, buff, 0, offset - (msgLen + 2));
							offset -= msgLen+2;
							continue;
						}
					}
					int len = in.read(buff, offset, buff.length - offset);
					if (len<0)
						throw new EOFException();
					offset+=len;
				}
			}catch (Exception e){
				Log.e(TAG, e.getMessage(), e);
			}

			peer.disconnect(socket);
			running = false;
			thread = null;
		}
	}

	private class PeerWriter implements Runnable{
		private final BluetoothSocket socket;
		private final PeerState peer;
		private boolean running = true;
		private Thread thread;
		// TODO simpler queue?
		private BlockingQueue<byte[]> queue;

		PeerWriter(PeerState peer){
			this.socket = peer.socket;
			this.peer = peer;
			queue = new LinkedBlockingQueue<byte[]>();
		}

		public void close(){
			if (!running)
				return;
			running = false;
			Thread t = thread;
			if (t!=null)
				t.interrupt();

		}

		public void queuePacket(byte[] payload){
			queue.add(payload);
		}

		@Override
		public void run() {
			thread = Thread.currentThread();
			try {
				OutputStream out = socket.getOutputStream();
				byte buff[] = new byte[MTU + 2];
				while (running) {
					byte payload[] = queue.take();
					if (payload.length + 2 > buff.length)
						throw new IllegalStateException(payload.length + " is greater than the link MTU");
					buff[0] = (byte) (payload.length);
					buff[1] = (byte) (payload.length >> 8);
					System.arraycopy(payload, 0, buff, 2, payload.length);
					// try to write in one go or the blutooth layer will waste bandwidth sending fragments
					out.write(buff, 0, payload.length + 2);
				}
			}catch (InterruptedException e){
				//ignore
			}catch (Exception e){
				Log.e(TAG, e.getMessage(), e);
			}

			peer.disconnect(socket);
			running = false;
			thread = null;
		}
	}

	private class Listener extends Thread{
		private final BluetoothServerSocket socket;
		private boolean running=true;

		private Listener(String name, BluetoothServerSocket socket){
			super(name);
			this.socket = socket;
		}

		public void close(){
			try {
				running = false;
				socket.close();
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}

		@Override
		public void run() {
			while (running && adapter.isEnabled()) {
				try {
					BluetoothSocket client = socket.accept();
					Log.v(TAG, "Incoming connection from "+client.getRemoteDevice().getAddress());
					PeerState peer = getPeer(client.getRemoteDevice());
					peer.setSocket(client);
				}catch (Exception e){
					Log.e(TAG, e.getMessage(), e);
				}
			}
		}
	}

	public class PeerState{
		public final BluetoothDevice device;
		public Date lastScan;
		public int runningServal=-1;
		private BluetoothSocket socket;
		private PeerReader reader;
		private PeerWriter writer;
		public final byte[] addrBytes;

		private PeerState(BluetoothDevice device){
			this.device = device;
			addrBytes = getAddress(device);
		}

		private synchronized void setSocket(BluetoothSocket socket) throws IOException {
			if (this.socket != null) {
				// pick the winning socket deterministically

				if (socket!=null && device.getAddress().compareTo(adapter.getAddress())>0) {
					Log.v(TAG, "Killing incoming connection as we already have one");
					socket.close();
					return;
				}

				Log.v(TAG, "Closing existing connection");
				this.reader.close();
				this.writer.close();
				this.socket.close();
				this.socket = null;
				this.reader = null;
				this.writer = null;
			}
			if (socket != null) {
				Log.v(TAG, "Starting threads for new connection");
				this.socket = socket;
				reader = new PeerReader(this);
				writer = new PeerWriter(this);

				if (socket.isConnected()){
					new Thread(reader, "Reader" + device.getAddress()).start();
					new Thread(writer, "Writer" + device.getAddress()).start();
				}else{
					// use a single thread to ensure connections are serialised
					// TODO start another worker thread to reduce contention with the rest of the app?
					app.runOnBackgroundThread(new Connector(this));
				}
			}
		}

		public void connect() throws IOException {
			if (socket!=null)
				return;
			if (state != BluetoothAdapter.STATE_ON)
				return;
			boolean paired = device.getBondState() == BluetoothDevice.BOND_BONDED;
			if (!paired && Build.VERSION.SDK_INT <10)
				return;

			synchronized (this) {
				if (socket!=null)
					return;

				if (paired) {
					setSocket(device.createRfcommSocketToServiceRecord(SECURE_UUID));
				} else {
					setSocket(device.createInsecureRfcommSocketToServiceRecord(INSECURE_UUID));
				}
			}
		}

		public void queuePacket(byte payload[]){
			if (state != BluetoothAdapter.STATE_ON)
				return;
			try {
				connect();
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
				return;
			}
			if (writer==null)
				return;
			writer.queuePacket(payload);
		}

		public synchronized void disconnect(BluetoothSocket socket){
			try {
				if (socket == this.socket)
					setSocket(null);
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
	}

	public PeerState getPeer(BluetoothDevice device){
		PeerState s = this.peers.get(device.getAddress());
		if (s==null){
			s = new PeerState(device);
			this.peers.put(device.getAddress(), s);
		}
		return s;
	}

	private byte[] getAddress(BluetoothDevice device){
		// TODO convert mac address string to hex bytes?
		return device.getAddress().getBytes();
	}

	private PeerState getDevice(byte[] address){
		// TODO convert mac address string to hex bytes?
		String addr = new String(address);
		PeerState ret = peers.get(addr);
		if (ret==null)
			Log.v(TAG, "Unable to find bluetooth device for "+addr);
		return ret;
	}

	private String debug(byte[] values){
		StringBuilder sb = new StringBuilder();
		for (int i=0;i<values.length;i++)
			sb.append(' ').append(Integer.toBinaryString(values[i] & 0xFF));
		return sb.toString();
	}

	private byte[] decodeName(String name){
		if (name==null)
			return null;
		if (!name.startsWith(SERVAL_PREFIX))
			return null;
		try {
			byte data[]=name.substring(7).getBytes(UTF8);
			int dataLen = data.length;
			byte next;

			if (dataLen>=2 && (data[dataLen-2]&0xFF)==0xD4){
				data[dataLen-2]=0;
				dataLen--;
			}
			// decode zero bytes
			for (int i=0;i<dataLen;i++){
				if ((data[i] & 0xC0) == 0xC0){
					next = (byte) (data[i] & 1);
					data[i]=0;
					data[i+1]= (byte) ((data[i+1] & 0x1F) | (next << 6));
					i++;
				}
			}

			int len = dataLen/8*7;
			if (dataLen%8!=0)
				len+=dataLen%8 -1;
			byte ret[]=new byte[len];
			int i=0;
			int j=0;
			while(j<ret.length){
				next=data[i++];
				ret[j] = (byte) (next<< 1);
				if (i>=dataLen) break;
				next=data[i++];
				ret[j] = (byte)(ret[j] | (next>>>6));j++;
				if (j>=ret.length) break;
				ret[j] = (byte)(next << 2);
				if (i>=dataLen) break;

				next=data[i++];
				ret[j] = (byte)(ret[j] | (next>>>5));j++;
				if (j>=ret.length) break;
				ret[j] = (byte)(next << 3);
				if (i>=dataLen) break;

				next=data[i++];
				ret[j] = (byte)(ret[j] | (next>>>4));j++;
				if (j>=ret.length) break;
				ret[j] = (byte)(next << 4);
				if (i>=dataLen) break;

				next=data[i++];
				ret[j] = (byte)(ret[j] | (next>>>3));j++;
				if (j>=ret.length) break;
				ret[j] = (byte)(next << 5);
				if (i>=dataLen) break;

				next=data[i++];
				ret[j] = (byte)(ret[j] | (next>>>2));j++;
				if (j>=ret.length) break;
				ret[j] = (byte)(next << 6);
				if (i>=dataLen) break;

				next=data[i++];
				ret[j] = (byte)(ret[j] | (next>>>1));j++;
				if (j>=ret.length) break;
				ret[j] = (byte)(next << 7);
				if (i>=dataLen) break;

				next=data[i++];
				ret[j] = (byte)(ret[j] | next);j++;
			}
			return ret;
		}catch(java.lang.ArrayIndexOutOfBoundsException e){
			Log.e(TAG, "Failed to decode "+name+"\n"+e.getMessage(), e);
			throw e;
		}
	}

	private static Charset UTF8 = Charset.forName("UTF-8");

	private String encodeName(byte[] data){
		int len = data.length/7*8;
		if (data.length%7!=0)
			len+=data.length%7 +1;
		byte[] ret = new byte[len+1];
		byte next=0;
		int j=0;
		int i=0;

		while(i<data.length){
			ret[j++]=(byte)((data[i]&0xFF) >>> 1);
			next=(byte)(data[i++] << 6 & 0x7F);
			if (i>=data.length) break;
			ret[j++]=(byte)(next | ((data[i]&0xFF) >>> 2));
			next=(byte)(data[i++] << 5 & 0x7F);
			if (i>=data.length) break;
			ret[j++]=(byte)(next | ((data[i]&0xFF) >>> 3));
			next=(byte)(data[i++] << 4 & 0x7F);
			if (i>=data.length) break;
			ret[j++]=(byte)(next | ((data[i]&0xFF) >>> 4));
			next=(byte)(data[i++] << 3 & 0x7F);
			if (i>=data.length) break;
			ret[j++]=(byte)(next | ((data[i]&0xFF) >>> 5));
			next=(byte)(data[i++] << 2 & 0x7F);
			if (i>=data.length) break;
			ret[j++]=(byte)(next | ((data[i]&0xFF) >>> 6));
			next=(byte)(data[i++] << 1 & 0x7F);
			if (i>=data.length) break;
			ret[j++]=(byte)(next | ((data[i]&0xFF) >>> 7));
			ret[j++]=(byte)(data[i++] & 0x7F);
		}
		if (j%8!=0)
			ret[j++]=next;
		// escape zero bytes
		for (i=0;i<j-1;i++){
			if (ret[i]==0){
				next = ret[i + 1];
				ret[i+1] = (byte) (0x80 | (next & 0x3F));
				ret[i] = (byte) (0xD0 | (next >> 6));
				i++;
			}
		}
		if (ret[j-1]==0){
			ret[j-1] = (byte) 0xD4;
			ret[j++]= (byte) 0x80;
		}
		return SERVAL_PREFIX+encoded;
	}

	private void receivedName(PeerState peer){
		try {
			byte packet[]=decodeName(peer.device.getName());
			if (packet != null)
				this.receivedPacket(getAddress(peer.device), packet);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private void processName(final PeerState peer){
		if (app.isMainThread())
			app.runOnBackgroundThread(new Runnable() {
				@Override
				public void run() {
					receivedName(peer);
				}
			});
		else
			receivedName(peer);
	}

	@Override
	protected void sendPacket(byte[] addr, ByteBuffer payload) {
		byte payloadBytes[] = new byte[payload.remaining()];
		payload.get(payloadBytes);
		if (addr==null || addr.length==0) {
			if (isDiscoverable())
				setName(encodeName(payloadBytes));
		}else{
			PeerState peer = getDevice(addr);
			if (peer==null)
				return;
			peer.queuePacket(payloadBytes);
		}
	}

	public void onFound(Intent intent){
		BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
		PeerState peer = getPeer(device);
		peer.lastScan = new Date();
		processName(peer);
	}

	public void onRemoteNameChanged(Intent intent){
		BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
		PeerState peer = getPeer(device);
		processName(peer);
	}

	public void onScanModeChanged(Intent intent){
		scanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, 0);
		Log.v(TAG, "Scan mode changed; "+scanMode);
		if (adapter.isEnabled())
			up();
	}

	public void onDiscoveryStarted(){
		this.lastScan = new Date();
		Log.v(TAG, "Discovery Started");
	}

	public void onDiscoveryFinished(){
		Log.v(TAG, "Discovery Finished");
		// TODO use an alarm? track if we have busy connections?
		if (continuousScan  && !connecting && state==BluetoothAdapter.STATE_ON)
			startDiscovery();
	}

	public void onStateChange(Intent intent){
		// on / off etc
		state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
		Log.v(TAG, "State changed; "+state);
		scanMode = adapter.getScanMode();
		if (state == BluetoothAdapter.STATE_ON) {
			adapter.getBondedDevices(); // TODO stuff!
			listen();
		}else{
			stopListening();
		}
		if (continuousScan && !connecting)
			startDiscovery();
	}

	public void onNameChanged(Intent intent){
		String name = intent.getStringExtra(BluetoothAdapter.EXTRA_LOCAL_NAME);
		//Log.v(TAG, "Name changed; "+name);
		// TODO if not something we set, update originalName?
	}

	public void cancelDiscovery(){
		if (adapter.isDiscovering())
			adapter.cancelDiscovery();
	}

	public boolean isDiscoverable(){
		return adapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
	}

	public void requestDiscoverable(Context context){
		if (isDiscoverable())
			return;

		Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600);
		// TODO notification .... ?
		context.startActivity(discoverableIntent);
	}

	public void setName(String name){
		if (name.equals(currentName))
			return;
		// fails if the adapter is off...
		Log.v(TAG, "Setting Name; "+name);
		currentName = name;
		adapter.setName(name);
	}

	public void startDiscovery(){
		if (state != BluetoothAdapter.STATE_ON)
			return;
		if (adapter.isDiscovering())
			return;
		adapter.startDiscovery();
	}

}
