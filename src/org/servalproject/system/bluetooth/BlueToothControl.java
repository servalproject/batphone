package org.servalproject.system.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servaldna.AbstractExternalInterface;
import org.servalproject.servaldna.ChannelSelector;
import org.servalproject.system.NetworkState;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

/**
 * Created by jeremy on 9/02/15.
 */
public class BlueToothControl extends AbstractExternalInterface{
	final BluetoothAdapter adapter;
	private final String myAddress;
	private int state;
	private int scanMode;
	private String currentName;
	private String originalName;
	private long lastScan;
	private boolean scanAgain = false;
	private boolean scanCancelled = false;
	private HashMap<String, PeerState> peers = new HashMap<String, PeerState>();
	private Listener secureListener, insecureListener;
	static final int MTU = 1200;
	private static final String TAG = "BlueToothControl";
	private static final String SERVAL_PREFIX = "Serval:";
	private static final String BLUETOOTH_NAME = "bluetoothName";
	private final ServalBatPhoneApplication app;

	// chosen by fair dice roll (otherwise known as UUID.randomUUID())
	static final UUID SECURE_UUID = UUID.fromString("85d832c2-b7e9-4166-a65f-695b925485aa");
	static final UUID INSECURE_UUID = UUID.fromString("4db52983-2c1b-454e-a8ba-e8fb4ae59eeb");

	private static void close(Closeable c){
		try {
			if (c==null)
				return;
			c.close();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	public static BlueToothControl getBlueToothControl(ChannelSelector selector, int loopbackMdpPort) throws IOException {
		BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
		if (a==null) return null;
		return new BlueToothControl(selector, loopbackMdpPort, a);
	}

	private BlueToothControl(ChannelSelector selector, int loopbackMdpPort, BluetoothAdapter a) throws IOException {
		super(selector, loopbackMdpPort);
		adapter = a;
		myAddress = adapter.getAddress();
		state = -1;
		scanMode = adapter.getScanMode();
		app = ServalBatPhoneApplication.context;
		lastScan = adapter.isDiscovering()?SystemClock.elapsedRealtime():0;
		String myName = adapter.getName();
		if (myName==null || myName.startsWith(SERVAL_PREFIX)){
			myName = app.settings.getString(BLUETOOTH_NAME, "");
		}
		originalName = myName;
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

			// We model "broadcast" packets using the bluetooth name of this device
			// This can be useful for easily discovering that our software is running

			// However, we have to initiate a scan to read bluetooth names,
			// which massively reduces our available bandwidth

			// We don't really want to know what the connectivity picture looks like.
			// We want our servald daemon to make those decisions.

			// So we assume that setting our name should trigger a device scan in order to detect
			// the name change of other peers. If this is the only link between two devices,
			// servald will probably try to send packets as fast as we allow.

			// And we set the tickms interval to 2 minutes, to force a periodic scan for peer detection.

			// MTU = trunc((248 - 7)/8)*7 = 210
			// on some devices it seems to be (127 - 7)/8*7 = 105

			sb	.append("socket_type=EXTERNAL\n")
				.append("prefer_unicast=on\n")
				.append("broadcast.tick_ms=120000\n")
				.append("broadcast.reachable_timeout_ms=180000\n")
				.append("broadcast.transmit_timeout_ms=15000\n")
				.append("broadcast.route=off\n")
				.append("broadcast.mtu=210\n")
				.append("broadcast.packet_interval=5000000\n")
				.append("unicast.tick_ms=5000\n")
				.append("unicast.reachable_timeout_ms=15000\n");
			up(sb.toString());
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private void listen(){
		if (!adapter.isEnabled() || !app.isEnabled())
			return;
		if (secureListener!=null)
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
			secureListener = new Listener("BluetoothSL", secure, true);
			secureListener.start();
			Log.v(TAG, "Listening for; " + SECURE_UUID);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		BluetoothServerSocket insecure = null;
		if (Build.VERSION.SDK_INT >= 10) {
			try {
				insecure = adapter.listenUsingInsecureRfcommWithServiceRecord(app_name, INSECURE_UUID);
				insecureListener = new Listener("BluetoothISL", insecure, false);
				insecureListener.start();
				Log.v(TAG, "Listening for; " + INSECURE_UUID);
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
		up();
		startDiscovery();
	}

	private void stopListening(){
		if (secureListener==null)
			return;

		if (app.isMainThread()){
			app.runOnBackgroundThread(new Runnable() {
				@Override
				public void run() {
					stopListening();
				}
			});
			return;
		}
		setName(originalName);
		try {
			down();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}

		for(PeerState p:peers.values()){
			p.disconnect();
		}
		peers.clear();

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

	private class Listener extends Thread{
		private final BluetoothServerSocket socket;
		private final boolean secure;
		private boolean running=true;

		private Listener(String name, BluetoothServerSocket socket, boolean secure){
			super(name);
			this.socket = socket;
			this.secure = secure;
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
					peer.onConnected(client, secure);
				}catch (Exception e){
					Log.e(TAG, e.getMessage(), e);
				}
			}
		}
	}

	public PeerState getPeer(BluetoothDevice device){
		PeerState s = this.peers.get(device.getAddress());
		if (s==null){
			s = new PeerState(this, device, getAddress(device));
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
					data[i+1]= (byte) ((data[i+1] & 0x3F) | (next << 6));
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
		return SERVAL_PREFIX + new String(ret, 0, j, UTF8);
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
		// TODO do we need a wakelock?

		// check for broken bluetooth state...
		long now = SystemClock.elapsedRealtime();

		if (this.lastScan!=0 && this.lastScan + 240000 < now && adapter.isDiscovering()){
			Log.v(TAG, "Last scan started " + (SystemClock.elapsedRealtime() - this.lastScan) + "ms ago, probably need to restart bluetooth");
		}

		byte payloadBytes[] = new byte[payload.remaining()];
		payload.get(payloadBytes);
		if (addr==null || addr.length==0) {
			String name = encodeName(payloadBytes);
			setName(name);
			startDiscovery();
		}else{
			PeerState peer = getDevice(addr);
			if (peer==null)
				return;
			peer.queuePacket(payloadBytes);
		}
	}

	public void onFound(Intent intent){
		if (!app.isEnabled())
			return;
		BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
		PeerState peer = getPeer(device);
		peer.lastScan = new Date();
		processName(peer);
	}

	public void onRemoteNameChanged(Intent intent){
		if (!app.isEnabled())
			return;
		BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
		PeerState peer = getPeer(device);
		processName(peer);
	}

	public void onScanModeChanged(Intent intent){
		scanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, 0);
		Log.v(TAG, "Scan mode changed; "+scanMode+" "+adapter.isEnabled());
		if (adapter.isEnabled())
			up();
	}

	public void onDiscoveryStarted(){
		this.lastScan = SystemClock.elapsedRealtime();
		Log.v(TAG, "Discovery Started");
		// TODO set alarm to cancel / restart bluetooth
	}

	private void startDiscovery(){
		if (!app.isEnabled() || state!=BluetoothAdapter.STATE_ON || !adapter.isEnabled())
			return;

		if (Connector.connecting || adapter.isDiscovering()) {
			scanAgain = true;
			return;
		}

		// TODO, do we need to grab a wakelock until discovery finishes?
		adapter.startDiscovery();
		scanAgain = false;
	}

	public void onConnectionFinished(){
		if (scanAgain)
			startDiscovery();
	}

	public void onDiscoveryFinished(){
		Log.v(TAG, "Discovery Finished");
		if (scanAgain)
			startDiscovery();
	}

	private void setState(int state){
		if (this.state != state) {
			this.state = state;
			Log.v(TAG, "State changed; " + state);
		}
		scanMode = adapter.getScanMode();
		if (state == BluetoothAdapter.STATE_ON && app.isEnabled()) {
			listen();
			adapter.getBondedDevices(); // TODO connect to paired neighbours?
		}else{
			stopListening();
		}
	}

	public void onEnableChanged(){
		setState(adapter.getState());
	}

	public void onStateChange(Intent intent){
		// on / off etc
		setState(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0));
	}

	public void setEnabled(boolean enabled){
		if (state==BluetoothAdapter.STATE_ON && !enabled)
			adapter.disable();
		if (state==BluetoothAdapter.STATE_OFF && enabled)
			adapter.enable();
	}

	public NetworkState getState(){
		switch (adapter.getState()){
			case BluetoothAdapter.STATE_ON:
				return NetworkState.Enabled;
			case BluetoothAdapter.STATE_OFF:
				return NetworkState.Disabled;
			case BluetoothAdapter.STATE_TURNING_ON:
				return NetworkState.Enabling;
			case BluetoothAdapter.STATE_TURNING_OFF:
				return NetworkState.Disabling;
			default:
				return NetworkState.Error;
		}
	}

	public void onNameChanged(Intent intent){
		String name = intent.getStringExtra(BluetoothAdapter.EXTRA_LOCAL_NAME);
		if (name!=null && !name.startsWith(SERVAL_PREFIX) && !name.equals(originalName)){
			originalName = name;
			SharedPreferences.Editor e = app.settings.edit();
			e.putString(BLUETOOTH_NAME, name);
			e.commit();
		}else if (!name.equals(currentName)){
			Log.v(TAG, "name ("+name+")!= currentName ("+currentName+")!? ");
		}
	}

	public boolean isDiscoverable(){
		return adapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
	}

	public boolean isEnabled(){
		return adapter.isEnabled();
	}

	public void requestDiscoverable(Context context){
		if (isDiscoverable())
			return;

		Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600);
		// TODO notify user .... ?
		context.startActivity(discoverableIntent);
	}

	public void setName(String name){
		// fails if the adapter is off...
		currentName = name;
		adapter.setName(name);
	}

	public void cancelDiscovery(){
		if (adapter.isDiscovering())
			adapter.cancelDiscovery();
	}


}
