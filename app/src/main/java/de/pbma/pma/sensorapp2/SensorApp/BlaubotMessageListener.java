package de.pbma.pma.sensorapp2.SensorApp;

import java.util.HashMap;

// an activity or whatever may register itself and get notified
// on sendMessage events and on blaubot events and on log events and
// on whatever. You should separate the interfaces and do not mix
// everything in just one listener
public interface BlaubotMessageListener {

    //void onMessage(String id, String text);
    //void onMessage(String channelId, String messageId, String text);

    void onMessage(String deviceId, short channelId, short messageId, String userName, short userRole,
                   String message, String predictvalue, String rotation, long timestamp, boolean called, boolean mmol);

    void onBlaubotStatus(boolean connected, String deviceId);

    void onLogMessage(String message);

    void onInetAddress(String ip_address);

    void onDeviceMapChanged(HashMap<String, String> connectedDevices);

    //void onDeviceJoined(final String userName, final short userRole);

    //void onDeviceLeft(final String userName, final short userRole);
}

