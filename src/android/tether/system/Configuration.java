package android.tether.system;

import java.io.File;

public class Configuration {

	/**
	 * Returns the device-type as string.
	 * A very ugly hack - checking for wifi-kernel-modules.
	 */
	public static String getDeviceType() {
		if ((new File("/system/lib/modules/bcm4329.ko")).exists() == true) {
			return "nexusone";
		}
		else if ((new File("/system/libmodules/bcm4325.ko")).exists() == true) {
			return "galaxy";
		}
		else if ((new File("/system/lib/modules/tiwlan_drv.ko")).exists() == true 
				&& (new File("/system/etc/wifi/fw_wlan1271.bin")).exists() == true){
			return "droid";
		}
		else if ((new File("/system/lib/modules/tiwlan_drv.ko")).exists() == true 
				&& (new File("/system/etc/wifi/Fw1273_CHIP.bin")).exists() == true) {
			return "legend";
		}
		else if ((new File("/system/lib/modules/wlan.ko")).exists() == true) {
			return "dream";
		}
		else if ((new File("/lib/modules/dhd.ko")).exists() == true
				&& (new File("/etc/rtecdc.bin")).exists() == true){
			return "moment";
		}
		else if ((new File("/system/lib/dhd.ko")).exists() == true
				&& (new File("/etc/wifi/sdio-g-cdc-reclaim-wme.bin")).exists() == true){
			return "cliq";
		}	
		else if ((new File("/system/etc/wifi/dhd.ko")).exists() == true
				&& (new File("/etc/wifi/BCM4325.bin")).exists() == true){
			return "liquid";
		}		
		return "unknown";
	}
	
	/**
	 * Returns the wpa_supplicant-driver which should be used
	 * on wpa_supplicant-start 
	 */
	public static String getWpaSupplicantDriver(String deviceType) {
		if (deviceType.equals("dream")) {
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
