package org.servalproject.batphone;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.account.AccountService;
import org.servalproject.servald.IPeerListListener;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servaldna.SubscriberId;

public class UnsecuredCall extends Activity {

	ServalBatPhoneApplication app;
	CallHandler callHandler;

	private TextView remote_name;
	private TextView remote_number;
	private TextView action;

	public static final String EXTRA_SID="sid";
	public static final String EXTRA_EXISTING="existing";

	// Create runnable for posting
	final Runnable updateCallStatus = new Runnable() {
		@Override
		public void run() {
			updateUI();
		}
	};
	private Button endButton;
	private Button incomingEndButton;
	private Button incomingAnswerButton;
	private Chronometer chron;

	private void updateUI()
	{
		if (callHandler==null)
			return;
		final Window win = getWindow();
		int incomingCallFlags =
				WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
				| WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
				| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
						| WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;

		Log.d("VoMPCall", "Updating UI for state " + callHandler.state);

		chron.setBase(callHandler.getCallStarted());

		action.setText(getString(callHandler.state.displayResource));
		if (callHandler.state == CallHandler.CallState.Ringing)
			win.addFlags(incomingCallFlags);
		else
			win.clearFlags(incomingCallFlags);

		switch (callHandler.state){
			case Ringing:
				incomingEndButton.setVisibility(View.VISIBLE);
				incomingAnswerButton.setVisibility(View.VISIBLE);
				endButton.setVisibility(View.GONE);
				break;
			case End:
				incomingEndButton.setVisibility(View.GONE);
				incomingAnswerButton.setVisibility(View.GONE);
				endButton.setVisibility(View.VISIBLE);
				chron.stop();
				callHandler.setCallUI(null);
				callHandler = null;
				break;
			default:
				incomingEndButton.setVisibility(View.GONE);
				incomingAnswerButton.setVisibility(View.GONE);
				endButton.setVisibility(View.VISIBLE);
		}
	}

	private void processIntent(Intent intent) {
		try{
			SubscriberId sid = null;
			boolean existing = false;
			String action = intent.getAction();

			if (Intent.ACTION_VIEW.equals(action)) {
				// This activity has been triggered from clicking on a SID
				// in contacts.
				Cursor cursor = getContentResolver().query(
						intent.getData(),
						new String[] {
								ContactsContract.Data.DATA1
						},
						ContactsContract.Data.MIMETYPE + " = ?",
						new String[] {
								AccountService.SID_FIELD_MIMETYPE
						},
						null);
				try {
					if (cursor.moveToNext())
						sid = new SubscriberId(cursor.getString(0));
				} finally {
					cursor.close();
				}

			} else {
				String sidString = intent.getStringExtra(EXTRA_SID);
				if (sidString != null)
					sid = new SubscriberId(sidString);
				existing = intent.getBooleanExtra(EXTRA_EXISTING, false);
			}

			if (sid == null)
				throw new IllegalArgumentException("Missing argument sid");

			if (existing){
				if (app.callHandler != null && app.callHandler.remotePeer.getSubscriberId().equals(sid)) {
					app.callHandler.setCallUI(this);
					this.callHandler = app.callHandler;
				}else{
					throw new Exception("That call no longer exists");
				}
			}else{
				this.callHandler = CallHandler.dial(this, PeerListService.getPeer(sid));
			}

			updatePeerDisplay();
			updateUI();
		}catch (Exception ex){
			ServalBatPhoneApplication.context.displayToastMessage(ex
					.getMessage());
			Log.e("BatPhone", ex.getMessage(), ex);
			if (app.callHandler != null)
				app.callHandler.hangup();
			finish();
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		processIntent(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Log.d("VoMPCall", "Activity started");

		app = (ServalBatPhoneApplication) this.getApplication();

		setContentView(R.layout.incall);

		chron = (Chronometer) findViewById(R.id.call_time);
		remote_name = (TextView) findViewById(R.id.caller_name);
		remote_number = (TextView) findViewById(R.id.ph_no_display);
		action = (TextView) findViewById(R.id.call_action_type);

		endButton = (Button) this.findViewById(R.id.cancel_call_button);
		endButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (callHandler==null || callHandler.state == CallHandler.CallState.End)
					finish();
				else
					callHandler.hangup();
			}
		});

		incomingEndButton = (Button) this.findViewById(R.id.incoming_decline);
		incomingEndButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				callHandler.hangup();
			}
		});

		incomingAnswerButton = (Button) this
				.findViewById(R.id.answer_button_incoming);
		incomingAnswerButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				callHandler.pickup();
			}
		});

		try{
			processIntent(this.getIntent());
		} catch (Exception e) {
		}
	}

	private IPeerListListener peerListener = new IPeerListListener(){
		@Override
		public void peerChanged(Peer p) {
			if (callHandler==null)
				return;
			if (p == callHandler.remotePeer){
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						updatePeerDisplay();
					}
				});
			}
		}
	};

	private void updatePeerDisplay() {
		if (callHandler==null)
			return;
		remote_name.setText(callHandler.remotePeer.getContactName());
		remote_number.setText(callHandler.remotePeer.did);
	}

	@Override
	protected void onPause() {
		super.onPause();
		chron.stop();
		PeerListService.removeListener(peerListener);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (callHandler!=null){
			chron.setBase(callHandler.getCallStarted());
			if (callHandler.state != CallHandler.CallState.End)
				chron.start();
		}
		PeerListService.addListener(peerListener);
	}

	@Override
	public void onBackPressed() {
		// cancel call before going back.
		if (callHandler!=null && callHandler.state.ordinal() < CallHandler.CallState.InCall.ordinal())
			callHandler.hangup();
		super.onBackPressed();
	}
}
