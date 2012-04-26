package org.servalproject.batphone;

public class BatPhone {

	static BatPhone instance = null;

	public static BatPhone getEngine() {
		// TODO Auto-generated method stub
		if (instance == null)
			instance = new BatPhone();
		return instance;
	}

	public void call(String phoneNumber) {
		// TODO Auto-generated method stub

	}

}
