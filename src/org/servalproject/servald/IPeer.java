package org.servalproject.servald;


import android.content.Context;
import android.content.OperationApplicationException;
import android.os.RemoteException;

public interface IPeer {

	public SubscriberId getSubscriberId();

	public long getContactId();

	public void addContact(Context context) throws RemoteException,
			OperationApplicationException;

	public boolean hasName();

	public String getSortString();

	public String getDid();
}
