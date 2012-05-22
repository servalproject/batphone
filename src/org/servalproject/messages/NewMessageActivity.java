/*
 * Copyright (C) 2012 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
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

package org.servalproject.messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.servalproject.IPeerListListener;
import org.servalproject.IPeerListMonitor;
import org.servalproject.PeerComparator;
import org.servalproject.PeerList;
import org.servalproject.R;
import org.servalproject.rhizome.RhizomeMessage;
import org.servalproject.servald.Identities;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.SubscriberId;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author brendon
 *
 *         activity used to send a new message
 */
public class NewMessageActivity extends Activity implements OnClickListener
{

	/*
	 * private class level constants
	 */
	// request code for the contact picker activity
	private final int PICK_CONTACT_REQUEST = 1;

	// codes to determine which dialog to show
	private final int DIALOG_RECIPIENT_EMPTY = 0;
	private final int DIALOG_RECIPIENT_INVALID = 1;
	private final int DIALOG_CONTENT_EMPTY = 2;

	private final String TAG = "NewMessageActivity";

	private Adapter listAdapter;
	private List<Peer> peers = new ArrayList<Peer>();

	/*
	 * private class level variables
	 */
	private final ContactAccessor contactAccessor = new ContactAccessor();
	private TextView contentLength;
	private String contentLengthTemplate;

	private Peer selectedPeer;

	private AutoCompleteTextView actv;

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.new_message);

		bindService(new Intent(this, PeerListService.class), svcConn,
				BIND_AUTO_CREATE);

		listAdapter = new Adapter(this);
		listAdapter.setNotifyOnChange(false);

		actv = (AutoCompleteTextView) findViewById(R.id.new_message_ui_txt_recipient);
		actv.setAdapter(listAdapter);
		actv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position,
					long id) {
				selectedPeer = listAdapter.getItem(position);
			}
		});

		Button button = (Button) findViewById(R.id.new_message_ui_btn_send_message);
		button.setOnClickListener(this);

		contentLengthTemplate = getString(R.string.new_message_ui_txt_length);

		contentLength = (TextView) findViewById(R.id.new_message_ui_txt_length);

		TextView content = (TextView) findViewById(R.id.new_message_ui_txt_content);
		content.addTextChangedListener(contentWatcher);

		// see if a phone number has been supplied
		Intent mIntent = getIntent();
		String recipientSid = mIntent.getStringExtra("recipient");
		if (recipientSid != null) {
			try {
				SubscriberId sid = new SubscriberId(recipientSid);
				selectedPeer = PeerListService.getPeer(
						getContentResolver(), sid);
				TextView mRecipient = (TextView) findViewById(R.id.new_message_ui_txt_recipient);

				if (selectedPeer.getContactName() == null
						|| "".equals(selectedPeer.getContactName())) {
					mRecipient.setText(selectedPeer.did);
				} else {
					mRecipient.setText(selectedPeer.getContactName());
				}
				content.requestFocus();
			} catch (SubscriberId.InvalidHexException e) {
				Log.e(TAG, "Intent contains invalid SID: "
						+ recipientSid, e);
				finish();
				return;
			}
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		service.removeListener(listener);
		unbindService(svcConn);
	}

	private IPeerListListener listener = new IPeerListListener() {
		@Override
		public void peerChanged(final Peer p) {
			// TODO - do we need to resolve peers in the background?
			// if (p.cacheUntil <= SystemClock.elapsedRealtime())
			// unresolved.put(p.sid, p);
			int pos = peers.indexOf(p);
			if (pos >= 0) {
				peers.set(pos, p);
			} else {
				peers.add(p);
			}
			Collections.sort(peers, new PeerComparator());
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					listAdapter.notifyDataSetChanged();
				}
			});
		}
	};

	// keep track of the number of characters remaining in the description
	private final TextWatcher contentWatcher = new TextWatcher() {

		@Override
		public void afterTextChanged(Editable arg0) {

		}

		@Override
		public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
				int arg3) {

		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before,
				int count) {
			contentLength.setText(Integer.toString(s.length()));
		}
	};

	/*
	 * (non-Javadoc)
	 *
	 * @see android.view.View.OnClickListener#onClick(android.view.View)
	 */
	@Override
	public void onClick(View v) {
		// work out which button was clicked
		if (v.getId() == R.id.new_message_ui_btn_send_message) {
			// send the new MeshMS message
			sendMessage();
		}

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.i(TAG, "requestCode " + requestCode + ", resultCode " + resultCode);
		if (requestCode == PICK_CONTACT_REQUEST) {
			if (resultCode == RESULT_OK) {
				String sidString = data.getStringExtra(PeerList.SID);
				try {
					SubscriberId sid = new SubscriberId(sidString);
					selectedPeer = PeerListService.getPeer(
							getContentResolver(), sid);
					Log.i(TAG,
							"Received recipient: "
									+ selectedPeer.getContactName() + ", "
									+ sid.abbreviation());
					TextView mRecipient = (TextView) findViewById(R.id.new_message_ui_txt_recipient);
					mRecipient.setText(selectedPeer.getContactName());
					actv.dismissDropDown();
					// set focus to message field (this will prevent drop down from
					// appearing on the recipient field)
					TextView mMessage = (TextView) findViewById(R.id.new_message_ui_txt_content);
					mMessage.requestFocus();
				}
				catch (SubscriberId.InvalidHexException e) {
					Log.e(TAG, "Received invalid SID: " + sidString, e);
				}
			}
		}
	}

	/*
	 * send a new MeshMS message
	 */
	private void sendMessage() {

		// validate the fields
		if (selectedPeer == null) {
			showDialog(DIALOG_RECIPIENT_EMPTY);
			return;
		}

		TextView mTextView = (TextView) findViewById(R.id.new_message_ui_txt_content);

		if (TextUtils.isEmpty(mTextView.getText()) == true) {
			showDialog(DIALOG_CONTENT_EMPTY);
			return;
		}

		String mContent = mTextView.getText().toString();

		// send the message
		RhizomeMessage message = new RhizomeMessage(
				Identities.getCurrentIdentity(), selectedPeer.sid, mContent);
		Intent mMeshMSIntent = new Intent(
				"org.servalproject.meshms.SEND_MESHMS");
		mMeshMSIntent.putExtra("senderSid", message.sender.toString());
		mMeshMSIntent.putExtra("senderDid", Identities.getCurrentDid());
		mMeshMSIntent.putExtra("recipientSid", message.recipient.toString());
		mMeshMSIntent.putExtra("recipientDid", selectedPeer.did);
		mMeshMSIntent.putExtra("content", message.message);
		startService(mMeshMSIntent);

		saveMessage(message);

		finish();

	}

	// save the message
	private void saveMessage(RhizomeMessage message) {
		ContentResolver contentResolver = getContentResolver();
		// save the message
		int[] result = MessageUtils.saveReceivedMessage(
				message,
				contentResolver);

		int threadId = result[0];
		int messageId = result[1];

		int toastMessageId;
		if (messageId != -1) {
			Log.i(TAG, "New message saved with messageId '" + messageId
					+ "', threadId '" + threadId + "'");
			toastMessageId = R.string.new_message_ui_toast_sent_successfully;
		} else {
			Log.e(TAG, "unable to save new message");
			toastMessageId = R.string.new_message_ui_toast_sent_unsuccessfully;
		}
		// keep the user informed
		Toast.makeText(getApplicationContext(),
				toastMessageId,
				Toast.LENGTH_LONG).show();
	}

	/*
	 * dialog related methods
	 */

	/*
	 * callback method used to construct the required dialog (non-Javadoc)
	 *
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {

		// create the required alert dialog
		AlertDialog.Builder mBuilder = new AlertDialog.Builder(this);
		AlertDialog mDialog = null;

		switch (id) {
		case DIALOG_RECIPIENT_EMPTY:
			mBuilder.setMessage(R.string.new_message_ui_dialog_recipient_empty)
					.setPositiveButton(android.R.string.ok,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							});
			mDialog = mBuilder.create();
			break;

		case DIALOG_RECIPIENT_INVALID:
			mBuilder.setMessage(
					R.string.new_message_ui_dialog_recipient_invalid)
					.setPositiveButton(android.R.string.ok,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							});
			mDialog = mBuilder.create();
			break;

		case DIALOG_CONTENT_EMPTY:
			mBuilder.setMessage(R.string.new_message_ui_dialog_content_empty)
					.setPositiveButton(android.R.string.ok,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							});
			mDialog = mBuilder.create();

			break;
		default:
			mDialog = null;
		}

		return mDialog;
	}

	private IPeerListMonitor service = null;
	private ServiceConnection svcConn = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className,
				IBinder binder) {
			Log.i(TAG, "service created");
			service = (IPeerListMonitor) binder;
			service.registerListener(listener);
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			Log.i(TAG, "service disconnected");
			service = null;
		}
	};

	class Adapter extends ArrayAdapter<Peer> {
		public Adapter(Context context) {
			super(context, R.layout.message_recipient,
					R.id.recipient_number, peers);
		}

		@Override
		public View getView(final int position, View convertView,
				ViewGroup parent) {
			View ret = super.getView(position, convertView, parent);

			Peer r = listAdapter.getItem(position);

			TextView displayName = (TextView) ret
					.findViewById(R.id.recipient_name);
			displayName.setText(r.getContactName());

			TextView displaySid = (TextView) ret
					.findViewById(R.id.recipient_number);
			displaySid.setText(r.did);

			ImageView type = (ImageView) ret.findViewById(R.id.recipient_type);
			type.setBackgroundResource(R.drawable.ic_24_serval);

			return ret;
		}

	}

}
