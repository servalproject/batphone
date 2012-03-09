/*
 * Copyright (C) 2009 The Sipdroid Open Source Project
 *
 * This file is part of Sipdroid (http://www.sipdroid.org)
 *
 * Sipdroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.sipdroid.sipua.ui;

import org.servalproject.Main;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.sipdroid.media.RtpStreamReceiver;
import org.sipdroid.media.RtpStreamSender;
import org.sipdroid.sipua.SipdroidEngine;
import org.sipdroid.sipua.UserAgent;
import org.sipdroid.sipua.phone.Call;
import org.sipdroid.sipua.phone.Connection;
import org.zoolu.sip.provider.SipProvider;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.RemoteViews;

public class Receiver extends BroadcastReceiver {

		final static String ACTION_PHONE_STATE_CHANGED = "android.intent.action.PHONE_STATE";
		final static String ACTION_SIGNAL_STRENGTH_CHANGED = "android.intent.action.SIG_STR";
		final static String ACTION_DATA_STATE_CHANGED = "android.intent.action.ANY_DATA_STATE";
		final static String ACTION_DOCK_EVENT = "android.intent.action.DOCK_EVENT";
		final static String EXTRA_DOCK_STATE = "android.intent.extra.DOCK_STATE";
		final static String ACTION_SCO_AUDIO_STATE_CHANGED = "android.media.SCO_AUDIO_STATE_CHANGED";
		final static String EXTRA_SCO_AUDIO_STATE = "android.media.extra.SCO_AUDIO_STATE";
		final static String PAUSE_ACTION = "com.android.music.musicservicecommand.pause";
		final static String TOGGLEPAUSE_ACTION = "com.android.music.musicservicecommand.togglepause";
		final static String ACTION_DEVICE_IDLE = "com.android.server.WifiManager.action.DEVICE_IDLE";
		final static String ACTION_VPN_CONNECTIVITY = "vpn.connectivity";
		final static String ACTION_EXTERNAL_APPLICATIONS_AVAILABLE = "android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE";
		final static String ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE = "android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE";
		final static String METADATA_DOCK_HOME = "android.dock_home";
		final static String CATEGORY_DESK_DOCK = "android.intent.category.DESK_DOCK";
		final static String CATEGORY_CAR_DOCK = "android.intent.category.CAR_DOCK";
		final static int EXTRA_DOCK_STATE_DESK = 1;
		final static int EXTRA_DOCK_STATE_CAR = 2;

		public final static int MWI_NOTIFICATION = 1;
		public final static int CALL_NOTIFICATION = 2;
		public final static int MISSED_CALL_NOTIFICATION = 3;

		final int MSG_SCAN = 1;
		final int MSG_ENABLE = 2;

		final static long[] vibratePattern = {0,1000,1000};

		public static int docked = -1,headset = -1,bluetooth = -1;

		public static Context mContext;
		public static SipdroidListener listener_video;
		public static Call ccCall;
		public static Connection ccConn;
		public static int call_state;
		public static int call_end_reason = -1;

		public static String pstn_state;
		public static long pstn_time;
		public static String MWI_account;
		private static String laststate,lastnumber;

		public void register(Context context){
	    	Receiver.mContext = context;
			IntentFilter intentfilter = new IntentFilter();
			intentfilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
			intentfilter.addAction(Receiver.ACTION_DATA_STATE_CHANGED);
			intentfilter.addAction(Receiver.ACTION_PHONE_STATE_CHANGED);
			intentfilter.addAction(Receiver.ACTION_DOCK_EVENT);
			intentfilter.addAction(Intent.ACTION_HEADSET_PLUG);
			intentfilter.addAction(Intent.ACTION_USER_PRESENT);
			intentfilter.addAction(Intent.ACTION_SCREEN_OFF);
			intentfilter.addAction(Intent.ACTION_SCREEN_ON);
			intentfilter.addAction(Receiver.ACTION_VPN_CONNECTIVITY);
			intentfilter.addAction(Receiver.ACTION_SCO_AUDIO_STATE_CHANGED);
			intentfilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
			intentfilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
			context.registerReceiver(this, intentfilter);
		}

		public static Ringtone oRingtone;
		static PowerManager.WakeLock wl;

		public static void stopRingtone() {
			if (v != null)
				v.cancel();
			if (Receiver.oRingtone != null) {
				Ringtone ringtone = Receiver.oRingtone;
				oRingtone = null;
				ringtone.stop();
			}
		}

		static android.os.Vibrator v;

		public static void onState(int state,String caller) {
			if (ccCall == null) {
		        ccCall = new Call();
		        ccConn = new Connection();
		        ccCall.setConn(ccConn);
		        ccConn.setCall(ccCall);
			}
			if (call_state != state) {
				if (state != UserAgent.UA_STATE_IDLE)
					call_end_reason = -1;
				call_state = state;
				switch(call_state)
				{
				case UserAgent.UA_STATE_INCOMING_CALL:
					RtpStreamReceiver.good = RtpStreamReceiver.lost = RtpStreamReceiver.loss = RtpStreamReceiver.late = 0;
					RtpStreamReceiver.speakermode = speakermode();
					bluetooth = -1;
					String text = caller.toString();
					if (text.indexOf("<sip:") >= 0 && text.indexOf("@") >= 0)
						text = text.substring(text.indexOf("<sip:")+5,text.indexOf("@"));
					String text2 = caller.toString();
					if (text2.indexOf("\"") >= 0)
						text2 = text2.substring(text2.indexOf("\"")+1,text2.lastIndexOf("\""));
					broadcastCallStateChanged("RINGING", caller);
			        mContext.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
					ccCall.setState(Call.State.INCOMING);
					ccConn.setUserData(null);
					ccConn.setAddress(text,text2);
					ccConn.setIncoming(true);
					ccConn.date = System.currentTimeMillis();
					ccCall.base = 0;
					AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
					int rm = am.getRingerMode();
					int vs = am.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);
			        KeyguardManager mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
					if (v == null) v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
					if ((pstn_state == null || pstn_state.equals("IDLE")) &&
							PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(org.sipdroid.sipua.ui.Settings.PREF_AUTO_ON, org.sipdroid.sipua.ui.Settings.DEFAULT_AUTO_ON) &&
							!mKeyguardManager.inKeyguardRestrictedInputMode())
						v.vibrate(vibratePattern,1);
					else {
						if ((pstn_state == null || pstn_state.equals("IDLE")) &&
								(rm == AudioManager.RINGER_MODE_VIBRATE ||
								(rm == AudioManager.RINGER_MODE_NORMAL && vs == AudioManager.VIBRATE_SETTING_ON)))
							v.vibrate(vibratePattern,1);
						if (am.getStreamVolume(AudioManager.STREAM_RING) > 0) {
							String sUriSipRingtone = PreferenceManager.getDefaultSharedPreferences(mContext).getString(org.sipdroid.sipua.ui.Settings.PREF_SIPRINGTONE,
									Settings.System.DEFAULT_RINGTONE_URI.toString());
							if(!TextUtils.isEmpty(sUriSipRingtone)) {
								oRingtone = RingtoneManager.getRingtone(mContext, Uri.parse(sUriSipRingtone));
								if (oRingtone != null) oRingtone.play();
							}
						}
					}
					moveTop();
					if (wl == null) {
						PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
						wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
								PowerManager.ACQUIRE_CAUSES_WAKEUP, "Sipdroid.onState");
					}
					wl.acquire();
					break;
				case UserAgent.UA_STATE_OUTGOING_CALL:
					RtpStreamReceiver.good = RtpStreamReceiver.lost = RtpStreamReceiver.loss = RtpStreamReceiver.late = 0;
					RtpStreamReceiver.speakermode = speakermode();
					bluetooth = -1;
					onText(MISSED_CALL_NOTIFICATION, null, 0,0);
					SipdroidEngine.getEngine().registerUdp();
					broadcastCallStateChanged("OFFHOOK", caller);
					ccCall.setState(Call.State.DIALING);
					ccConn.setUserData(null);
					ccConn.setAddress(caller,caller);
					ccConn.setIncoming(false);
					ccConn.date = System.currentTimeMillis();
					ccCall.base = 0;
					moveTop();
					break;
				case UserAgent.UA_STATE_IDLE:
					broadcastCallStateChanged("IDLE", null);
					onText(CALL_NOTIFICATION, null, 0,0);
					ccCall.setState(Call.State.DISCONNECTED);
					if (listener_video != null)
						listener_video.onHangup();
					stopRingtone();
					if (wl != null && wl.isHeld())
						wl.release();
			        mContext.startActivity(createIntent(InCallScreen.class));
					ccConn.log(ccCall.base);
					ccConn.date = 0;
					SipdroidEngine.getEngine().listen();
					break;
				case UserAgent.UA_STATE_INCALL:
					broadcastCallStateChanged("OFFHOOK", null);
					if (ccCall.base == 0) {
						ccCall.base = SystemClock.elapsedRealtime();
					}
					progress();
					ccCall.setState(Call.State.ACTIVE);
					stopRingtone();
					if (wl != null && wl.isHeld())
						wl.release();
			        mContext.startActivity(createIntent(InCallScreen.class));
					break;
				case UserAgent.UA_STATE_HOLD:
					onText(CALL_NOTIFICATION, mContext.getString(R.string.card_title_on_hold), android.R.drawable.stat_sys_phone_call_on_hold,ccCall.base);
					ccCall.setState(Call.State.HOLDING);
			        if (InCallScreen.started) mContext.startActivity(createIntent(InCallScreen.class));
					break;
				}
				RtpStreamReceiver.ringback(false);
			}
		}

		static String cache_text;
		static int cache_res;

		public static void onText(int type,String text,int mInCallResId,long base) {
	        NotificationManager mNotificationMgr = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
	        if (text != null) {
		        Notification notification = new Notification();
		        notification.icon = mInCallResId;
				if (type == MISSED_CALL_NOTIFICATION) {
			        	notification.flags |= Notification.FLAG_AUTO_CANCEL;
			        	notification.setLatestEventInfo(mContext, text, mContext.getString(R.string.app_name),
			        			PendingIntent.getActivity(mContext, 0, createCallLogIntent(), 0));
			        	if (PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).getBoolean(org.sipdroid.sipua.ui.Settings.PREF_NOTIFY, org.sipdroid.sipua.ui.Settings.DEFAULT_NOTIFY)) {
				        	notification.flags |= Notification.FLAG_SHOW_LIGHTS;
				        	notification.ledARGB = 0xff0000ff; /* blue */
				        	notification.ledOnMS = 125;
				        	notification.ledOffMS = 2875;
			        	}
	        	} else {
	        		switch (type) {
		        	case MWI_NOTIFICATION:
			        	notification.flags |= Notification.FLAG_AUTO_CANCEL;
						notification.contentIntent = PendingIntent.getActivity(mContext, 0,
								createMWIIntent(), 0);
			        	notification.flags |= Notification.FLAG_SHOW_LIGHTS;
			        	notification.ledARGB = 0xff00ff00; /* green */
			        	notification.ledOnMS = 125;
			        	notification.ledOffMS = 2875;
						break;
		        	default:
					notification.contentIntent = PendingIntent.getActivity(
							mContext, 0, createIntent(Main.class), 0);
				        if (mInCallResId == R.drawable.sym_presence_away) {
				        	notification.flags |= Notification.FLAG_SHOW_LIGHTS;
				        	notification.ledARGB = 0xffff0000; /* red */
				        	notification.ledOnMS = 125;
				        	notification.ledOffMS = 2875;
				        }
		        		break;
		        	}
		        	notification.flags |= Notification.FLAG_ONGOING_EVENT;
			        RemoteViews contentView = new RemoteViews(mContext.getPackageName(),
	                        R.layout.ongoing_call_notification);
			        contentView.setImageViewResource(R.id.icon, notification.icon);
					if (base != 0) {
						contentView.setChronometer(R.id.text1, base, text+" (%s)", true);
					} else
						contentView.setTextViewText(R.id.text1, text);
					notification.contentView = contentView;
		        }
		        mNotificationMgr.notify(type,notification);
	        } else {
	        	mNotificationMgr.cancel(type);
	        }
		}

		static boolean was_playing;

		private static void broadcastCallStateChanged(String state,String number) {
			if (state == null) {
				state = laststate;
				number = lastnumber;
			}
			Intent intent = new Intent(ACTION_PHONE_STATE_CHANGED);
			intent.putExtra("state",state);
			if (number != null)
				intent.putExtra("incoming_number", number);
			intent.putExtra(mContext.getString(R.string.app_name), true);
			mContext.sendBroadcast(intent, android.Manifest.permission.READ_PHONE_STATE);
			if (state.equals("IDLE")) {
				if (was_playing) {
					if (pstn_state == null || pstn_state.equals("IDLE"))
						mContext.sendBroadcast(new Intent(TOGGLEPAUSE_ACTION));
					was_playing = false;
				}
			} else {
				AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
				if ((laststate == null || laststate.equals("IDLE")) && (was_playing = am.isMusicActive()))
					mContext.sendBroadcast(new Intent(PAUSE_ACTION));
			}
			laststate = state;
			lastnumber = number;
		}

		public static void alarm(int renew_time,Class <?>cls) {
	        Intent intent = new Intent(mContext, cls);
	        PendingIntent sender = PendingIntent.getBroadcast(mContext,
	                0, intent, 0);
			AlarmManager am = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
			am.cancel(sender);
			if (renew_time > 0)
				am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime()+renew_time*1000, sender);
		}

		public static long expire_time;

		public static void reRegister(int renew_time) {
			if (renew_time == 0)
				expire_time = 0;
			else {
				if (expire_time != 0 && renew_time*1000 + SystemClock.elapsedRealtime() > expire_time) return;
				expire_time = renew_time*1000 + SystemClock.elapsedRealtime();
			}
	       	alarm(renew_time-15, OneShotAlarm.class);
		}

		private static Intent createIntent(Class<?>cls) {
        	Intent startActivity = new Intent();
        	startActivity.setClass(mContext,cls);
    	    startActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	    return startActivity;
		}

		public static Intent createCallLogIntent() {
	        Intent intent = new Intent(Intent.ACTION_VIEW, null);
	        intent.setType("vnd.android.cursor.dir/calls");
	        return intent;
		}

		private static Intent createHomeDockIntent() {
	        Intent intent = new Intent(Intent.ACTION_MAIN, null);
	        if (docked == EXTRA_DOCK_STATE_CAR) {
		        intent.addCategory(CATEGORY_CAR_DOCK);
	        } else if (docked == EXTRA_DOCK_STATE_DESK) {
		        intent.addCategory(CATEGORY_DESK_DOCK);
	        } else {
	            return null;
	        }

	        ActivityInfo ai = intent.resolveActivityInfo(
	                mContext.getPackageManager(), PackageManager.GET_META_DATA);
	        if (ai == null) {
	            return null;
	        }

	        if (ai.metaData != null && ai.metaData.getBoolean(METADATA_DOCK_HOME)) {
	            intent.setClassName(ai.packageName, ai.name);
	            return intent;
	        }

	        return null;
	    }

	    public static Intent createHomeIntent() {
	        Intent intent = createHomeDockIntent();
	        if (intent != null) {
	            try {
	                return intent;
	            } catch (ActivityNotFoundException e) {
	            }
	        }
	        intent = new Intent(Intent.ACTION_MAIN, null);
	        intent.addCategory(Intent.CATEGORY_HOME);
	        return intent;
	    }

	    private static Intent createMWIIntent() {
			Intent intent;

			if (MWI_account != null)
				intent = new Intent(Intent.ACTION_CALL, Uri.parse(MWI_account));
			else
				intent = new Intent(Intent.ACTION_DIAL);
			return intent;
		}

	public static void moveTop() {
		progress();

		Intent startActivity = new Intent();
		startActivity.setClass(mContext, InCallScreen.class);
		startActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		mContext.startActivity(startActivity);
	}

		public static void progress() {
			if (call_state == UserAgent.UA_STATE_IDLE) return;
			int mode = RtpStreamReceiver.speakermode;
			if (mode == -1)
				mode = speakermode();
			if (mode == AudioManager.MODE_NORMAL)
				Receiver.onText(Receiver.CALL_NOTIFICATION, mContext.getString(R.string.menu_speaker), android.R.drawable.stat_sys_speakerphone,Receiver.ccCall.base);
			else if (bluetooth > 0)
			Receiver.onText(Receiver.CALL_NOTIFICATION,
					mContext.getString(R.string.menu_bluetooth),
					android.R.drawable.stat_sys_phone_call,
					Receiver.ccCall.base);
			else
			Receiver.onText(Receiver.CALL_NOTIFICATION,
					mContext.getString(R.string.card_title_in_progress),
					android.R.drawable.stat_sys_phone_call,
					Receiver.ccCall.base);
		}

		public static boolean on_wlan;

		static boolean on_vpn() {
			return PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(org.sipdroid.sipua.ui.Settings.PREF_ON_VPN, org.sipdroid.sipua.ui.Settings.DEFAULT_ON_VPN);
		}

		static void on_vpn(boolean enable) {
    		Editor edit = PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).edit();

    		edit.putBoolean(org.sipdroid.sipua.ui.Settings.PREF_ON_VPN,enable);
    		edit.commit();
		}

		public static int speakermode() {
        		if (docked > 0 && headset <= 0)
    				return AudioManager.MODE_NORMAL;
        		else
        			return AudioManager.MODE_IN_CALL;
		}

	    @Override
		public void onReceive(Context context, Intent intent) {
	        String intentAction = intent.getAction();
	        if (!SipdroidEngine.on(context)) return;
        	if (mContext == null) mContext = context;
	        if (intentAction.equals(Intent.ACTION_BOOT_COMPLETED)){
	        	on_vpn(false);

			if (ServalBatPhoneApplication.context.settings
					.getInt("has_root", 0) == 1) {
				// assume root may have disappeared on boot
				Editor ed = ServalBatPhoneApplication.context.settings.edit();
				ed.putInt("has_root", 0);
				ed.commit();
			}

	        } else
		    if (intentAction.equals(ConnectivityManager.CONNECTIVITY_ACTION) ||
		    		intentAction.equals(ACTION_EXTERNAL_APPLICATIONS_AVAILABLE) ||
		    		intentAction.equals(ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE) ||
		    		intentAction.equals(Intent.ACTION_PACKAGE_REPLACED)) {
			} else
			if (intentAction.equals(ACTION_VPN_CONNECTIVITY) && intent.hasExtra("connection_state")) {
				String state = intent.getSerializableExtra("connection_state").toString();
				if (state != null && on_vpn() != state.equals("CONNECTED")) {
					on_vpn(state.equals("CONNECTED"));
					for (SipProvider sip_provider : SipdroidEngine.getEngine().sip_providers)
						if (sip_provider != null)
							sip_provider.haltConnections();
				}
			} else
	        if (intentAction.equals(ACTION_DATA_STATE_CHANGED)) {
			} else
	        if (intentAction.equals(ACTION_PHONE_STATE_CHANGED) &&
	        		!intent.getBooleanExtra(context.getString(R.string.app_name),false)) {
	        	stopRingtone();
	    		pstn_state = intent.getStringExtra("state");
	    		pstn_time = SystemClock.elapsedRealtime();
	    		if (pstn_state.equals("IDLE") && call_state != UserAgent.UA_STATE_IDLE)
	    			broadcastCallStateChanged(null,null);
	    		if ((pstn_state.equals("OFFHOOK") && call_state == UserAgent.UA_STATE_INCALL) ||
		    			(pstn_state.equals("IDLE") && call_state == UserAgent.UA_STATE_HOLD))
		    			SipdroidEngine.getEngine().togglehold();
	        } else
	        if (intentAction.equals(ACTION_DOCK_EVENT)) {
	        	docked = intent.getIntExtra(EXTRA_DOCK_STATE, -1);
	        	if (call_state == UserAgent.UA_STATE_INCALL)
	        		SipdroidEngine.getEngine().speaker(speakermode());
	        } else
	        if (intentAction.equals(ACTION_SCO_AUDIO_STATE_CHANGED)) {
	        	bluetooth = intent.getIntExtra(EXTRA_SCO_AUDIO_STATE, -1);
	        	progress();
	        	RtpStreamSender.changed = true;
	        } else
		    if (intentAction.equals(Intent.ACTION_HEADSET_PLUG)) {
		        headset = intent.getIntExtra("state", -1);
	        	if (call_state == UserAgent.UA_STATE_INCALL)
	        		SipdroidEngine.getEngine().speaker(speakermode());
	        } else
	        if (intentAction.equals(Intent.ACTION_SCREEN_ON)) {
	        	// TODO check adhoc/batman etc?
	        } else
	        if (intentAction.equals(Intent.ACTION_USER_PRESENT)) {
		    	// TODO check adhoc/batman etc?
	        } else
	        if (intentAction.equals(Intent.ACTION_SCREEN_OFF)) {
	        	// TODO check adhoc/batman etc?
	        	if (SipdroidEngine.pwl != null)
	        		for (PowerManager.WakeLock pwl : SipdroidEngine.pwl)
	        			if (pwl != null && pwl.isHeld()) {
			        		pwl.release();
			        		pwl.acquire();
	        			}
	        } else
		    if (intentAction.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
		    	// TODO check adhoc/batman etc
	        }
		}
}
