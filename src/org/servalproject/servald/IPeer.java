package org.servalproject.servald;


import android.content.Context;

public interface IPeer {

	public SubscriberId getSubscriberId();

	public long getContactId();

	public void addContact(Context context);

	public boolean hasName();

	public String getSortString();

	public String getDid();
}
