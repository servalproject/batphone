package org.servalproject;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import org.servalproject.servald.IPeer;
import org.servalproject.ui.SimpleAdapter;

/**
 * Created by jeremy on 14/05/14.
 */
public class PeerBinder<T extends IPeer> implements SimpleAdapter.ViewBinder<T>{

	public boolean showCall=true;
	public boolean showChat=true;
	public boolean showContact=true;

	private final Context context;
	public PeerBinder(Context context){
		this.context = context;
	}

	@Override
	public long getId(int position, T t) {
		return 0;
	}

	@Override
	public int getViewType(int position, T t) {
		return 0;
	}

	public int getTextColour(T t){
		return t.isReachable()? Color.WHITE:Color.GRAY;
	}

	@Override
	public void bindView(int position, T t, View view) {
		TextView displayName = (TextView) view.findViewById(R.id.Name);
		TextView displaySid = (TextView) view.findViewById(R.id.sid);
		TextView displayNumber = (TextView) view.findViewById(R.id.Number);
		View chat = view.findViewById(R.id.chat);
		View call = view.findViewById(R.id.call);
		View contact = view.findViewById(R.id.add_contact);

		int textColour = getTextColour(t);

		displaySid.setText(t.getSubscriberId().abbreviation());
		displaySid.setTextColor(textColour);
		displayNumber.setText(t.getDid());
		displayNumber.setTextColor(textColour);
		displayName.setTextColor(textColour);
		displayName.setText(t.toString());

		if (showCall) {
			call.setVisibility(t.getSubscriberId().isBroadcast() ? View.INVISIBLE : View.VISIBLE);
			call.setOnClickListener(t);
		}else{
			call.setVisibility(View.GONE);
		}
		if (showContact) {
			contact.setVisibility(t.getContactId() >= 0 ? View.INVISIBLE : View.VISIBLE);
			contact.setOnClickListener(t);
		}else{
			contact.setVisibility(View.GONE);
		}
		if (showChat) {
			chat.setVisibility(t.getSubscriberId().isBroadcast() ? View.INVISIBLE : View.VISIBLE);
			chat.setOnClickListener(t);
		}else{
			chat.setVisibility(View.GONE);
		}

			/*new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					ServalBatPhoneApplication app = ServalBatPhoneApplication.context;

					T p = getItem(position);

					if (!ServalD.isRhizomeEnabled()) {
						ServalBatPhoneApplication.context.displayToastMessage("Messaging cannot function without an sdcard");
						return;
					}

					// Send MeshMS by SID
					Intent intent = new Intent(
							app, ShowConversationActivity.class);
					intent.putExtra("recipient", t.getSubscriberId().toHex());
					context.startActivity(intent);
				}
			});*/
	}

	@Override
	public int[] getResourceIds() {
		return new int[]{R.layout.peer};
	}

	@Override
	public boolean hasStableIds() {
		return false;
	}

	@Override
	public boolean isEnabled(T t) {
		return t.isReachable();
	}
}
