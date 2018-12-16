package de.pbma.pma.sensorapp2.SensorApp;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.hgross.blaubot.android.BlaubotAndroid;
import eu.hgross.blaubot.android.BlaubotAndroidFactory;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.ILifecycleListener;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;
import eu.hgross.blaubot.ethernet.BlaubotEthernetAdapter;
import eu.hgross.blaubot.ethernet.BlaubotEthernetMulticastBeacon;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.messaging.IBlaubotMessageListener;

import static de.pbma.pma.sensorapp2.SensorApp.MainActivity.blaubotService;
import static de.pbma.pma.sensorapp2.SensorApp.MainActivity.blaubot_connected;
import static de.pbma.pma.sensorapp2.SensorApp.SensorAppHelpers.USER_ROLE_CARETAKER;
import static de.pbma.pma.sensorapp2.SensorApp.SensorAppHelpers.getUserRoleString;


public class BlaubotService extends Service {
    final static String TAG = BlaubotService.class.getSimpleName();

    final static UUID APP_UUID = UUID.fromString("28af7798-4d59-11e8-8ac4-901b0ee18b9f"); // Identifier for the app

    // Port numbers
    final static int WIFI_ACCEPTOR_PORT = 17042; // only for Wi-Fi adapter
    final static int WIFI_BEACON_PORT = 17117; // only for Wi-Fi multicast beacon
    final static int WIFI_BEACON_BROADCAST_PORT = 17142; // only for Wi-Fi multicast beacon

    // for LocalService getInstance
    final static String ACTION_START = "start";
    final static String ACTION_STOP = "stop";

    // for LocalService Messaging
    final static String ACTION_BIND = "sendMessage";
    final static String ACTION_LOG = "log";

    // Bluetooth or WLAN
    final static String EXTRA_BLAUBOT_TYPE = "blaubot_type";
    final static String BLAUBOT_BLUETOOTH = "bluetooth";
    final static String BLAUBOT_WIFI = "wifi";

    // MessageKeys
    final static String MSGKEY_DEVICE_ID = "id";
    final static String MSGKEY_MESSAGE_ID = "message_id";
    final static String MSGKEY_PAYLOAD = "text";
    final static String MSGKEY_PREDICTION = "prediction";
    final static String MSGKEY_ROTATION = "rotation";
    final static String MSGKEY_USER_NAME =  "user_name";
    final static String MSGKEY_USER_ROLE = "user_role";
    final static String MSGKEY_TIMESTAMP = "time_stamp";
    final static String MSGKEY_CALLED = "called";
    final static String MSGKEY_MMOL = "unit_mmol";

    // Message ID's
    final static short MSG_ID_CONNECTED = 1;
    final static short MSG_ID_ONLINE = 2;
    final static short MSG_ID_DISCONNECTED = 3;
    final static short MSG_ID_SENSOR_DATA = 4;
    final static short MSG_ID_EMERGENCY_CALL = 112; // :-D

    private CopyOnWriteArrayList<BlaubotMessageListener> blaubotMessageListeners = new CopyOnWriteArrayList<>();

    // Local device names
    private String localDeviceName; // should be the device name with blaubot
    private String shortLocalDeviceName; // try to use as short id

    // Channels and their listeners
    final static short CHANNEL_ID_STATUS = 1; // Channel ID
    final static short CHANNEL_ID_SENSOR_DATA = 2; // Channel ID
    final static short CHANNEL_ID_EMERGENCY = 3; // Channel ID

    private IBlaubotChannel channelStatus; // Channel for status messages
    private IBlaubotChannel channelSensorData; // Channel for sensor data
    private IBlaubotChannel channelEmergency; // Channel for emergency messages
    private IBlaubotMessageListener channelStatusReceiveListener; // Listener for the status messages channel
    private IBlaubotMessageListener channelSensorDataReceiveListener; // Listener for the sensor data channel
    private IBlaubotMessageListener channelEmergencyReceiveListener; // Listener for the emergency messages channel

    // Blaubot instance
    private BlaubotAndroid blaubot; // in case we do blaubot that is the central instance
    private String blaubotType;

    // For device to device communication
    private String m_userName = "";
    private Short m_userRole = 0;
    public static HashMap<String, String> connected_devices_hash_map = new HashMap<String, String>();

    // Blaubot ping
    private BlaubotPing blaubotPing = null;
    private Thread blaubotPingThread = null;


    // methods to call from a connected activity
    // mainly register and deregister callbacks to get notified
    // there can be several types of blaubotMessageListeners or any type of methods
    // in the callbacks. If it is like that it needs to be a local service
    public boolean registerBlaubotMessageListener(BlaubotMessageListener blaubotMessageListener) {
        return blaubotMessageListeners.addIfAbsent(blaubotMessageListener);
    }

    public boolean deregisterBlaubotMessageListener(BlaubotMessageListener blaubotMessageListener) {
        return blaubotMessageListeners.remove(blaubotMessageListener);
    }

/*    public void sendMessage(short channelId, short messageId, String payload) { // called by activity after binding
        Log.v(TAG, "Sending message...");
        if (blaubot == null) {
            String msg = "sendMessage, no blaubot running";
            Log.e(TAG, msg);
            remoteLog(msg);
            return;
        }
        try {
            JSONObject msg = new JSONObject(); // wrap in JSON
            // msg.put(MSGKEY_DEVICE_ID, localDeviceName);
            msg.put(MSGKEY_DEVICE_ID, shortLocalDeviceName);
            msg.put(MSGKEY_MESSAGE_ID, messageId);
            msg.put(MSGKEY_PAYLOAD, payload);
            // Log.v(TAG, "send: " + msg.toString());
            switch (channelId) {
                case CHANNEL_ID_STATUS:
                    channelStatus.publish(msg.toString().getBytes());
                case CHANNEL_ID_SENSOR_DATA:
                    channelSensorData.publish(msg.toString().getBytes());
            }
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            remoteLog(e.getMessage());
        }
    }*/
    // end methods to call from a connceted activity


    public void sendMessage(short channelId, short messageId, String userName, short userRole, String readvalue,
                            String predictvalue, String rotation, long timestamp, boolean called, boolean mmol) { // called by activity after binding
        //Log.v(TAG, "Sending status message...");
        if (blaubot == null) {
            String msg = "sendMessage, no blaubot running";
            Log.e(TAG, msg);
            remoteLog(msg);
            return;
        }

        try {
            JSONObject msg = new JSONObject(); // wrap in JSON
            msg.put(MSGKEY_DEVICE_ID, shortLocalDeviceName);
            msg.put(MSGKEY_MESSAGE_ID, messageId);
            msg.put(MSGKEY_USER_NAME, userName);
            msg.put(MSGKEY_USER_ROLE, userRole);
            msg.put(MSGKEY_PAYLOAD, readvalue);
            msg.put(MSGKEY_PREDICTION, predictvalue);
            msg.put(MSGKEY_ROTATION, rotation);
            msg.put(MSGKEY_TIMESTAMP, timestamp);
            msg.put(MSGKEY_CALLED, called);
            msg.put(MSGKEY_MMOL, mmol);
            // Log.v(TAG, "send: " + msg.toString());
            switch (channelId) {
                case CHANNEL_ID_STATUS:
                    channelStatus.publish(msg.toString().getBytes(), true);
                case CHANNEL_ID_SENSOR_DATA:
                    channelSensorData.publish(msg.toString().getBytes(), true);
                case CHANNEL_ID_EMERGENCY:
                    channelEmergency.publish(msg.toString().getBytes(), true);
            }
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            remoteLog(e.getMessage());
        }
    }


    @Override
    public void onCreate() {
        //Log.v(TAG, "onCreate");
        super.onCreate();
        localDeviceName = null;
        blaubot = null;
        blaubotType = null;
        channelStatus = null;
        channelSensorData = null;
        channelEmergency = null;

    }

    public class LocalBinder extends Binder {
        BlaubotService getBlaubotService() {
            return BlaubotService.this;
        }
    }
    private IBinder localBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        //Log.v(TAG, "onBind");
        String action = intent.getAction();
        if (action.equals(BlaubotService.ACTION_BIND)) {
            return localBinder;
            // } else if (action.equals(BlaubotService.ACTION_LOG)) {
            //    Log.v(TAG, "onBind for Log");
            //    return messenger.getBinder();
            // we do not provide messageing in this small example
            // you might want to
        } else {
            Log.e(TAG, "onBind only defined for ACTION_BIND"); // or ACTION_LOG ");
            Log.e(TAG, "       did you want to call startService? ");
            return null;
        }
    }

    @Override
    public void onDestroy() {
        //Log.v(TAG, "onDestroy");
        if (blaubot != null) {
            shutdownBlaubotAndroid(blaubot);
            blaubot = null;
        }

        super.onDestroy();
    }

    private IBlaubotMessageListener generateMessageListenerBlaubotReceive() {
        IBlaubotMessageListener ibml  = new IBlaubotMessageListener() {
            @Override
            public void onMessage(BlaubotMessage blaubotMessage) {
                short channelId = blaubotMessage.getChannelId();
                String stringMsg = new String(blaubotMessage.getPayload());
                //Log.v(TAG, "  blaubotService receives message: " + stringMsg + " on channel: " + channelId);
                try {
                    JSONObject msg = new JSONObject(stringMsg);
                    if (!msg.has(MSGKEY_DEVICE_ID)) {
                        Log.e(TAG, "BlaubotReceiveMessageListener: no " + MSGKEY_DEVICE_ID + ", ignoring");
                        return;
                    }
                    String deviceId = msg.getString(MSGKEY_DEVICE_ID);
                    String messageId = msg.getString(MSGKEY_MESSAGE_ID);
                    short messageIdShort = 0;
                    String userName = msg.getString(MSGKEY_USER_NAME);
                    String userRole = msg.getString(MSGKEY_USER_ROLE);
                    short userRoleShort = 0;
                    String text = msg.getString(MSGKEY_PAYLOAD);
                    String prediction = msg.getString(MSGKEY_PREDICTION);
                    String rotation = msg.getString(MSGKEY_ROTATION);
                    String timestamp = msg.getString(MSGKEY_TIMESTAMP);
                    boolean called = Boolean.parseBoolean(msg.getString(MSGKEY_CALLED));
                    boolean is_mmol = Boolean.parseBoolean(msg.getString(MSGKEY_MMOL));
                    //Log.v(TAG, "Blaubot received message from " + m_userName + " with role " + m_userRole + " and text: " + text );
                    remoteLog("received: " + MSGKEY_DEVICE_ID + "=" + deviceId + ", " + MSGKEY_PAYLOAD + "=" + text);

                    // Parse the numbers
                    try {
                        messageIdShort = Short.parseShort(messageId);
                        userRoleShort = Short.parseShort(userRole);
                    } catch (NumberFormatException e){
                        e.printStackTrace();
                    }
                    // here you would typically call a setSomething of a model
                    // or store content in a content provider or whatever
                    // we delegate to listener
                    for (BlaubotMessageListener listener : blaubotMessageListeners) {
                        listener.onMessage(deviceId, channelId, messageIdShort, userName, userRoleShort, text, prediction, rotation, Long.valueOf(timestamp), called, is_mmol);
                    }

                    // Sending messages on the status channel when devices are connecting
                    if(channelId == CHANNEL_ID_STATUS){
                        if(messageIdShort == MSG_ID_CONNECTED){
                            // Remember the new connected device
                            addUserToDeviceMap(deviceId, userName, userRoleShort);
                            // Send a message on the channel, that all connected devices know, which users are online
                            sendMessageOnline();
                        }
                        if(messageIdShort == MSG_ID_ONLINE){
                            // Remember the online device
                            addUserToDeviceMap(deviceId, userName, userRoleShort);
//                            // Inform the listening devices about the device which has joined the blaubot network
//                            for (BlaubotMessageListener blaubotMessageListener : blaubotMessageListeners) {
//                                blaubotMessageListener.onDeviceJoined(userName, userRoleShort);
//                            }
                        }
                        if(messageIdShort == MSG_ID_DISCONNECTED){
                            // Remove the device which has left
                            removeUserFromDeviceMap(deviceId);
//                            // Inform the listening devices about the device which has left the blaubot network
//                            for (BlaubotMessageListener blaubotMessageListener : blaubotMessageListeners) {
//                                blaubotMessageListener.onDeviceLeft(userName, userRoleShort);
//                            }
                        }
                        if(messageIdShort == 0){
                            //ping
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "blaubot receiver, JSONExpection while receiving" + e.getMessage());
                }
            }
        };
        return ibml;
    }

    private final ILifecycleListener lifecycleListener = new ILifecycleListener() {
        @Override
        public void onConnected() {
            remoteLog("onConnected, subscribing");
            // Remember the own connected device
            //addUserToDeviceMap(m_userName, m_userRole);

            localDeviceName = blaubot.getOwnDevice().getUniqueDeviceID();
            shortLocalDeviceName = localDeviceName.substring(0, 8);

            addUserToDeviceMap(shortLocalDeviceName, m_userName, m_userRole);

            //remoteLog(String.format("onConnected: id=%s", localDeviceName));
            remoteLog("BLAUBOT LIFECYCLE onConnected " + m_userName);

            // Subscribe to channels
            channelStatus.subscribe(channelStatusReceiveListener);
            channelSensorData.subscribe(channelSensorDataReceiveListener);
            channelEmergency.subscribe(channelEmergencyReceiveListener);

            // Inform the listening devices about the status
            for (BlaubotMessageListener blaubotMessageListener : blaubotMessageListeners) {
                blaubotMessageListener.onBlaubotStatus(true, shortLocalDeviceName);
            }

//            // Inform the listening devices that the own device has joined the blaubot network
//            for (BlaubotMessageListener blaubotMessageListener : blaubotMessageListeners) {
//                blaubotMessageListener.onDeviceJoined(m_userName, m_userRole);
//            }

            // Send a message on the channel, that all connected devices know, which user has connected
            sendMessageConnected();

            // Start to ping all other devices
            if(!blaubotPing.wasStarted()){
                blaubotPingThread.start();
            }
        }

        @Override
        public void onDisconnected() {
            //remoteLog("onDisconnected");
            remoteLog("BLAUBOT LIFECYCLE onDisconnected " + m_userName);

            for (BlaubotMessageListener blaubotMessageListener : blaubotMessageListeners) {
                blaubotMessageListener.onBlaubotStatus(false, null);
            }

            // Send a message on the channel, that all connected devices know, which user has left
            sendMessageDisconnected();

            blaubotPing.pause();
        }

        @Override
        public void onDeviceJoined(IBlaubotDevice blaubotDevice) {
            if (blaubotDevice == null) {
                Log.e(TAG, "onDeviceJoined with empty Blaubotdevice?");
                return;
            }
            String name = blaubotDevice.getReadableName();
            //remoteLog("onDeviceJoined " + name);
            remoteLog("BLAUBOT LIFECYCLE onDeviceJoined " + name + " on device of " + m_userName);

            // Send a message on the channel, that all connected devices know, which users are online
            sendMessageOnline();
        }

        @Override
        public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
            String name = "null";
            if (blaubotDevice != null) {
                name =  blaubotDevice.getReadableName();
            }
            //remoteLog("onDeviceLeft " + name);
            remoteLog("BLAUBOT LIFECYCLE onDeviceLeft " + name + " on device of " + m_userName);


            String disconnectedDevice = blaubotDevice.getUniqueDeviceID().substring(0, 8);
            if(connected_devices_hash_map.containsKey(disconnectedDevice)){
                connected_devices_hash_map.remove(disconnectedDevice);
                // Notify all listeners
                for (BlaubotMessageListener listener : blaubotMessageListeners) {
                    listener.onDeviceMapChanged(connected_devices_hash_map);
                }
            }

            // Send a message on the channel, that all connected devices know, which user has left
            sendMessageDisconnected();
        }

        @Override
        public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince) {
            String name = "null";
            if (newPrince != null) {
                name =  newPrince.getReadableName();
            }
            //remoteLog("onPrinceDeviceChanged " + name);
            remoteLog("BLAUBOT LIFECYCLE onPrinceDeviceChanged " + name + " on device of " + m_userName);

            // Send a message on the channel, that all connected devices know, which users are online
            sendMessageOnline();
        }

        @Override
        public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {
            String name = "null";
            if (newKing != null) {
                name =  newKing.getReadableName();
            }
            //remoteLog("onKingDeviceChanged " + name);
            remoteLog("BLAUBOT LIFECYCLE onKingDeviceChanged " + name + " on device of " + m_userName);

            // Send a message on the channel, that all connected devices know, which users are online
            sendMessageOnline();
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Log.v(TAG, "onStartCommand");
        String action;
        if (intent != null) {
            action = intent.getAction();
            if (intent.hasExtra(BlaubotService.EXTRA_BLAUBOT_TYPE)) {
                blaubotType = intent.getStringExtra(BlaubotService.EXTRA_BLAUBOT_TYPE);
            } else {
                blaubotType = BLAUBOT_WIFI;
            }
        } else {
            Log.w(TAG, "upps, restart");
            action = ACTION_START;
        }
        if (action == null) {
            Log.w(TAG, "  action=null, nothing further to do");
            return START_STICKY;
        }
        if (action.equals(ACTION_STOP)) {
            remoteLog("  stopping");

            // Send a message on the channel, that all connected devices know, which users has left
            sendMessageDisconnected();

            // Invalidate the hashmap of connected devices
            connected_devices_hash_map.clear();

//            // Inform the listening devices that the own device has left the blaubot network
//            for (BlaubotMessageListener blaubotMessageListener : blaubotMessageListeners) {
//                blaubotMessageListener.onDeviceLeft(m_userName, m_userRole);
//            }

            if (blaubot != null) {
                shutdownBlaubotAndroid(blaubot);
            }
            // whatever else needs to be done here
            return START_NOT_STICKY;
        }
        if (!action.equals(ACTION_START)) {
            Log.e(TAG, "  unknown action " + action);
            return START_NOT_STICKY;
        }
        // we start!
        remoteLog("  starting");
        // whatever else needs to be done on start may be done here

        //Log.v(TAG, "  starting Blaubot");
        if (blaubotType.equals(BlaubotService.BLAUBOT_WIFI)) {
            startBlaubotAndroid(true);
        } else {
            startBlaubotAndroid(false);
        }
        return START_STICKY;
    }

    // ok, using sockets and as beacon
    private BlaubotAndroid createWifiBlaubot() {
        IBlaubotDevice ownDevice = new BlaubotDevice(UUID.randomUUID().toString());
        InetAddress ownInetAddress = BlaubotHelper.getWifiInetAddress(this, Inet4Address.class);
        if (ownInetAddress == null) {
            Log.e(TAG, "createWifiBlaubot: no own InetAddress?");
            return null;
        }
        //Log.v(TAG, "ownInetAddress=" + ownInetAddress);
        for (BlaubotMessageListener blaubotMessageListener : blaubotMessageListeners) {
            blaubotMessageListener.onInetAddress(ownInetAddress.getHostAddress());
        }
        BlaubotEthernetAdapter ethernetAdapter = new BlaubotEthernetAdapter(
                ownDevice, WIFI_ACCEPTOR_PORT, ownInetAddress);
        IBlaubotBeacon beacon = new BlaubotEthernetMulticastBeacon(
                WIFI_BEACON_PORT, WIFI_BEACON_BROADCAST_PORT);
        BlaubotAndroid blaubotAndroid = BlaubotAndroidFactory.createBlaubot(
                APP_UUID,
                ownDevice,
                ethernetAdapter,
                beacon);
        return blaubotAndroid;
    }

    private boolean startBlaubotAndroid(boolean wifi) {
        //Log.v(TAG, "startBlaubotAndroid wifi=" + wifi);
        if (wifi) {
            blaubot = createWifiBlaubot();
            if (blaubot == null) {
                remoteLog("Wifi is most likely not enabled, give up");
                return false;
            }
        } else {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                remoteLog("Bluetooth is most likely not enabled, give up");
                return false;
            }
            blaubot = BlaubotAndroidFactory.createBluetoothBlaubot(APP_UUID);
        }
        blaubot.registerReceivers(this);
        blaubot.setContext(this);
        blaubot.startBlaubot();
        channelStatus = blaubot.createChannel(CHANNEL_ID_STATUS);
        channelSensorData = blaubot.createChannel(CHANNEL_ID_SENSOR_DATA);
        channelEmergency = blaubot.createChannel(CHANNEL_ID_EMERGENCY);
        channelStatusReceiveListener = generateMessageListenerBlaubotReceive();
        channelSensorDataReceiveListener = generateMessageListenerBlaubotReceive();
        channelEmergencyReceiveListener = generateMessageListenerBlaubotReceive();
        blaubot.addLifecycleListener(lifecycleListener);
        // Blaubot Ping
        blaubotPing = new BlaubotPing();
        blaubotPingThread = new Thread(blaubotPing);
        return true;
    }

    private void shutdownBlaubotAndroid(BlaubotAndroid ba) {
        //Log.v(TAG, "shutdownBlaubotAndroid");
        try {
            if (channelStatus != null  && channelStatusReceiveListener != null) {
                channelStatus.removeMessageListener(channelStatusReceiveListener);
            }
            if(channelSensorData != null && channelSensorDataReceiveListener != null){
                channelSensorData.removeMessageListener(channelSensorDataReceiveListener);
            }
            if(channelEmergency != null && channelEmergencyReceiveListener != null){
                channelEmergency.removeMessageListener(channelEmergencyReceiveListener);
            }
            if (ba != null) {
                ba.removeLifecycleListener(lifecycleListener);
                ba.unregisterReceivers(this);
                ba.stopBlaubot();
                ba.close();
                for (BlaubotMessageListener blaubotMessageListener : blaubotMessageListeners) {
                    blaubotMessageListener.onBlaubotStatus(false, null);
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "  ignoring IOException on Blaubot close: " + e.getMessage());
        }
    }

    private void remoteLog(String line) {
        //Log.v(TAG, "remoteLog: " + line);
        for (BlaubotMessageListener listener : blaubotMessageListeners) {
            //listener.onLogMessage(line);
        }
    }

    public void setUserName(String m_userName){
        this.m_userName = m_userName;
    }

    public void setUserRole(Short m_userRole) { this.m_userRole = m_userRole; }

    private void addUserToDeviceMap(String uuid, String userName, short userRole){
        if(!connected_devices_hash_map.containsKey(uuid)){
            connected_devices_hash_map.put(uuid, userName+": "+userRole);
            Log.v(TAG, "BLAUBOT: " + uuid + " added to list: " + connected_devices_hash_map.toString());
            // Notify all listeners
            for (BlaubotMessageListener listener : blaubotMessageListeners) {
                listener.onDeviceMapChanged(connected_devices_hash_map);
            }
        }
    }

    private void removeUserFromDeviceMap(String uuid){
        if(connected_devices_hash_map.containsKey(uuid)){
            connected_devices_hash_map.remove(uuid);
            Log.v(TAG, "BLAUBOT: " + uuid + " removed from list: " + connected_devices_hash_map.toString());
            // Notify all listeners
            for (BlaubotMessageListener listener : blaubotMessageListeners) {
                listener.onDeviceMapChanged(connected_devices_hash_map);
            }
        }
    }


    // ---------------------------------------------------------------------------------------------
    // Ping thread to ping all blaubot devices in a time interval of 10s
    // ---------------------------------------------------------------------------------------------

    private class BlaubotPing implements Runnable {
        private volatile boolean running = true;
        private volatile boolean paused = false;
        private volatile boolean wasStarted = false;
        private long millisToSleep = 10000;

        public void terminate () {
            running = false;
        }

        public void pause() {
            paused = true;
        }

        public synchronized void resume() {
            notify();
        }

        public boolean wasStarted(){
            return this.wasStarted;
        }

        public void reset(){
            if(paused=true){
                paused=false;
                resume();
            }
        }

        @Override
        public void run() {
            wasStarted = true;
            while(running){
                if(!paused){
                    sendMessage(CHANNEL_ID_STATUS, (short)0, m_userName, m_userRole, "ping", "", "", System.currentTimeMillis(), false, false);
                    try {
                        Thread.sleep(millisToSleep);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        running = false;
                    }
                } else {
                    synchronized (this) {
                        // Wait for resume to be called
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        paused = false;
                    }
                }
            }
        }
    }


    private void sendMessageDisconnected(){
        sendMessage(CHANNEL_ID_STATUS, MSG_ID_DISCONNECTED, m_userName, m_userRole, "DISCONNECTED", "", "", System.currentTimeMillis(), false, false);
    }
    private void sendMessageConnected(){
        sendMessage(CHANNEL_ID_STATUS, MSG_ID_CONNECTED, m_userName, m_userRole, "HELLO", "", "", System.currentTimeMillis(), false, false);
    }
    private void sendMessageOnline(){
        sendMessage(CHANNEL_ID_STATUS, MSG_ID_ONLINE, m_userName, m_userRole, "I'M ONLINE", "", "", System.currentTimeMillis(), false, false);
    }

}