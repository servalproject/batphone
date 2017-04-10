package org.servalproject.system;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.Log;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.IllegalFormatException;
import java.util.Properties;

public class WifiAdhocNetwork implements
		OnSharedPreferenceChangeListener {
	private static final String TAG="AdhocNetwork";
	private NetworkState state = NetworkState.Disabled;
	public final String preferenceName;
	private final SharedPreferences prefs;
	private int version = 0;
	ScanResults results;

	private Inet4Address addr;
	private Inet4Address mask;
	private String validatedAddress = null;

	public int getVersion() {
		return version;
	}

	public static byte[] lengthToMask(int length) {
		byte maskBytes[] = new byte[4];

		for (int i = 0; i < 4; i++) {
			int bits = length;
			if (bits < 0)
				bits = 0;
			if (bits > 8)
				bits = 8;
			// 0xFF ^ ((2^n)-1)
			maskBytes[i] = (byte) (0xFF ^ ((1 << (8 - bits)) - 1));
			length -= 8;
		}
		return maskBytes;
	}

	public static void randomiseAddress(byte addrBytes[], byte maskBytes[]) {
		SecureRandom random = new SecureRandom();
		byte[] randomBytes = new byte[4];

		random.nextBytes(randomBytes);
		for (int i = 0; i < addrBytes.length; i++) {
			addrBytes[i] = (byte) ((addrBytes[i] & maskBytes[i])
					| (randomBytes[i] & ~maskBytes[i]));
		}
	}

	private static boolean hasUnmaskedBits(byte addr[], byte mask[]) {
		if (addr == null || mask == null || mask.length < addr.length)
			throw new IllegalStateException();

		for (int i = 0; i < addr.length; i++) {
			if ((addr[i] & mask[i]) != addr[i])
				return true;
		}
		return false;
	}

	public static WifiAdhocNetwork getTestNetwork() throws UnknownHostException {
		String ssid = "TestingMesh" + Math.random();
		if (ssid.length() > 32)
			ssid = ssid.substring(0, 32);
		final String tempSid = ssid;

		return new WifiAdhocNetwork(null, null) {
			@Override
			String getNetwork() {
				return "10.0.0.1/8";
			}

			@Override
			void setNetwork(String network) {
			}

			@Override
			public String getSSID() {
				return tempSid;
			}

			@Override
			int getChannel() {
				return 11;
			}

			@Override
			String getTxPower() {
				return "disabled";
			}
		};
	}

	String getNetwork() {
		return prefs.getString("lannetworkpref", null);
	}

	void setNetwork(String network) {
		Editor ed = prefs.edit();
		ed.putString("lannetworkpref", network);
		ed.commit();
	}

	public String getSSID() {
		return prefs.getString("ssidpref", null);
	}

	int getChannel() {
		String channel = prefs.getString("channelpref", null);
		if (channel == null)
			return 11;
		return Integer.parseInt(channel);
	}

	String getTxPower() {
		return prefs.getString("txpowerpref", "disabled");
	}

	public static WifiAdhocNetwork getAdhocNetwork(Context context,
			String profile) {
		return new WifiAdhocNetwork(context, profile);
	}

	// do this by hand, as we don't need to support hostnames and can't run network code on the UI thread
	private static byte[] ipStrToBytes(String addr) {
		String[] bytes = addr.split("\\.");
		if (bytes.length != 4)
			throw new NumberFormatException();
		byte ret[] = new byte[4];
		for (int i = 0; i < bytes.length; i++) {
			int val = Integer.parseInt(bytes[i]);
			if (val < 0 || val > 255)
				throw new NumberFormatException();
			ret[i]=(byte)val;
		}
		return ret;
	}

	public WifiAdhocNetwork(Context context, String preferenceName) {
		this.preferenceName = preferenceName;
		if (preferenceName != null && context != null) {
			this.prefs = context.getSharedPreferences(preferenceName, 0);
			this.prefs.registerOnSharedPreferenceChangeListener(this);
		} else {
			this.prefs = null;
		}
		updateAddress();
	}

	private void updateAddress() {
		String lannetwork = this.getNetwork();

		// default to a random 28.0.0.0/7 address (from DOD address
		// ranges)
		byte addrBytes[] = null;
		int maskLen = -1;

		if (lannetwork != null && !"".equals(lannetwork)) {
			if (lannetwork.equals(validatedAddress))
				return;

			try{
				String[] pieces = lannetwork.split("/");
				if (pieces.length!=2)
					throw new NumberFormatException();
				addrBytes = ipStrToBytes(pieces[0]);
				maskLen = Integer.parseInt(pieces[1]);
				if (maskLen<0 || maskLen>32)
					throw new NumberFormatException();
			}catch(Exception e){
				// undo
				ServalBatPhoneApplication.context.displayToastMessage(R.string.settings_formABC);
				addrBytes = null;
				maskLen = -1;
				if (validatedAddress!=null) {
					setNetwork(validatedAddress);
					return;
				}
			}
		}

		if (addrBytes == null)
			addrBytes = new byte[] {
					28, 0, 0, 0
			};

		if (maskLen < 0)
			maskLen = 7;

		byte maskBytes[] = lengthToMask(maskLen);

		if (!hasUnmaskedBits(addrBytes, maskBytes))
			randomiseAddress(addrBytes, maskBytes);

		try {
			addr = (Inet4Address) Inet4Address.getByAddress(addrBytes);
			mask = (Inet4Address) Inet4Address.getByAddress(maskBytes);

			String newValue =addr.getHostAddress() + "/"
					+ Integer.toString(maskLen);

			validatedAddress = newValue;
			if (!newValue.equals(lannetwork))
				this.setNetwork(newValue);
		} catch (UnknownHostException e) {
			// shouldn't happen....
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private void updateAdhocConf() {
		try {
			Properties props = new Properties();
			CoreTask coretask = ServalBatPhoneApplication.context.coretask;
			String adhoc = coretask.DATA_FILE_PATH + "/conf/adhoc.conf";

			props.load(new FileInputStream(adhoc));

			props.put("wifi.essid", this.getSSID());
			props.put("ip.network", addr.getHostAddress());
			props.put("ip.netmask", mask.getHostAddress());
			props.put("ip.gateway", addr.getHostAddress());
			props.put("wifi.interface", coretask.getProp("wifi.interface"));
			props.put("wifi.txpower", this.getTxPower());
			int channel = this.getChannel();
			props.put("wifi.channel", Integer.toString(channel));
			int frequency = 2437;
			switch (channel) {
			case 1:
				frequency = 2412;
				break;
			case 2:
				frequency = 2417;
				break;
			case 3:
				frequency = 2422;
				break;
			case 4:
				frequency = 2427;
				break;
			case 5:
				frequency = 2432;
				break;
			case 6:
				frequency = 2437;
				break;
			case 7:
				frequency = 2442;
				break;
			case 8:
				frequency = 2447;
				break;
			case 9:
				frequency = 2452;
				break;
			case 10:
				frequency = 2457;
				break;
			case 11:
				frequency = 2462;
				break;
			case 12:
				frequency = 2467;
				break;
			case 13:
				frequency = 2472;
				break;
			case 14:
				frequency = 2484;
				break;
			}
			props.put("wifi.frequency", Integer.toString(frequency));

			props.store(new FileOutputStream(adhoc), null);
		} catch (IOException e) {
			Log.e(TAG, e.toString(), e);
		}
	}

	private void updateTiWLANConf() {
		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
		String find[] = new String[] {
				"WiFiAdhoc", "dot11DesiredSSID",
				"dot11DesiredChannel", "dot11DesiredBSSType", "dot11PowerMode"
		};
		String replace[] = new String[] {
				"1", this.getSSID(), Integer.toString(this.getChannel()), "0",
				"1"
		};

		app.replaceInFile("/system/etc/wifi/tiwlan.ini",
						app.coretask.DATA_FILE_PATH + "/conf/tiwlan.ini", find,
						replace);
	}

	public void updateConfiguration() {
		updateAdhocConf();
		updateTiWLANConf();
	}

	public void setNetworkState(NetworkState state) {
		this.state = state;
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return this.getSSID();
	}

	public String getDetails(Context context) {
		return context.getString(R.string.adhocconnectmessage,
				getSSID(),
				Integer.valueOf(getChannel()),
				getNetwork());
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		if (key.equals("lannetworkpref"))
			this.updateAddress();
		version++;
	}

	public int getBars() {
		return results == null ? -1 : results.getBars();
	}

	public String getType() {
		return "Mesh";
	}

	public InetAddress getAddress() throws UnknownHostException {
		if (state == NetworkState.Enabled)
			return addr;
		return null;
	}
}