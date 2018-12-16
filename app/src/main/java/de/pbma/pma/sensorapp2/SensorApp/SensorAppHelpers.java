package de.pbma.pma.sensorapp2.SensorApp;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Created by Chris on 27.05.2018.
 */

public class SensorAppHelpers {
    final static String ACTION_ON_DEVICE_JOINED = "onDeviceJoined";
    final static String ACTION_ON_DEVICE_LEFT = "onDeviceLeft";
    final static String ACTION_ON_DEVICE_CONNECTED = "onDeviceConnected";

    public final static short USER_ROLE_SENSOR_SIMULATOR = 1;
    public final static short USER_ROLE_PATIENT = 2;
    public final static short USER_ROLE_CARETAKER = 3;
    public final static short USER_ROLE_SHAKE_DETECTOR = 4;

    public static String getUserRoleString(short userRole){
        if(userRole == USER_ROLE_SENSOR_SIMULATOR){
            return "Simulator";
        }
        if(userRole == USER_ROLE_PATIENT){
            return "Patient";
        }
        if(userRole == USER_ROLE_CARETAKER){
            return "Caretaker";
        }
        return "None";
    }

    public static ArrayList<String> connectedDevicesHashmapToArrayList(HashMap<String, Short> connectedDevicesHashMap){
        ArrayList<String> connectedDevicesArrayList = new ArrayList<String>();
        Set<Map.Entry<String, Short>> entries = connectedDevicesHashMap.entrySet();
        for (Map.Entry<String, Short> entry: entries) {
            connectedDevicesArrayList.add(entry.getKey()+"::"+getUserRoleString(entry.getValue()));
        }
        return connectedDevicesArrayList;
    }

    public static String getTimeOfDay(long timestamp){
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        Date date = new Date(timestamp);
        DateFormat formatter = new SimpleDateFormat("HH:mm:ss");
        String timeOfDay = formatter.format(date);
        return timeOfDay;
    }
}
