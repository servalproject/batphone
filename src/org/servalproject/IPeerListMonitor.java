package org.servalproject;

public interface IPeerListMonitor {

	public void registerListener(IPeerListListener listener);

	public void removeListener(IPeerListListener listener);

}
