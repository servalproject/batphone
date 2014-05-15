package org.servalproject.servald;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.SubscriberId;
import org.servalproject.ui.TunnelSearchActivity;

import java.io.IOException;

/**
 * Created by jeremy on 12/05/14.
 */
public class TunnelService extends Service{
	private static final String TAG = "TunnelService";
	private Process mspTunnel;
	private Process proxy;
	private String type;
	private boolean client;
	private int ipPort;
	private int mdpPort;
	private SubscriberId peer;

	public SubscriberId getPeer() {
		return peer;
	}

	public int getIpPort() {
		return ipPort;
	}

	public int getMdpPort() {
		return mdpPort;
	}

	public String getType() {
		return type;
	}

	public boolean isClient(){
		return client;
	}

	public static final String EXTRA_TYPE="type";
	public static final String EXTRA_MODE="mode";
	public static final String EXTRA_PEER="peer";
	public static final String EXTRA_PORT="port";

	public static final String HTTP_PROXY = "http_proxy";
	public static final String SOCKS5 = "socks5";
	public static final String CLIENT = "client";
	public static final String SERVER = "server";

	public static final String ACTION_STATE="org.servalproject.tunnel_state";

	private static TunnelService instance;
	public static TunnelService getInstance(){
		return instance;
	}

	private void killProcess(Process p){
		if (p==null)
			return;

		try {
			p.getInputStream().close();
			p.destroy();
			p.waitFor();
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private void reset(){
		instance = null;
		peer = null;
		ipPort = 0;
		mdpPort = 0;
		type = null;
		client = true;
		killProcess(mspTunnel);
		mspTunnel=null;
		killProcess(proxy);
		proxy=null;
		this.sendBroadcast(new Intent(ACTION_STATE));
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		reset();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (mspTunnel==null){
			ServalBatPhoneApplication app = ServalBatPhoneApplication.context;

			type = intent.getStringExtra(EXTRA_TYPE);
			String mode = intent.getStringExtra(EXTRA_MODE);
			String notificationText=null;
			int typeString;

			try {
				if (HTTP_PROXY.equals(type)) {
					typeString = R.string.http_proxy;
					ipPort = 8123;
				} else if (SOCKS5.equals(type)) {
					typeString = R.string.socks5;
					ipPort = 1123;
				} else {
					throw new IOException("Unknown type " + type);
				}
				mdpPort = intent.getIntExtra(EXTRA_PORT, ipPort);

				if (CLIENT.equals(mode)){
					client = true;
					peer = new SubscriberId(intent.getStringExtra(EXTRA_PEER));
					mspTunnel = ServalDCommand.mspTunnelConnect(app.server.getExecPath(), ipPort, peer, mdpPort);
					notificationText="127.0.0.1:"+ipPort;
				}else if(SERVER.equals(mode)) {
					if (type.equals(HTTP_PROXY)) {
						proxy = new ProcessBuilder(
								app.coretask.DATA_FILE_PATH+"/bin/inetd",
								Integer.toString(ipPort),
								app.coretask.DATA_FILE_PATH+"/bin/proxyhttp"
						).start();
					} else if (type.equals(SOCKS5)) {
						proxy = new ProcessBuilder(
								app.coretask.DATA_FILE_PATH+"/bin/srelay",
								"-f",
								"-i",
								"127.0.0.1:"+Integer.toString(ipPort)
						).start();
					}
					client = false;
					mspTunnel = ServalDCommand.mspTunnnelCreate(app.server.getExecPath(), ipPort, type, mdpPort);
					notificationText="Accepting connections";
				}
				instance = this;
			} catch (Exception e) {
				reset();
				Log.e(TAG, e.getMessage(), e);
				return START_NOT_STICKY;
			}

			Notification notification = new Notification(
					R.drawable.ic_serval_logo, getString(typeString),
					System.currentTimeMillis());

			Intent actionIntent = new Intent(this, TunnelSearchActivity.class);
			actionIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

			notification.setLatestEventInfo(this, getString(typeString),
					notificationText,
					PendingIntent.getActivity(this, 0, actionIntent,
							PendingIntent.FLAG_UPDATE_CURRENT));

			notification.flags = Notification.FLAG_ONGOING_EVENT;
			this.startForeground(-2, notification);

			this.sendBroadcast(new Intent(ACTION_STATE));
		}
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
}
