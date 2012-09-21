package org.servalproject;

import java.util.List;

import org.servalproject.messages.ShowConversationActivity;
import org.servalproject.servald.IPeer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class PeerListAdapter extends ArrayAdapter<IPeer> {
	public PeerListAdapter(Context context, List<IPeer> peers) {
		super(context, R.layout.peer, R.id.Number, peers);
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		View ret = super.getView(position, convertView, parent);
		IPeer p = this.getItem(position);

		TextView displaySid = (TextView) ret.findViewById(R.id.sid);
		displaySid.setText(p.getSubscriberId().abbreviation());

		View chat = ret.findViewById(R.id.chat);
		chat.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				IPeer p = getItem(position);

				// Send MeshMS by SID
				Intent intent = new Intent(
						ServalBatPhoneApplication.context,
						ShowConversationActivity.class);
				intent.putExtra("recipient", p.getSubscriberId().toString());
				getContext().startActivity(intent);
			}
		});

		View call = ret.findViewById(R.id.call);
		if (p.getSubscriberId().isBroadcast()) {
			call.setVisibility(View.INVISIBLE);
		} else {
			call.setVisibility(View.VISIBLE);
		}

		View contact = ret.findViewById(R.id.add_contact);
		if (p.getContactId() >= 0) {
			contact.setVisibility(View.INVISIBLE);
		} else {
			contact.setVisibility(View.VISIBLE);
			contact.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					IPeer p = getItem(position);

					// Create contact if required
					p.addContact(getContext());
					v.setVisibility(View.INVISIBLE);

					// now display/edit contact
					Intent intent = new Intent(Intent.ACTION_VIEW,
							Uri.parse(
									"content://contacts/people/"
											+ p.getContactId()));
					getContext().startActivity(intent);
				}
			});
		}

		return ret;
	}

}