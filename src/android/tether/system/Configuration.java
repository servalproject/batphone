package android.tether.system;

import java.io.File;

import android.os.Build;

public class Configuration {

	public static String DEVICE_NEXUSONE = "nexusone";
	public static String DEVICE_GALAXY1X = "galaxy1x";
	public static String DEVICE_GALAXY2X = "galaxy2x";
	public static String DEVICE_DROID    = "droid";
	public static String DEVICE_LEGEND   = "legend";
	public static String DEVICE_DREAM    = "dream";
	public static String DEVICE_HERO1X   = "hero1x";
	public static String DEVICE_HERO2X   = "hero2x";
	public static String DEVICE_MOMENT   = "moment";
	public static String DEVICE_CLIQ     = "cliq";
	public static String DEVICE_LIQUID   = "liquid";
	public static String DEVICE_UNKOWN   = "unknown";
	
	/**
	 * Returns the device-type as string.
	 * A very ugly hack - checking for wifi-kernel-modules.
	 */
	public static String getDeviceType() {
		if ((new File("/system/lib/modules/bcm4329.ko")).exists() == true) {
			return DEVICE_NEXUSONE;
		}
		else if ((new File("/system/libmodules/bcm4325.ko")).exists() == true) {
			int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
        	if (sdkVersion >= Build.VERSION_CODES.DONUT) {
        		return DEVICE_GALAXY2X;
        	}
			return DEVICE_GALAXY1X;
		}
		else if ((new File("/system/lib/modules/tiwlan_drv.ko")).exists() == true 
				&& (new File("/system/etc/wifi/fw_wlan1271.bin")).exists() == true){
			return DEVICE_DROID;
		}
		else if ((new File("/system/lib/modules/tiwlan_drv.ko")).exists() == true 
				&& (new File("/system/etc/wifi/Fw1273_CHIP.bin")).exists() == true) {
			return DEVICE_LEGEND;
		}
		else if ((new File("/system/lib/modules/wlan.ko")).exists() == true) {
			if ((NativeTask.getProp("ro.product.device")).contains("hero")) {
				int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
	        	if (sdkVersion >= Build.VERSION_CODES.ECLAIR) {
	        		return DEVICE_HERO2X;
	        	}
	        	return DEVICE_HERO1X;
			}
			return DEVICE_DREAM;
		}
		else if ((new File("/lib/modules/dhd.ko")).exists() == true
				&& (new File("/etc/rtecdc.bin")).exists() == true){
			return DEVICE_MOMENT;
		}
		else if ((new File("/system/lib/dhd.ko")).exists() == true
				&& (new File("/etc/wifi/sdio-g-cdc-reclaim-wme.bin")).exists() == true){
			return DEVICE_CLIQ;
		}	
		else if ((new File("/system/etc/wifi/dhd.ko")).exists() == true
				&& (new File("/etc/wifi/BCM4325.bin")).exists() == true){
			return DEVICE_LIQUID;
		}		
		return DEVICE_UNKOWN;
	}
	
	/**
	 * Returns the wpa_supplicant-driver which should be used
	 * on wpa_supplicant-start 
	 */
	public static String getWpaSupplicantDriver(String deviceType) {
		if (deviceType.equals(DEVICE_DREAM) || deviceType.equals(DEVICE_HERO1X) || deviceType.equals(DEVICE_HERO2X)) {
			return "tiwlan0";
		}
		return "wext";
	}

	/**
	 * Returns the wpa_supplicant-driver which should be used
	 * on wpa_supplicant-start 
	 */
	public static String getEncryptionAutoMethod(String deviceType) {
		if (deviceType.equals("legend") || deviceType.equals("nexusone")) {
			return "iwconfig";
		}
		return "wpa_supplicant";
	}
}
