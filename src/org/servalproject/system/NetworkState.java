package org.servalproject.system;

import android.content.Context;

import org.servalproject.R;

/**
 * Created by jeremy on 17/03/14.
 */
public enum NetworkState {
	Enabled(R.string.wifi_enabled),
	Enabling(R.string.wifi_enabling),
	Disabled(R.string.wifi_disabled),
	Disabling(R.string.wifi_disabling),
	Error(R.string.wifi_error);

	private final int resourceId;
	private NetworkState(int resourceId){
		this.resourceId = resourceId;
	}
	public String toString(Context context){
		return context.getString(resourceId);
	}
}
