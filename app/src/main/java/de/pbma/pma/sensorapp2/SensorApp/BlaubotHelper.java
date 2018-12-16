package de.pbma.pma.sensorapp2.SensorApp;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Locale;

// Why on earth do they protect the mac address in a way that I can scan the mac
// address on others devices nearby, but not my own?
// https://developer.android.com/about/versions/marshmallow/android-6.0-changes.html

// Wi-Fi helper, to create a BlaubotAndroid instance via WLAN despite
// being on Android 6.0+ (no valid mac address) or on tethering (each
// time a different mac address)
public class BlaubotHelper {
    final static String TAG = BlaubotHelper.class.getSimpleName();

    // the mac address since API-Level 23 instead of the real one
    final static byte[] hiddenMacSinceApiLevel23 = {2, 0, 0, 0, 0, 0};
    // the alternative network adapter names to be used in case we have
    // an unusable mac address
    final static String[] defaultAlternativeNames = { "wlan0", "wlan1" };

    // I need to know whether we are on a tethering infrastructure as then
    // the mac address is changing on every start and thus is no longer a constant
    // http://stackoverflow.com/questions/9065592/how-to-detect-wifi-tethering-state
    private static boolean isWifiApEnabled(WifiManager wifiManager) {
        Method[] wmMethods = wifiManager.getClass().getDeclaredMethods();
        for (Method method: wmMethods) {
            if (method.getName().equals("isWifiApEnabled")) {
                try {
                    boolean result = (Boolean) method.invoke(wifiManager);
                    return result;
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "isWifiAppEnabled: IllegalArgumentException");
                } catch (IllegalAccessException e) {
                    Log.e(TAG, "isWifiAppEnabled: IllegalAccessException");
                } catch (InvocationTargetException e) {
                    Log.e(TAG, "isWifiAppEnabled: InvocationTargetException");
                }
            }
        }
        return false; // no such method, not enabled
    }

    // find out the mac address on the device we run
    private static byte[] getOwnMacAddress(WifiManager wifiManager) {
        final WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        final String macAddress = wifiInfo.getMacAddress();
        final String[] macParts = macAddress.split(":");
        final byte[] macBytes = new byte[macParts.length];
        for (int i = 0; i< macParts.length; i++) {
            macBytes[i] = (byte) Integer.parseInt(macParts[i], 16);
        }
        return macBytes;
    }

    // recreate a mac address from bytes (hex) separated by colon
    private static String join(byte[] a) {
        if (a == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (byte b : a) {
            if (first) {
                first = false;
            } else {
                sb.append(":");
            }
            sb.append(b);
        }
        return sb.toString();
    }

    // we have to do funny stuff on modern android to just get the own main internet address
    // http://stackoverflow.com/questions/16730711/get-my-wifi-ip-address-android
    public static Enumeration<InetAddress> getWifiInetAddresses(final Context context) {
        final WifiManager wifiManager =
                (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        final byte[] macBytes = getOwnMacAddress(wifiManager);
        String[] alternativeNames = new String[0];
        // Log.v(TAG, "macBytes                =" + join(macBytes));
        // Log.v(TAG, "hiddenMacSinceApiLevel23=" + join(hiddenMacSinceApiLevel23));
        if (Arrays.equals(macBytes, hiddenMacSinceApiLevel23)) {
            Log.w(TAG, "API-Level 23, no mac address; try alternatively network adapter names");
            alternativeNames = defaultAlternativeNames;
        }
        if (isWifiApEnabled(wifiManager)) {
            Log.w(TAG, "Wifi-Access-Point enabled; try alternatively network adapter names");
            alternativeNames = defaultAlternativeNames;
        }
        try {
            final Enumeration<NetworkInterface> e =  NetworkInterface.getNetworkInterfaces();
            // Log.v(TAG, "getWifiInet: enum");
            while (e.hasMoreElements()) { // for each network elements
                final NetworkInterface networkInterface = e.nextElement();
                // Log.v(TAG, "getWifiInet: enum " + networkInterface);
                String displayName = networkInterface.getDisplayName();
                byte[] hwaddr = networkInterface.getHardwareAddress();
                // Log.v(TAG, "hwaddr of " + displayName + " = " + join(hwaddr));
                if (Arrays.equals(hwaddr, macBytes)) { // it is the right mac address
                    Locale locale = Locale.getDefault();
                    Log.v(TAG, String.format(locale,
                            "getWifiInetAddresses, hwAddre=%s",  join(hwaddr)));
                    return networkInterface.getInetAddresses();
                }
                for (String name : alternativeNames) { // or an ok network adapter name
                    if (name.equals(displayName)) {
                        Locale locale = Locale.getDefault();
                        Log.v(TAG, String.format(locale,
                                "getWifiInetAddresses, name=%s", name));
                        return networkInterface.getInetAddresses();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "Unable to use NetworkInterface.getNetworkInterfaces()");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static<T extends InetAddress> T getWifiInetAddress(final Context context,
                                                              final Class<T> inetClass) {
        final Enumeration<InetAddress> e = getWifiInetAddresses(context);
        if (e == null) {
            Log.e(TAG, "getWifiInetAddresses return null, no Inet-Addresses?");
            return null;
        }
        while (e.hasMoreElements()) {
            final InetAddress inetAddress = e.nextElement();
            if (inetAddress.getClass() == inetClass) {
                return (T)inetAddress;
            }
        }
        return null;
    }

}
