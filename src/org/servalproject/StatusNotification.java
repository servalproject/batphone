package org.servalproject;

import java.io.IOException;
import java.util.Date;

import org.servalproject.batman.FileParser;
import org.servalproject.batman.ServiceStatus;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class StatusNotification {
	TrafficCounter trafficCounterThread;
	private ServalBatPhoneApplication app;
	
    // Notification
	private NotificationManager notificationManager;
	private Notification notification;
	private PendingIntent mainIntent;
	
	StatusNotification(ServalBatPhoneApplication app){
		this.app=app;
        // init notificationManager
        this.notificationManager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
    	this.notification = new Notification(R.drawable.start_notification, "Serval BatPhone", System.currentTimeMillis());
    	this.mainIntent = PendingIntent.getActivity(app, 0, new Intent(app, MainActivity.class), 0);
	}
	
    // Notification
    public void showStatusNotification() {
		notification.flags = Notification.FLAG_ONGOING_EVENT;
    	notification.setLatestEventInfo(app, "Serval BatPhone", "BatPhone is currently running ...", this.mainIntent);
    	this.notificationManager.notify(-1, this.notification);
    }
    
    public void hideStatusNotification(){
    	this.notificationManager.cancel(-1);
    }
    
   	public void trafficCounterEnable(Handler callback) {
   		if (callback != null) {
			if (this.trafficCounterThread == null || this.trafficCounterThread.isAlive() == false) {
				this.trafficCounterThread = new TrafficCounter(callback);
				this.trafficCounterThread.start();
			}
   		} else {
	    	if (this.trafficCounterThread != null)
	    		this.trafficCounterThread.interrupt();
   		}
   	}
   	
   	class TrafficCounter extends Thread {
   		private static final int INTERVAL = 2;  // Sample rate in seconds.
   		long previousDownload;
   		long previousUpload;
   		long lastTimeChecked;
   		Handler callback;
   		
   		TrafficCounter(Handler callback){
   			this.callback=callback;
   		}
   		
   		public void run() {
            try {
	   			this.previousDownload = this.previousUpload = 0;
	   			this.lastTimeChecked = new Date().getTime();
	   			FileParser fileParser = new FileParser(ServiceStatus.PEER_FILE_LOCATION);
	   			
	   			String adhocNetworkDevice = app.getAdhocNetworkDevice();
	   			
	   			while (true) {
			        // Check data count
			        long [] trafficCount = app.coretask.getDataTraffic(adhocNetworkDevice);
			        long currentTime = new Date().getTime();
			        float elapsedTime = (float) ((currentTime - this.lastTimeChecked) / 1000);
			        this.lastTimeChecked = currentTime;
			        DataCount datacount = new DataCount();
			        datacount.totalUpload = trafficCount[0];
			        datacount.totalDownload = trafficCount[1];
			        try {
						datacount.peerCount=fileParser.getPeerCount();
					} catch (IOException e) {
						datacount.peerCount=-1;
						Log.v("BatPhone",e.toString(),e);
					}
			        datacount.uploadRate = (long) ((datacount.totalUpload - this.previousUpload)*8/elapsedTime);
			        datacount.downloadRate = (long) ((datacount.totalDownload - this.previousDownload)*8/elapsedTime);
					Message message = Message.obtain();
					message.what = MainActivity.MESSAGE_TRAFFIC_COUNT;
					message.obj = datacount;
					callback.sendMessage(message); 
					this.previousUpload = datacount.totalUpload;
					this.previousDownload = datacount.totalDownload;
					
					Thread.sleep(INTERVAL * 1000);
	   			}
            } catch (InterruptedException e) {
            }
			Message message = Message.obtain();
			message.what = MainActivity.MESSAGE_TRAFFIC_END;
			MainActivity.currentInstance.viewUpdateHandler.sendMessage(message); 
   		}
   	}
   	
   	public class DataCount {
   		// Total data uploaded
   		public long totalUpload;
   		// Total data downloaded
   		public long totalDownload;
   		// Current upload rate
   		public long uploadRate;
   		// Current download rate
   		public long downloadRate;
   		// Total number of BATMAN peers in range
   		public long peerCount;
   	}
}
