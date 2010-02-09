package android.tether.system;

import android.app.Application;
import android.os.Build;

public abstract class BluetoothService {

	public abstract boolean startBluetooth();
	public abstract boolean stopBluetooth();
	public abstract boolean isBluetoothEnabled();
	public abstract void setApplication(Application application);
	
	private static BluetoothService bluetoothService;
	
	public static BluetoothService getInstance() {
	    if (bluetoothService == null) {
	        String className;

	        int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
	        if (sdkVersion < Build.VERSION_CODES.ECLAIR) {
	            className = "android.tether.system.BluetoothService_cupcake";
	        } else {
	            className = "android.tether.system.BluetoothService_eclair";
	        }
	
	        try {
	            Class<? extends BluetoothService> clazz = Class.forName(className).asSubclass(BluetoothService.class);
	            bluetoothService = clazz.newInstance();
	        } catch (Exception e) {
	            throw new IllegalStateException(e);
	        }
	    }
	    return bluetoothService;
	}
}
