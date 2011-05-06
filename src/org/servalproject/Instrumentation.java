package org.servalproject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.servalproject.batman.FileParser;
import org.servalproject.batman.PeerRecord;
import org.servalproject.dna.Dna;
import org.servalproject.dna.OpStat;
import org.servalproject.dna.Packet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class Instrumentation extends Thread{
	public enum Variable{
		BatteryLevel((short)0x01),
		BatteryScale((short)0x02),
		BatteryVoltage((short)0x03),
		BatteryTemperature((short)0x04),
		BatteryPlugged((short)0x05),
		BatteryHealth((short)0x06);
		
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
	FileParser fileParser = FileParser.getFileParser();
	ArrayList<OpStat> pendingValues=new ArrayList<OpStat>();
	
	public static void valueChanged(Variable var, int value){
		if (instance!=null)
			instance.pendingValues.add(new OpStat(new Date(), var, value));
	}
	
	private Instrumentation(){}
	
	@Override
	public void run() {
		BroadcastReceiver instrumentReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
					valueChanged(Variable.BatteryLevel,(int)intent.getIntExtra("level",0));
					valueChanged(Variable.BatteryScale,(int)intent.getIntExtra("scale",0));
					valueChanged(Variable.BatteryVoltage,(int)intent.getIntExtra("voltage",0));
					valueChanged(Variable.BatteryTemperature,(int)intent.getIntExtra("temperature",0));
					valueChanged(Variable.BatteryPlugged,(int)intent.getIntExtra("plugged",0));
					valueChanged(Variable.BatteryHealth,(int)intent.getIntExtra("health",0));
				}
			}
		};
		
		try{
			{
				IntentFilter instrumentFilter = new IntentFilter();
				instrumentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
				ServalBatPhoneApplication.context.registerReceiver(instrumentReceiver, instrumentFilter);
			}
			
			while(true){
				
				try {
					Packet p=new Packet();
					p.setDid("");
					
					// might miss some values in a race condition, but I don't think we care.
					p.operations.addAll(pendingValues);
					pendingValues.clear();
					// TODO write statistics to sdcard log file?
					
					ArrayList<PeerRecord> peers=fileParser.getPeerList();
					dna.setDynamicPeers(peers);
					dna.beaconParallel(p);
				} catch (IOException e) {
					// Ignore file parsing errors
				}
				sleep(10000);
			}
		}catch (InterruptedException e){
			// stop processing
		}
		
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
