package de.pbma.pma.sensorapp2.SensorApp;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Chris on 27.05.2018.
 */

public class ShakeDetectorService extends Service implements ShakeDetector.OnShakeListener  {

    private String TAG = getClass().getSimpleName();
    private ShakeDetector shakeDetector;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private IBinder binder = new ShakeDetectorBinder();
    private BlaubotService blaubotService;
    private AtomicBoolean blaubotServiceBound;
    private String m_userName = "";

    // ---------------------------------------------------------------------------------------------
    // Service Methods
    // ---------------------------------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        // Get the sensor tools
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        blaubotServiceBound = new AtomicBoolean(false);

        // Create the shake detector class
        shakeDetector = new ShakeDetector();
        shakeDetector.setOnShakeListener(new ShakeDetector.OnShakeListener() {
            @Override
            public void onShake(int count) {
                handleShakingEvent(count);
            }
        });

        // Register the shake detecor class on sensor manager
        sensorManager.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up if necessary - on destroy called from the system
        // Stop shaker
        Log.v(TAG, "Destroying Shaking service");
        stopBlaubotService();
        unbindBlaubotService();
        // Unregister the shake detecor class on sensor manager
        sensorManager.unregisterListener(shakeDetector);
    }

    public class ShakeDetectorBinder extends Binder
    {
        public ShakeDetectorService getService() {
            return ShakeDetectorService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "Binding Shaking service");
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "Starting Shaking service");
        // Start shake detector
        super.onStartCommand(intent, flags, startId);
        bindBlaubotService();
        return START_STICKY;    // Service runs in background
    }

    // ---------------------------------------------------------------------------------------------
    // Shaker functionalities
    // ---------------------------------------------------------------------------------------------
    @Override
    public void onShake(int count) {
        Log.v(TAG, "Shaking service has detected a shake");
    }

    private void handleShakingEvent(int count){
        blaubotService.sendMessage(BlaubotService.CHANNEL_ID_EMERGENCY, BlaubotService.MSG_ID_EMERGENCY_CALL, m_userName, SensorAppHelpers.USER_ROLE_SHAKE_DETECTOR, "Please HELP !!", "", "", System.currentTimeMillis(), true, false);
        Log.v(TAG, "Handle shaking event");
    }

    // ---------------------------------------------------------------------------------------------
    // Blaubot methods
    // ---------------------------------------------------------------------------------------------
    private ServiceConnection blaubotServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.v(TAG, "onServiceConnected");
            blaubotService = ((BlaubotService.LocalBinder) service).getBlaubotService();
            // Set user role
            //blaubotService.setUserRole(SensorAppHelpers.USER_ROLE_CARETAKER);
            // register listeners
            //blaubotService.registerSendDataListener(blaubotMessageListener);
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            // unintentionally disconnected
            Log.v(TAG, "onServiceDisconnected");
            //blaubotService.setUserRole((short)0);
            unbindBlaubotService(); // cleanup
        }
    };

    private void bindBlaubotService() {
        Log.v(TAG, "bindBlaubotService");
        Intent intent = new Intent(this, BlaubotService.class);
        // intent.putExtra(getString(R.string.key_role), ROLE);
        intent.setAction(BlaubotService.ACTION_BIND);
        boolean result = bindService(intent, blaubotServiceConnection, Context.BIND_AUTO_CREATE);
        blaubotServiceBound.set(result);
        if (!result) {
            Log.w(TAG, "could not bind service, will not be bound");
        }
        //trying_to_bind_blaubot_service = false;
    }

    private void stopBlaubotService(){
        Intent stopBlaubotIntent = new Intent(this, BlaubotService.class);
        stopBlaubotIntent.setAction(BlaubotService.ACTION_STOP);
        startService(stopBlaubotIntent);
    }

    private void unbindBlaubotService() {
        if (blaubotServiceBound.get()) {
            if (blaubotService != null) {
                // deregister listeners, if there are any
                //blaubotService.deregisterSendDataListener(blaubotMessageListener);
            }
            blaubotServiceBound.set(false);
            unbindService(blaubotServiceConnection);
        }
    }

    public void setUserName(String userName){
        this.m_userName = userName;
    }
}