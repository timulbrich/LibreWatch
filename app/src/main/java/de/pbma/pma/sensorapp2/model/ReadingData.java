package de.pbma.pma.sensorapp2.model;

import android.service.media.MediaBrowserService;
import android.util.Log;

import org.apache.commons.collections4.list.TreeList;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

//import static de.pbma.pma.sensorapp2.OpenLibre.realmConfigProcessedData;
import static de.pbma.pma.sensorapp2.model.SensorData.maxSensorAgeInMinutes;
import static de.pbma.pma.sensorapp2.model.SensorData.minSensorAgeInMinutes;
import static java.lang.Math.min;

public class ReadingData {
    public static final String ID = "id";
    public static final String SENSOR = "sensor";
    public static final String SENSOR_AGE_IN_MINUTES = "sensorAgeInMinutes";
    public static final String DATE = "date";
    public static final String TIMEZONE_OFFSET_IN_MINUTES = "timezoneOffsetInMinutes";
    public static final String TREND = "trend";
    public static final String HISTORY = "history";


    public static final int numHistoryValues = 32;
    public static final int historyIntervalInMinutes = 15;
    public static final int numTrendValues = 16;

    private String id;
    private SensorData sensor;
    private int sensorAgeInMinutes = -1;
    private long date = -1;
    private int timezoneOffsetInMinutes;
    private List<GlucoseData> trend = new ArrayList<>();
    private List<GlucoseData> history = new ArrayList<>();

    public ReadingData() {}
    public ReadingData(RawTagData rawTagData) {
        id = rawTagData.getId();
        date = rawTagData.getDate();
        timezoneOffsetInMinutes = rawTagData.getTimezoneOffsetInMinutes();
        sensorAgeInMinutes = rawTagData.getSensorAgeInMinutes();

        sensor = new SensorData(rawTagData);

        // check if sensor is of valid age
        if (sensorAgeInMinutes <= minSensorAgeInMinutes || sensorAgeInMinutes > maxSensorAgeInMinutes) {
            return;
        }

        int indexTrend = rawTagData.getIndexTrend();

        int mostRecentHistoryAgeInMinutes = 3 + (sensorAgeInMinutes - 3) % historyIntervalInMinutes;

        // read trend values from ring buffer, starting at indexTrend (bytes 28-123)
        for (int counter = 0; counter < numTrendValues; counter++) {
            int index = (indexTrend + counter) % numTrendValues;

            int glucoseLevelRaw = rawTagData.getTrendValue(index);
            // skip zero values if the sensor has not filled the ring buffer yet completely
            if (glucoseLevelRaw > 0) {
                int dataAgeInMinutes = numTrendValues - counter;
                int ageInSensorMinutes = sensorAgeInMinutes - dataAgeInMinutes;
                long dataDate = date - 15*1000*60 + counter*1000*60;   //trend has a value for each of last 15 minutes
                trend.add(new GlucoseData(sensor, ageInSensorMinutes, timezoneOffsetInMinutes, glucoseLevelRaw, true, dataDate));
            }
        }

        int indexHistory = rawTagData.getIndexHistory();

        ArrayList<Integer> glucoseLevels = new ArrayList<>();
        ArrayList<Integer> ageInSensorMinutesList = new ArrayList<>();

        // read history values from ring buffer, starting at indexHistory (bytes 124-315)
        for (int counter = 0; counter < numHistoryValues; counter++) {
            int index = (indexHistory + counter) % numHistoryValues;

            int glucoseLevelRaw = rawTagData.getHistoryValue(index);
            // skip zero values if the sensor has not filled the ring buffer yet completely
            if (glucoseLevelRaw > 0) {
                int dataAgeInMinutes = mostRecentHistoryAgeInMinutes + (numHistoryValues - (counter + 1)) * historyIntervalInMinutes;
                int ageInSensorMinutes = sensorAgeInMinutes - dataAgeInMinutes;

                // skip the first hour of sensor data as it is faulty
                if (ageInSensorMinutes > minSensorAgeInMinutes) {
                    glucoseLevels.add(glucoseLevelRaw);
                    ageInSensorMinutesList.add(ageInSensorMinutes);
                }
            }
        }

        // check if there were actually any valid data points
        if (ageInSensorMinutesList.isEmpty()) {
            return;
        }

        // create history data point list
        for (int i = 0; i < glucoseLevels.size(); i++) {
            int glucoseLevelRaw = glucoseLevels.get(i);
            int ageInSensorMinutes = ageInSensorMinutesList.get(i);
            long dataDate = date - 1000*60*60*8 + 5*1000*60 + i*1000*60*historyIntervalInMinutes; //history has a value every 15 minutes

            GlucoseData glucoseData = makeGlucoseData(glucoseLevelRaw, ageInSensorMinutes, dataDate);
            if(glucoseData == null) {
                return;
            }
            history.add(glucoseData);
        }

    }

    private GlucoseData makeGlucoseData(int glucoseLevelRaw, int ageInSensorMinutes, long dataDate) {
        return new GlucoseData(sensor, ageInSensorMinutes, timezoneOffsetInMinutes, glucoseLevelRaw, false, dataDate);
    }

    public String getId() {
        return id;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public int getTimezoneOffsetInMinutes() {
        return timezoneOffsetInMinutes;
    }

    public void setTimezoneOffsetInMinutes(int timezoneOffsetInMinutes) {
        this.timezoneOffsetInMinutes = timezoneOffsetInMinutes;
    }

    public List<GlucoseData> getTrend() {
        return trend;
    }

    public List<GlucoseData> getHistory() {
        return history;
    }

    public int getSensorAgeInMinutes() {
        return sensorAgeInMinutes;
    }

    public SensorData getSensor() {
        return sensor;
    }

}
