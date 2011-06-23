package org.servalproject;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.servalproject.batman.PeerRecord;
import org.servalproject.dna.Dna;
import org.servalproject.dna.OpStat;
import org.servalproject.dna.Packet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class Instrumentation extends Thread{
	public enum Variable{
		BatteryLevel((short)0x01),
		BatteryScale((short)0x02),
		BatteryVoltage((short)0x03),
		BatteryTemperature((short)0x04),
		BatteryPlugged((short)0x05),
		BatteryHealth((short)0x06),
		StillAlive((short)0x07),
		PeerCount((short)0x08);

		public short code;
		Variable(short code){
			this.code=code;
		}
	}

	private static Map<Short, Variable> varByCode;
	static{
		varByCode=new HashMap<Short, Variable>();
		for (Variable v:Variable.values()){
			varByCode.put(v.code, v);
		}
	}

	public static Variable getVariable(short code){
		return varByCode.get(code);
	}

	static Instrumentation instance;

	Dna dna = new Dna();
	ArrayList<OpStat> pendingValues=new ArrayList<OpStat>();

	public static void valueChanged(Variable var, int value){
		if (instance!=null)
			instance.pendingValues.add(new OpStat(new Date(), var, value));
	}

	private Instrumentation(){
		try {
			// allow the local dna instance to log our packets
			dna.addStaticPeer(Inet4Address.getLocalHost());
		} catch (UnknownHostException e) {
		}
	}

	@Override
	public void run() {
		BroadcastReceiver instrumentReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
					valueChanged(Variable.BatteryLevel,intent.getIntExtra("level",0));
					valueChanged(Variable.BatteryScale,intent.getIntExtra("scale",0));
					valueChanged(Variable.BatteryVoltage,intent.getIntExtra("voltage",0));
					valueChanged(Variable.BatteryTemperature,intent.getIntExtra("temperature",0));
					valueChanged(Variable.BatteryPlugged,intent.getIntExtra("plugged",0));
					valueChanged(Variable.BatteryHealth,intent.getIntExtra("health",0));
				}
			}
		};

		try{
			{
				IntentFilter instrumentFilter = new IntentFilter();
				instrumentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
				ServalBatPhoneApplication.context.registerReceiver(instrumentReceiver, instrumentFilter);
			}

			Log.v("BatPhone","Instrumentation thread starting");

			while(true){

				Packet p=new Packet();
				p.setDid("");

				// might miss some values in a race condition, but I don't think we care.
				p.operations.addAll(pendingValues);
				pendingValues.clear();

				if (p.operations.isEmpty())
					p.operations.add(new OpStat(new Date(), Variable.StillAlive, 0));

				try{
					ArrayList<PeerRecord> peers = ServalBatPhoneApplication.context.wifiRadio
							.getPeers();
					if (peers==null || peers.isEmpty())
						Log.v("BatPhone","No remote peers to forward instrumentation to.");
					dna.setDynamicPeers(peers);
				} catch (IOException e) {
					dna.setDynamicPeers(null);
				}

				try {
					dna.beaconParallel(p);
				} catch (IOException e) {}
				sleep(10000);
			}
		}catch (InterruptedException e){
			// stop processing
		}

		Log.v("BatPhone","Instrumentation thread shut down");
		ServalBatPhoneApplication.context.unregisterReceiver(instrumentReceiver);
	}

	public static boolean isEnabled(){
		return instance!=null;
	}

	public static void setEnabled(boolean enabled){
		if (enabled == (instance!=null))
			return;

		if (enabled){
			instance=new Instrumentation();
			instance.start();
		}else{
			instance.interrupt();
			instance=null;
		}
	}
}
