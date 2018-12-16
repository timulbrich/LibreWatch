package de.pbma.pma.sensorapp2.SensorApp;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Base64;
import android.util.Log;
import android.util.Xml;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.io.UnsupportedEncodingException;
import java.sql.Array;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import de.pbma.pma.sensorapp2.R;
import de.pbma.pma.sensorapp2.Settings;
import de.pbma.pma.sensorapp2.model.GlucoseData;
import de.pbma.pma.sensorapp2.model.PredictionData;
import de.pbma.pma.sensorapp2.model.ReadingData;
import de.pbma.pma.sensorapp2.NfcVReaderTask;
import io.netty.handler.codec.base64.Base64Encoder;

import static de.pbma.pma.sensorapp2.SensorApp.BlaubotService.CHANNEL_ID_STATUS;
import static de.pbma.pma.sensorapp2.SensorApp.BlaubotService.MSG_ID_DISCONNECTED;
import static de.pbma.pma.sensorapp2.SensorApp.SensorAppHelpers.USER_ROLE_CARETAKER;
import static de.pbma.pma.sensorapp2.SensorApp.SensorAppHelpers.USER_ROLE_PATIENT;
import static de.pbma.pma.sensorapp2.SensorApp.SensorAppHelpers.USER_ROLE_SENSOR_SIMULATOR;
import static de.pbma.pma.sensorapp2.SensorApp.SensorAppHelpers.getTimeOfDay;
import static de.pbma.pma.sensorapp2.SensorApp.SensorAppHelpers.getUserRoleString;
import static de.pbma.pma.sensorapp2.model.AlgorithmUtil.TREND_UP_DOWN_LIMIT;
import static de.pbma.pma.sensorapp2.model.AlgorithmUtil.bytesToHexString;
import static de.pbma.pma.sensorapp2.model.AlgorithmUtil.mFormatDate;
import static de.pbma.pma.sensorapp2.model.AlgorithmUtil.mFormatDateTime;
import static de.pbma.pma.sensorapp2.model.AlgorithmUtil.mFormatTimeShort;
import static de.pbma.pma.sensorapp2.model.GlucoseData.convertGlucoseMGDLToDisplayUnit;
import static de.pbma.pma.sensorapp2.model.GlucoseData.getDisplayUnit;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class MainActivity extends Activity implements OnChartValueSelectedListener, OnChartGestureListener{

    private final String TAG = getClass().getSimpleName();

    // Widgets
    public static TextView tv_connection_status = null;
    private ListView lv_connected_devices = null;
    private TextView tv_info_box = null;
    private Button btn_emergency_call = null;
    private ProgressBar pb_timeline = null;
    private LinearLayout ll_simulator = null;
    private EditText et_sim_readvalue = null;
    private EditText et_sim_predictvalue = null;
    private Button btn_sim_confirm = null;
    private TextView tv_glucose_prediction = null;
    private TextView tv_glucose_read = null;
    private ImageView iv_glucose_arrow = null;
    private TextView tv_plot_title = null;
    private LinearLayout ll_caretaker_warning = null;
    private TextView tv_caretaker_warning = null;
    private Button btn_caretaker_warning_yes = null;
    private Button btn_caretaker_warning_no = null;
    private ListView lv_caretaker_info = null;
    private TextView tv_pat_sim_infobox = null;
    private Button btn_screener = null;
    private WebView webView = null;
    final static String CAPABILITY_WATCH_APP = "watch";
    Set<Node> nodes;

    // Settings
    public static boolean NFC_USE_MULTI_BLOCK_READ = true;
    public static boolean GLUCOSE_UNIT_IS_MMOL = false;
    public static float GLUCOSE_TARGET_MIN;
    public static float GLUCOSE_TARGET_MAX;

    // Lists
    public static ArrayAdapter<String> connected_devices_adapter = null;
    public static List<String> connected_devices_list = new ArrayList<String>();

    private ArrayAdapter infobox_adapter = null;
    private List<String> infobox_list = new ArrayList<String>();

    // Blaubot
    public static BlaubotService blaubotService;
    public static AtomicBoolean blaubotServiceBound;
    private boolean wlan_joined = false;

    // Connection info
    public static boolean blaubot_connected = false;
    private String ownHostAddress = "";
    private HashMap<String, String> connected_devices_hash_map = new HashMap<String, String>();

    // Shared preferences
    private SharedPreferences appPreferences = null;
    private SharedPreferences.Editor editor = null;
    private String m_userName = "";
    private short m_userRole = 0;
    private boolean firstRun = false;

    // Emergency calls
    private ShakeDetectorService shakeDetectorService = null;
    private boolean shakeDetectorServiceBound = false;
    private static String caretaker_info = new String();

    //Notifications
    private Notification.Builder nBuilder = null;
    private NotificationManager nManager = null;

    // Progress bar updater
    private ProgressBarUpdater progressBarUpdater = null;
    private Thread progressBarUpdaterThread = null;

    //Sensor scan
    public static float PREDICTION_VALUE = 0;
    public static float READ_VALUE = 0;
    public static double ROTATION_VALUE = 0;
    public static long TIME_VALUE = 0;
    private static final String DEBUG_SENSOR_TAG_ID = "e007a00000111111";
    private static final int PENDING_INTENT_TECH_DISCOVERED = 1;
    public long mLastScanTime = 0;
    private NfcAdapter mNfcAdapter;
    private boolean mContinuousSensorReadingFlag = false;
    private Tag mLastNfcTag;
    public static List<Date> dateList = new ArrayList<>();

    private final static int NUM_PLOT_COLORS = 3;
    private final static int[][] PLOT_COLORS = new int[][] {
            {Color.BLUE, Color.BLUE},
            {Color.MAGENTA, Color.RED},
            {Color.CYAN, Color.GREEN},
    };
    private static int mPlotColorIndex = 0;
    private final static int maxZoomFactor = 12;

    LineChart mPlot;
    private long mFirstDate = -1;
    private Timer mUpdatePlotTitleTimer;
    private TimerTask mUpdatePlotTitleTask = null;
    private DateTimeMarkerView mDateTimeMarkerView;
    boolean isZoomedToTrend = false;

    // ---------------------------------------------------------------------------------------------
    // Android Lifecycle methods
    // ---------------------------------------------------------------------------------------------


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sensor_main);

        // Initialize variables
        blaubotServiceBound = new AtomicBoolean(false);

        // Get the widgets
        tv_connection_status = (TextView)findViewById(R.id.sapp_tv_connection_status);
        lv_connected_devices = (ListView)findViewById(R.id.sapp_lv_connected_devices);
        tv_info_box = (TextView)findViewById(R.id.sapp_tv_infobox);
        btn_emergency_call = (Button)findViewById(R.id.sapp_btn_emergency_call);
        pb_timeline = (ProgressBar)findViewById(R.id.pb_timeline);
        ll_simulator = (LinearLayout)findViewById(R.id.ll_simulator);
        et_sim_readvalue = (EditText)findViewById(R.id.et_simulator_readvalue);
        et_sim_predictvalue = (EditText)findViewById(R.id.et_simulator_predictvalue);
        btn_sim_confirm = (Button)findViewById(R.id.btn_simulator);
        tv_glucose_prediction = (TextView)findViewById(R.id.tv_glucose_prediction);
        tv_glucose_read = (TextView)findViewById(R.id.tv_glucose_current_value);
        iv_glucose_arrow = (ImageView)findViewById(R.id.iv_glucose_prediction);
        tv_plot_title = (TextView)findViewById(R.id.tv_plot_title);
        ll_caretaker_warning = (LinearLayout)findViewById(R.id.ll_caretaker_warning);
        tv_caretaker_warning = (TextView)findViewById(R.id.tv_caretaker_warning);
        btn_caretaker_warning_yes = (Button)findViewById(R.id.btn_caretaker_emergeny_yes);
        btn_caretaker_warning_no = (Button)findViewById(R.id.btn_caretaker_emergeny_no);
        lv_caretaker_info = (ListView)findViewById(R.id.lv_caretaker_info);
        tv_pat_sim_infobox = (TextView)findViewById(R.id.tv_pat_sim_infobox);
        btn_screener = (Button)findViewById(R.id.sapp_btn_screener);
        webView = (WebView) findViewById(R.id.webview);


        // Associate the listeners
        btn_emergency_call.setOnClickListener(btn_emergency_call_clicked);
        btn_sim_confirm.setOnClickListener(btn_send_sensor_data_clicked);
        btn_caretaker_warning_no.setOnClickListener(btn_warning_no_clicked);
        btn_caretaker_warning_yes.setOnClickListener(btn_warning_yes_clicked);
        btn_screener.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.getSettings().setJavaScriptEnabled(true);
                webView.setWebViewClient(new WebViewClient());
                webView.loadUrl("https://ethn.io/mob/68819");
                webView.setVisibility(View.VISIBLE);

            }
        });

        // Set the Adapters
        connected_devices_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, connected_devices_list){
            @Override
            public View getView(int position, View convertView, ViewGroup parent){
                // Get the current item from ListView
                View view = super.getView(position,convertView,parent);

                // Get the Layout Parameters for ListView Current Item View
                ViewGroup.LayoutParams params = view.getLayoutParams();

                // Set the height of the Item View
                params.height = 90;
                view.setLayoutParams(params);

                return view;
            }
        };
        lv_connected_devices.setAdapter(connected_devices_adapter);

        infobox_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, infobox_list){
            @Override
            public View getView(int position, View convertView, ViewGroup parent){
                // Get the current item from ListView
                View view = super.getView(position,convertView,parent);

                // Get the Layout Parameters for ListView Current Item View
                ViewGroup.LayoutParams params = view.getLayoutParams();

                // Set the height of the Item View
                params.height = 90;
                view.setLayoutParams(params);

                return view;
            }
        };
        lv_caretaker_info.setAdapter(infobox_adapter);

        // Create a new thread to update the progress bar
        progressBarUpdater = new ProgressBarUpdater();
        progressBarUpdater.setSecondsHundredPercent(60*5); //5 Minuten
        progressBarUpdaterThread = new Thread(progressBarUpdater);

        nBuilder = new Notification.Builder(this);
        nManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        this.registerReceiver(this.WifiStateChangedReceiver, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));

        startconfig();

    }

    private void startconfig(){

        // Get the shared preferences
        appPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());//getSharedPreferences(getResources().getString(R.string.shpref_app_preferences), Context.MODE_PRIVATE);
        editor = appPreferences.edit();
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).registerOnSharedPreferenceChangeListener(ospcl);

        firstRun = appPreferences.getBoolean(getString(R.string.key_first_run), true);

        // Get the user preferences
        GLUCOSE_UNIT_IS_MMOL = appPreferences.getBoolean(getString(R.string.pref_glucose_unit_is_mmol), GLUCOSE_UNIT_IS_MMOL);
        GLUCOSE_TARGET_MIN = Float.parseFloat(appPreferences.getString(getString(R.string.pref_glucose_target_min), Float.toString(GLUCOSE_TARGET_MIN)));
        GLUCOSE_TARGET_MAX = Float.parseFloat(appPreferences.getString(getString(R.string.pref_glucose_target_max), Float.toString(GLUCOSE_TARGET_MAX)));

        m_userName = appPreferences.getString(getString(R.string.key_user_name), "");

        if(appPreferences.getString(getString(R.string.key_start_role), "role").equals(
                SensorAppHelpers.getUserRoleString((short)2)))
            m_userRole = 2; //Patient
        else if(appPreferences.getString(getString(R.string.key_start_role), "role").equals(
                SensorAppHelpers.getUserRoleString((short)3)))
            m_userRole = 3; //Caretaker
        else
            m_userRole = 1; //Simulator

        tv_info_box.setText(Html.fromHtml(getString(R.string.name_hello) + " " + m_userName + "! You are registered as " +
                "<b>" + getUserRoleString(m_userRole) + "</b>"));
        ll_caretaker_warning.setVisibility(View.GONE);

        if(m_userRole == 2) {
            findViewById(R.id.sapp_cv_glucose_plot).setVisibility(View.VISIBLE);
            ll_simulator.setVisibility(View.GONE);
            btn_emergency_call.setVisibility(View.VISIBLE);
            lv_caretaker_info.setVisibility(View.GONE);
            tv_pat_sim_infobox.setVisibility(View.VISIBLE);
            startsensor();
        }

        else if(m_userRole == 3){
            findViewById(R.id.sapp_cv_glucose_plot).setVisibility(View.GONE);
            ll_simulator.setVisibility(View.GONE);
            btn_emergency_call.setVisibility(View.GONE);
            lv_caretaker_info.setVisibility(View.VISIBLE);
            tv_pat_sim_infobox.setVisibility(View.GONE);
        }

        else if(m_userRole == 1){ //SensorSimulator
            findViewById(R.id.sapp_cv_glucose_plot).setVisibility(View.GONE);
            ll_simulator.setVisibility(View.VISIBLE);
            btn_emergency_call.setVisibility(View.GONE);
            lv_caretaker_info.setVisibility(View.GONE);
            tv_pat_sim_infobox.setVisibility(View.GONE);
        }

        Task<CapabilityInfo> capabilityInfoTask = Wearable.getCapabilityClient(this.getApplicationContext())
                .getCapability(CAPABILITY_WATCH_APP, CapabilityClient.FILTER_REACHABLE);

        capabilityInfoTask.addOnCompleteListener(new OnCompleteListener<CapabilityInfo>() {
            @Override
            public void onComplete(Task<CapabilityInfo> task) {

                if (task.isSuccessful()) {
                    CapabilityInfo capabilityInfo = task.getResult();
                    if (capabilityInfo != null) {
                        nodes = capabilityInfo.getNodes();
                    }
                } else {
                    Log.d("TAG", "Capability request failed to return any results.");
                }

            }
        });

        /*byte[] bytes = new byte[2];
        bytes[0] = Byte.parseByte(String.valueOf(1));
        bytes[1] = Byte.parseByte(String.valueOf(2));
        Wearable.getMessageClient(getApplicationContext()).sendMessage(
                "phone", "/newScan", bytes);*/
        String a = "8.3;1;3";
        byte[] bytes = a.getBytes();
        String b = new String(bytes);

    }

    @Override
    protected void onStart() {
        //Log.v(TAG, "onStart");
        super.onStart();
    }

    @Override
    protected void onResume() {
        //Log.v(TAG, "onResume");
        super.onResume();
        bindBlaubotService();
        if (mNfcAdapter == null) {
            mNfcAdapter = ((NfcManager) this.getSystemService(Context.NFC_SERVICE)).getDefaultAdapter();
        }

        if (mNfcAdapter != null) {
            try {
                mNfcAdapter.isEnabled();
            } catch (NullPointerException e) {
                // Drop NullPointerException
            }

            PendingIntent pi = createPendingResult(PENDING_INTENT_TECH_DISCOVERED, new Intent(), 0);
            if (pi != null) {
                try {
                    mNfcAdapter.enableForegroundDispatch(this, pi,
                            new IntentFilter[] { new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED) },
                            new String[][] { new String[]{"android.nfc.tech.NfcV"} }
                    );
                } catch (NullPointerException e) {
                    // Drop NullPointerException
                }
            }
        }
        if(m_userRole == USER_ROLE_PATIENT)
            bindAndStartShakeDetectorService();
    }

    @Override
    protected void onPause() {
        //Log.v(TAG, "onPause");
        super.onPause();
        //unbindBlaubotService();  //Auskommentieren damit Verbindung bestehen bleibt
        if (mNfcAdapter != null) {
            try {
                // Disable foreground dispatch:
                mNfcAdapter.disableForegroundDispatch(this);
            } catch (NullPointerException e) {
                // Drop NullPointerException
            }
        }
    }

    @Override
    protected void onStop() {
        //Log.v(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        //Log.v(TAG, "onDestroy");
        super.onDestroy();

        // Stop Blaubot Service
        stopBlaubotService();

        // Unbind Blaubot Service
        unbindBlaubotService();

        // Stop and unbind the shake detector service if bound
        if(shakeDetectorServiceBound){
            unbindAndStopShakeDetectorService();
        }

        unregisterReceiver(this.WifiStateChangedReceiver);
    }


    SharedPreferences.OnSharedPreferenceChangeListener ospcl =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences settings, String key) {
                    leaveWlan();
                    if(key.equals("pref_glucose_unit_is_mmol")){
                        appPreferences.edit().putBoolean(getString(R.string.pref_glucose_unit_is_mmol), settings.getBoolean(
                                getString(R.string.pref_glucose_unit_is_mmol), false)).apply();
                    }
                    else if (key.equals("pref_glucose_target_min")) {
                        appPreferences.edit().putString(getString(R.string.pref_glucose_target_min), settings.getString(
                                getString(R.string.pref_glucose_target_min), "80")).apply();
                    }
                    else if(key.equals("pref_glucose_target_max")){
                        appPreferences.edit().putString(getString(R.string.pref_glucose_target_max), settings.getString(
                                getString(R.string.pref_glucose_target_max), "180")).apply();
                    }
                    else if (key.equals("key_user_name")) {
                        appPreferences.edit().putString(getString(R.string.key_user_name), settings.getString(
                                getString(R.string.key_user_name), "user")).apply();
                    }
                    else if (key.equals("key_start_role")) {
                        appPreferences.edit().putString(getString(R.string.key_start_role), settings.getString(
                                getString(R.string.key_start_role), "role")).apply();
                    }
                    startconfig();
                    if(wlan_joined)
                        joinWlan();


                }
            };



    // ---------------------------------------------------------------------------------------------
    // Associated listneers
    // ---------------------------------------------------------------------------------------------

    View.OnClickListener btn_send_sensor_data_clicked = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if(et_sim_readvalue.getText().toString().isEmpty() || et_sim_predictvalue.getText().toString().isEmpty()){
                Toast.makeText(getApplicationContext(), "Please fill in both values", Toast.LENGTH_SHORT).show();
                return;
            }

            String readvalue = et_sim_readvalue.getText().toString();
            String predictvalue = et_sim_predictvalue.getText().toString();
            float read_float = Float.parseFloat(readvalue);
            float predict_float = Float.parseFloat(predictvalue);
            float rotatevalue = (read_float - predict_float)*3;

            if(GLUCOSE_UNIT_IS_MMOL){
                read_float = read_float *18f;
                predict_float = predict_float *18f;
                rotatevalue = (read_float - predict_float)*3;
            }

            if(read_float != 0 && predict_float != 0){
                // Visualize the sensor value
                if(read_float < 71){
                    tv_glucose_read.setBackgroundColor(Color.CYAN);
                }
                if(read_float >= 71 && read_float <= 160) {
                    tv_glucose_read.setBackgroundColor(Color.GREEN);
                }
                if(read_float > 160 && read_float <= 240) {
                    tv_glucose_read.setBackgroundColor(Color.YELLOW);
                }
                if(read_float > 240){
                    tv_glucose_read.setBackgroundColor(Color.RED);
                }

                if(predict_float < 71){
                    tv_glucose_prediction.setBackgroundColor(Color.CYAN);
                }
                if(predict_float >= 71 && predict_float <= 160) {
                    tv_glucose_prediction.setBackgroundColor(Color.GREEN);
                }
                if(predict_float > 160 && predict_float <= 240) {
                    tv_glucose_prediction.setBackgroundColor(Color.YELLOW);
                }
                if(predict_float > 240){
                    tv_glucose_prediction.setBackgroundColor(Color.RED);
                }
            }
            long now = System.currentTimeMillis();
            String timestamp = mFormatDateTime.format(new Date(now));

            if(read_float<71 || predict_float<71)
                blaubotService.sendMessage(BlaubotService.CHANNEL_ID_EMERGENCY,
                        BlaubotService.MSG_ID_EMERGENCY_CALL, m_userName, m_userRole,
                        unitformat(read_float), unitformat(predict_float), "LOW", now, false, false);
            if(read_float>159 || predict_float>159)
                blaubotService.sendMessage(BlaubotService.CHANNEL_ID_EMERGENCY,
                        BlaubotService.MSG_ID_EMERGENCY_CALL, m_userName, m_userRole,
                        unitformat(read_float), unitformat(predict_float), "HIGH", now, false, false);
            if(read_float>240 || predict_float>240)
                blaubotService.sendMessage(BlaubotService.CHANNEL_ID_EMERGENCY,
                        BlaubotService.MSG_ID_EMERGENCY_CALL, m_userName, m_userRole,
                        unitformat(read_float), unitformat(predict_float), "HIGHER", now, false, false);

            ImageView iv_unit = (ImageView) findViewById(R.id.iv_unit);
            if(GLUCOSE_UNIT_IS_MMOL){
                read_float = read_float /18f;
                predict_float = predict_float /18f;
                iv_unit.setImageResource(R.drawable.ic_unit_mmoll);
            }
            else
                iv_unit.setImageResource(R.drawable.ic_unit_mgdl);

            tv_glucose_read.setText(unitformat(read_float));
            tv_glucose_prediction.setText(unitformat(predict_float));
            tv_plot_title.setText(timestamp);
            if(rotatevalue >= 90)
                rotatevalue = 90;
            else if(rotatevalue <= -90)
                rotatevalue = -90;
            iv_glucose_arrow.setRotation(rotatevalue);
            String rotation = String.valueOf(rotatevalue);
            readvalue = String.valueOf(read_float);
            predictvalue = String.valueOf(predict_float);

            if(!progressBarUpdater.wasStarted()){
                progressBarUpdaterThread.start();
            } else {
                progressBarUpdater.reset();
            }
            blaubotService.sendMessage(BlaubotService.CHANNEL_ID_SENSOR_DATA, BlaubotService.MSG_ID_SENSOR_DATA, m_userName, m_userRole,
                    readvalue, predictvalue, rotation, now, false, GLUCOSE_UNIT_IS_MMOL);
        }
    };


    View.OnClickListener btn_emergency_call_clicked = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            blaubotService.sendMessage(BlaubotService.CHANNEL_ID_EMERGENCY,
                    BlaubotService.MSG_ID_EMERGENCY_CALL, m_userName, m_userRole, "Please HELP !!","", "", System.currentTimeMillis(), true, GLUCOSE_UNIT_IS_MMOL);
        }
    };


    View.OnClickListener btn_warning_yes_clicked = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            ll_caretaker_warning.setVisibility(View.GONE);
            lv_caretaker_info.setVisibility(View.VISIBLE);
            nManager.cancelAll();
            String[] temp = infobox_list.get(0).trim().split(" ");
            String oldName = temp[1].split(":")[0];

            caretaker_info = getTimeOfDay(System.currentTimeMillis()) + " " + "You took care of " + oldName + "\n";

            if(infobox_list.isEmpty())
                infobox_list.add(caretaker_info.replace("\n", ""));
            else{
                for(int i=infobox_list.size(); i>0; i--){
                    if(i==infobox_list.size())
                        infobox_list.add(infobox_list.get(i-1));
                    else
                        infobox_list.set(i, infobox_list.get(i-1));
                }
                infobox_list.set(0, caretaker_info.replace("\n", ""));
            }
            infobox_adapter.notifyDataSetChanged();

            blaubotService.sendMessage(BlaubotService.CHANNEL_ID_EMERGENCY, BlaubotService.MSG_ID_EMERGENCY_CALL,
                    m_userName, m_userRole, "STOP","", "",
                    System.currentTimeMillis(), true, GLUCOSE_UNIT_IS_MMOL);
        }
    };

    View.OnClickListener btn_warning_no_clicked = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            ll_caretaker_warning.setVisibility(View.GONE);
            lv_caretaker_info.setVisibility(View.VISIBLE);
            nManager.cancelAll();
        }
    };


    // ---------------------------------------------------------------------------------------------
    // Broadcast Receiver that listens to WIFI events
    // ---------------------------------------------------------------------------------------------

    private BroadcastReceiver WifiStateChangedReceiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub

            int extraWifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE ,
                    WifiManager.WIFI_STATE_UNKNOWN);

            switch(extraWifiState){
                case WifiManager.WIFI_STATE_DISABLED:

                    if(infobox_list.isEmpty())
                        infobox_list.add(getTimeOfDay(System.currentTimeMillis()) + ": You disabled WiFi");
                    else{
                        for(int i=infobox_list.size(); i>0; i--){
                            if(i==infobox_list.size())
                                infobox_list.add(infobox_list.get(i-1));
                            else
                                infobox_list.set(i, infobox_list.get(i-1));

                        }
                        infobox_list.set(0, getTimeOfDay(System.currentTimeMillis()) + ": You disabled WiFi");
                    }
                    infobox_adapter.notifyDataSetChanged();

                    leaveWlan();
                    break;

                case WifiManager.WIFI_STATE_UNKNOWN:

                    if(infobox_list.isEmpty())
                        infobox_list.add(getTimeOfDay(System.currentTimeMillis()) + ": You may have lost WiFi Signal");
                    else{
                        for(int i=infobox_list.size(); i>0; i--){
                            if(i==infobox_list.size())
                                infobox_list.add(infobox_list.get(i-1));
                            else
                                infobox_list.set(i, infobox_list.get(i-1));
                        }
                        infobox_list.set(0, getTimeOfDay(System.currentTimeMillis()) + ": You may have lost WiFi Signal");
                    }
                    infobox_adapter.notifyDataSetChanged();

                    break;
            }
        }};



    // ---------------------------------------------------------------------------------------------
    // Blaubot methods
    // ---------------------------------------------------------------------------------------------

    public ServiceConnection blaubotServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //Log.v(TAG, "onServiceConnected");
            blaubotService = ((BlaubotService.LocalBinder) service).getBlaubotService();
            // register listeners
            blaubotService.registerBlaubotMessageListener(blaubotMessageListener);

            // Inform the blaubot Service about the user name
            blaubotService.setUserName(m_userName);
            blaubotService.setUserRole(m_userRole);
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            // unintentionally disconnected
            //Log.v(TAG, "onServiceDisconnected");
            unbindBlaubotService(); // cleanup
        }
    };

    Handler handler = new Handler();
    BlaubotMessageListener blaubotMessageListener = new BlaubotMessageListener() {
        @Override
        public void onMessage(final String deviceId, final short channelId, final short messageId, final String userName, final short userRole,
                              final String text, final String prediction, final String rotation, final long timestamp, final boolean called, final boolean mmol) {
            handler.post(new Runnable() {
                @Override
                public void run() {

                    String time = mFormatDateTime.format(new Date(timestamp));

                    Log.v(TAG, "Message with id " + deviceId + " from " + userName + " with role " + userRole + " and text " + text + " with timestamp " + timestamp + " via Blaubot...");

                    if(channelId == BlaubotService.CHANNEL_ID_SENSOR_DATA && messageId == BlaubotService.MSG_ID_SENSOR_DATA && m_userRole == USER_ROLE_CARETAKER){
                        handleSensorData(userName, text, prediction, rotation, time, mmol);
                    }

                    if(channelId == BlaubotService.CHANNEL_ID_EMERGENCY && messageId == BlaubotService.MSG_ID_EMERGENCY_CALL && m_userRole == USER_ROLE_CARETAKER){
                        handleEmergencyCall(userName, text, prediction, timestamp, called, mmol);
                    }

                    if(channelId == BlaubotService.CHANNEL_ID_EMERGENCY && messageId == BlaubotService.MSG_ID_EMERGENCY_CALL
                            && (m_userRole == USER_ROLE_PATIENT || m_userRole == USER_ROLE_SENSOR_SIMULATOR)
                            && text.equals("STOP"))
                        handlePatientEmergencyAnswer(userName);
                }
            });
        }

        @Override
        public void onBlaubotStatus(final boolean connected, final String deviceId) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (connected) {
                        Log.v(TAG, "Blaubot Service connected...");
                    } else {
                        Log.v(TAG, "Blaubot Service not connected...");
                    }
                    if (deviceId == null) {
                        Log.v(TAG, "No device ID available...");
                    } else {
                        Log.v(TAG, "Blaubot device ID: " + deviceId);
                    }
                }
            });
        }

        @Override
        public void onLogMessage(final String message) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Log.v(TAG, "LOGVIEW MESSAGE: " + message);
                }
            });
        }

        @Override
        public void onInetAddress(String hostAddress) {
            blaubot_connected = true;
            ownHostAddress = hostAddress;
            tv_connection_status.setBackgroundColor(Color.GREEN);
            tv_connection_status.setText(hostAddress);
        }

        @Override
        public void onDeviceMapChanged(final HashMap<String, String> connectedDevices) {
            connected_devices_hash_map = connectedDevices;
            // Reorganize the Devices List
            connected_devices_list.clear();
            Set<Map.Entry<String, String>> entrySet = connected_devices_hash_map.entrySet();
            for(Map.Entry<String, String> entry: entrySet){
                final String uuid = entry.getKey();
                final String[] userNameUserRole = entry.getValue().split(": ");
                final String userName = userNameUserRole[0];
                final Short userRoleShort = Short.parseShort(userNameUserRole[1]);
                final String userRoleString = getUserRoleString(userRoleShort);
                // Update the list view on UI-Thread
                runOnUiThread(new Runnable(){
                    @Override
                    public void run() {
                        if(!connected_devices_list.contains(userName+": "+userRoleString)) {
                            connected_devices_list.add(userName + ": " + userRoleString);
                            connected_devices_adapter.notifyDataSetChanged();
                        }
                    }
                });

            }
        }

        //        @Override
//        public void onDeviceJoined(final String userName, final short userRole) {
//            //Log.v(TAG, userName + " JOINED WITH ROLE: " + userRole);
//            connected_devices_hash_map.put(userName, userRole);
//
//            // Update the list view on UI-thread
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    if(!connected_devices_list.contains(userName+"::"+getUserRoleString(userRole))){
//                        connected_devices_list.add(userName+"::"+getUserRoleString(userRole));
//                    }
//                    connected_devices_adapter.notifyDataSetChanged();
//                }
//            });
//
//            //Log.v(TAG, connected_devices_hash_map.toString());
//        }
//
//        @Override
//        public void onDeviceLeft(final String userName, final short userRole) {
//            //Log.v(TAG, userName + " LEFT WITH ROLE: " + userRole);
//            connected_devices_hash_map.remove(userName);
//
//            // Update the list view on UI-thread
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    if(connected_devices_list.contains(userName+"::"+getUserRoleString(userRole))){
//                        connected_devices_list.remove(userName+"::"+getUserRoleString(userRole));
//                    }
//                    connected_devices_adapter.notifyDataSetChanged();
//                }
//            });
//
//            //Log.v(TAG, connected_devices_hash_map.toString());
//        }
    };

    private void bindBlaubotService() {
        //Log.v(TAG, "bindBlaubotService");
        Intent intent = new Intent(this, BlaubotService.class);
        intent.setAction(BlaubotService.ACTION_BIND);
        boolean result = bindService(intent, blaubotServiceConnection, Context.BIND_AUTO_CREATE);
        blaubotServiceBound.set(result);
        if (!result) {
            Log.w(TAG, "could not bind service, will not be bound");
        }
    }

    private void unbindBlaubotService() {
        if (blaubotServiceBound.get()) {
            if (blaubotService != null) {
                // deregister listeners, if there are any
                blaubotService.deregisterBlaubotMessageListener(blaubotMessageListener);
            }
            blaubotServiceBound.set(false);
            unbindService(blaubotServiceConnection);
        }
    }

    private void stopBlaubotService(){
        Intent stopBlaubotIntent = new Intent(findViewById(android.R.id.content).getContext(), BlaubotService.class);
        stopBlaubotIntent.setAction(BlaubotService.ACTION_STOP);
        startService(stopBlaubotIntent);
    }

//    private void updateConnectedDevicesListView(){
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                connected_devices_list = connectedDevicesHashmapToArrayList(connected_devices_hash_map);
//                connected_devices_adapter.notifyDataSetChanged();
//            }
//        });
//    }



    // ---------------------------------------------------------------------------------------------
    // Shaking service methods
    // ---------------------------------------------------------------------------------------------

    private ServiceConnection shakeDetectorConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            shakeDetectorService = ((ShakeDetectorService.ShakeDetectorBinder) service).getService();
            shakeDetectorService.setUserName(m_userName);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            shakeDetectorServiceBound = false;
            shakeDetectorService = null;
        }
    };

    private boolean bindAndStartShakeDetectorService() {
        // Start a new service to detect the shaking of the smartphone
        Intent serviceIntent = new Intent(this, ShakeDetectorService.class);
        shakeDetectorServiceBound = bindService(serviceIntent, shakeDetectorConnection, Context.BIND_AUTO_CREATE);
        if(shakeDetectorServiceBound){
            startService(serviceIntent);
            //Log.v(TAG, "Shake Detector Service bound");
        }
        return shakeDetectorServiceBound;
    }

    private void unbindAndStopShakeDetectorService() {
        // Stop the service
        Intent serviceIntent = new Intent(this, ShakeDetectorService.class);
        stopService(serviceIntent);
        // Unbind the service if bound
        if(shakeDetectorServiceBound){
            shakeDetectorServiceBound = false;
            shakeDetectorService = null;
            unbindService(shakeDetectorConnection);
        }
    }


    // ---------------------------------------------------------------------------------------------
    // Options menu methods
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.actions_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){

            case R.id.menu_join_wlan:
                joinWlan();
                return true;
            case R.id.menu_leave_wlan:
                leaveWlan();
                wlan_joined = false;
                return true;
            case R.id.menu_settings:
                startActivity(new Intent(this, Settings.class));
                return true;
            default:
                return false;
        }
    }



    // ---------------------------------------------------------------------------------------------
    // Progress Bar updater thread
    // ---------------------------------------------------------------------------------------------

    private class ProgressBarUpdater implements Runnable {
        private volatile boolean running = true;
        private volatile boolean paused = false;
        private volatile boolean wasStarted = false;
        private int cnt = 0;
        private long progressSeconds = 100;
        private long millisToSleep = 1000;

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

        public void setSecondsHundredPercent(long secondsHundredPercent){
            //this.progressSeconds = seconds;
            // Calculate the sleeping time
            millisToSleep = Math.round(1f*secondsHundredPercent/100f*1000f);

            //millisToSleep = TimeUnit.SECONDS.toMillis(Math.round(milliSecondsOnePercent));
            //float sleepTime = 1*progressSeconds/100*1000;
            //millisToSleep = Math.round(sleepTime);
        }

        public void reset(){
            cnt = 100;
            pb_timeline.setProgress(cnt);
            if(paused=true){
                paused=false;
                resume();
            }
        }

        @Override
        public void run() {
            cnt = 100;
            wasStarted = true;
            while(running){
                if(!paused){
                    pb_timeline.setProgress(cnt);
                    if(cnt == 0){
                        cnt = 100;
                        paused = true;
                    }
                    try {
                        Thread.sleep(millisToSleep);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        running = false;
                    }
                    cnt--;
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


    // ---------------------------------------------------------------------------------------------
    // Helper functions
    // ---------------------------------------------------------------------------------------------

    private void handleSensorData(String username, String read, String prediction, String rotation, String timestamp, boolean mmol){
        float readValue = 0;
        float predictionValue = 0;
        float rotationValue = 0;
        // Parse sensor read and display
        try {
            readValue = Float.parseFloat(read);
            predictionValue = Float.parseFloat(prediction);
            rotationValue = Float.parseFloat(rotation);
        } catch (NumberFormatException e){
            e.printStackTrace();
        }

        if(mmol){
            readValue = readValue *18f;
            predictionValue = predictionValue *18f;
        }

        if(readValue != 0 && predictionValue != 0){

            // Visualize the sensor value
            if(readValue < 71){
                tv_glucose_read.setBackgroundColor(Color.CYAN);
            }
            if(readValue >= 71 && readValue <= 160) {
                tv_glucose_read.setBackgroundColor(Color.GREEN);
            }
            if(readValue > 160 && readValue <= 240) {
                tv_glucose_read.setBackgroundColor(Color.YELLOW);
            }
            if(readValue > 240){
                tv_glucose_read.setBackgroundColor(Color.RED);
            }

            if(predictionValue < 71){
                tv_glucose_prediction.setBackgroundColor(Color.CYAN);
            }
            if(predictionValue >= 71 && predictionValue <= 160) {
                tv_glucose_prediction.setBackgroundColor(Color.GREEN);
            }
            if(predictionValue > 160 && predictionValue <= 240) {
                tv_glucose_prediction.setBackgroundColor(Color.YELLOW);
            }
            if(predictionValue > 240){
                tv_glucose_prediction.setBackgroundColor(Color.RED);
            }
        }

        ImageView iv_unit = (ImageView) findViewById(R.id.iv_unit);
        if(GLUCOSE_UNIT_IS_MMOL){
            readValue = readValue /18f;
            predictionValue = predictionValue /18f;
            iv_unit.setImageResource(R.drawable.ic_unit_mmoll);
        }
        else
            iv_unit.setImageResource(R.drawable.ic_unit_mgdl);

        // Update the sensor value display
        tv_glucose_read.setText(unitformat(readValue));
        tv_glucose_prediction.setText(unitformat(predictionValue));
        iv_glucose_arrow.setRotation(rotationValue);

        // Update the log text view
        tv_plot_title.setText(timestamp + ": " + username);

        //Update the ProgressBar
        if(!progressBarUpdater.wasStarted()){
            progressBarUpdaterThread.start();
        } else {
            progressBarUpdater.reset();
        }
    }

    private String unitformat(float value){
        return GLUCOSE_UNIT_IS_MMOL ?
                new DecimalFormat("##.0").format(value) :
                new DecimalFormat("###").format(value);
    }

    private void handleEmergencyCall(String userName, String read, String predict, long timestamp, boolean called, boolean mmol){

        String sendTime = getTimeOfDay(System.currentTimeMillis());
        read = read.replace(",", ".");
        predict = predict.replace(",", ".");

        final Intent emptyIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, emptyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        nBuilder.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(sendTime + ": New warning from " + userName)
                .setContentIntent(pendingIntent)
                .setDefaults(Notification.DEFAULT_ALL)
                .setPriority(Notification.PRIORITY_MAX);

        if(!read.equals("STOP")) {
            if(!called) {
                String unit = new String();
                if (GLUCOSE_UNIT_IS_MMOL) {
                    unit = "mmol/L";
                    if (!mmol) {
                        read = String.valueOf(Float.parseFloat(read) / 18f);
                        predict = String.valueOf(Float.parseFloat(predict) / 18f);
                    }
                } else {
                    unit = "mg/dL";
                    if (mmol) {
                        read = String.valueOf(Float.parseFloat(read) * 18f);
                        predict = String.valueOf(Float.parseFloat(predict) * 18f);
                    }
                }
                caretaker_info = sendTime + " " + userName + ": " + unitformat(Float.parseFloat(read)) + " -> " +
                        unitformat(Float.parseFloat(predict)) + " " + unit + "\n";
                tv_caretaker_warning.setText(sendTime + " " + userName + " measured " +
                        unitformat(Float.parseFloat(read)) + " -> " + unitformat(Float.parseFloat(predict)) + " " + unit);
            }

            else {
                caretaker_info = sendTime + " " + userName + ": " + read + "\n";
                tv_caretaker_warning.setText(sendTime + " " + userName.toUpperCase() + " NEEDS HELP!!");
            }

            if(infobox_list.isEmpty())
                infobox_list.add(caretaker_info.replace("\n", ""));
            else{
                for(int i=infobox_list.size(); i>0; i--){
                    if(i==infobox_list.size())
                        infobox_list.add(infobox_list.get(i-1));
                    else
                        infobox_list.set(i, infobox_list.get(i-1));
                }
                infobox_list.set(0, caretaker_info.replace("\n", ""));
            }
            infobox_adapter.notifyDataSetChanged();
            lv_caretaker_info.setVisibility(View.GONE);
            ll_caretaker_warning.setVisibility(View.VISIBLE);

            nManager.notify(1, nBuilder.build());
        }

        else{
            if(!infobox_list.isEmpty()) {
                nManager.cancelAll();
                ll_caretaker_warning.setVisibility(View.GONE);
                lv_caretaker_info.setVisibility(View.VISIBLE);
                String[] temp = infobox_list.get(0).trim().split(" ");
                String oldName = temp[1].split(":")[0];
                sendTime = getTimeOfDay(System.currentTimeMillis());
                caretaker_info = sendTime + " " + userName + " took care of " + oldName + "\n";

                if (infobox_list.isEmpty())
                    infobox_list.add(caretaker_info.replace("\n", ""));
                else {
                    for (int i = infobox_list.size(); i > 0; i--) {
                        if (i == infobox_list.size())
                            infobox_list.add(infobox_list.get(i - 1));
                        else
                            infobox_list.set(i, infobox_list.get(i - 1));
                    }
                    infobox_list.set(0, caretaker_info.replace("\n", ""));
                }
                infobox_adapter.notifyDataSetChanged();
            }
        }
    }

    private void handlePatientEmergencyAnswer(String userName){
        tv_pat_sim_infobox.setText(getTimeOfDay(System.currentTimeMillis()) + ": " + userName + " is taking care of you!");
    }

    private void joinWlan(){
            if(!blaubot_connected){
                //Log.v(TAG, "Starting Blaubot Service...");
                wlan_joined = true;
                if(!blaubotServiceBound.get()){
                    bindBlaubotService();
                }
                Intent startBlaubotIntent = new Intent(findViewById(android.R.id.content).getContext(), BlaubotService.class);
                startBlaubotIntent.setAction(BlaubotService.ACTION_START);
                startBlaubotIntent.putExtra(BlaubotService.EXTRA_BLAUBOT_TYPE, BlaubotService.BLAUBOT_WIFI); // Only wifi mode needed
                startService(startBlaubotIntent);
            }

    }

    public void leaveWlan(){
        int counter = 0;
        Set<Map.Entry<String, String>> entrySet = connected_devices_hash_map.entrySet();
        for(Map.Entry<String, String> entry: entrySet) {
            final String uuid = entry.getKey();
            final String[] userNameUserRole = entry.getValue().split(": ");
            final String userName = userNameUserRole[0];
            final Short userRoleShort = Short.parseShort(userNameUserRole[1]);
            final String userRoleString = getUserRoleString(userRoleShort);
            if(userRoleString == getUserRoleString(USER_ROLE_CARETAKER))
                counter++;
        }
        if(counter < 2 && m_userRole == USER_ROLE_CARETAKER){
            Toast.makeText(getApplicationContext(), "You were the last remaining caretaker!", Toast.LENGTH_LONG).show();
        }

        if(blaubot_connected){
            stopBlaubotService();
            if(blaubotServiceBound.get()){
                unbindBlaubotService();
            }
            blaubot_connected = false;
            tv_connection_status.setBackgroundColor(Color.RED);
            tv_connection_status.setText("");
            connected_devices_hash_map.clear(); // Map of the devices which are online is invalidate -> clear
            connected_devices_list.clear(); // same as above
            connected_devices_adapter.notifyDataSetChanged();
        }
    }


    //------------NFC Adapter vorbereiten--------------------

    private void startsensor(){

        mNfcAdapter = ((NfcManager) this.getSystemService(Context.NFC_SERVICE)).getDefaultAdapter();
        if (mNfcAdapter != null) {
            Log.d(TAG, "Got NFC adapter");
            if (!mNfcAdapter.isEnabled()) {
                Toast.makeText(this, getResources().getString(R.string.error_nfc_disabled), Toast.LENGTH_LONG).show();
            }
        } else {
            Log.e(TAG,"No NFC adapter found!");
            Toast.makeText(this, getResources().getString(R.string.error_nfc_device_not_supported), Toast.LENGTH_LONG).show();
        }
    }


    // ---------------------------------------------------------------------------------------------
    // Blood Sugar Sensor
    // ---------------------------------------------------------------------------------------------

    //@Override
    public void onShowScanData(ReadingData readingData) {
        afterread(readingData);
        showScan(readingData);
    }


    private void startContinuousSensorReadingTimer() {
        Timer continuousSensorReadingTimer = new Timer();
        TimerTask continuousSensorReadingTask = new TimerTask() {
            @Override
            public void run() {
                new NfcVReaderTask(MainActivity.this).execute(mLastNfcTag);
            }
        };
        continuousSensorReadingTimer.schedule(continuousSensorReadingTask, 0L, TimeUnit.SECONDS.toMillis(60L));
    }

    @Override
    protected void onNewIntent(Intent data) {
        resolveIntent(data);
    }


    private void resolveIntent(Intent data) {
        this.setIntent(data);

        if ((data.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            return;
        }

        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(data.getAction()) && m_userRole == 2) {
            mLastNfcTag = data.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            long now = new Date().getTime();

            if (mContinuousSensorReadingFlag) {
                startContinuousSensorReadingTimer();

            } else
                new NfcVReaderTask(this).execute(mLastNfcTag);

        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case PENDING_INTENT_TECH_DISCOVERED:
                // Resolve the foreground dispatch intent:
                resolveIntent(data);
                break;
        }
    }


    public void onNfcReadingFinished(ReadingData readingData) {
        mLastScanTime = new Date().getTime();
        onShowScanData(readingData);

        //Update the ProgressBar
        if(!progressBarUpdater.wasStarted()){
            progressBarUpdaterThread.start();
        } else {
            progressBarUpdater.reset();
        }


        blaubotService.sendMessage(BlaubotService.CHANNEL_ID_SENSOR_DATA, BlaubotService.MSG_ID_SENSOR_DATA, m_userName, m_userRole,
                String.valueOf(READ_VALUE), String.valueOf(PREDICTION_VALUE), String.valueOf(ROTATION_VALUE), TIME_VALUE, false, GLUCOSE_UNIT_IS_MMOL);

    }


    private void afterread(ReadingData readingData){

        mPlot = (LineChart)findViewById(R.id.sapp_cv_glucose_plot);
        resetView();
        mUpdatePlotTitleTimer = new Timer();
        setupPlot();
        clearScanData();
    }


    private void resetView() {

        if(mPlot == null)
            mPlot = (LineChart)findViewById(R.id.sapp_cv_glucose_plot);
        mPlot.clear();
        //mPlot.setVisibility(View.INVISIBLE);

    }

    private void setupPlot() {
        if(mPlot == null)
            mPlot = (LineChart) findViewById(R.id.sapp_cv_glucose_plot);
        mPlot.setNoDataText("");
        mPlot.setOnChartGestureListener(this);
        mDateTimeMarkerView = new DateTimeMarkerView(this, R.layout.date_time_marker);
        mPlot.setMarker(mDateTimeMarkerView);
        mDateTimeMarkerView.setChartView(mPlot);

        // no description text
        mPlot.getDescription().setEnabled(false);

        // enable touch gestures
        mPlot.setTouchEnabled(true);
        mPlot.setDragDecelerationFrictionCoef(0.9f);

        // if disabled, scaling can be done on x- and y-axis separately
        mPlot.setPinchZoom(true);

        // enable scaling and dragging
        mPlot.setDragEnabled(true);
        mPlot.setScaleEnabled(true);
        mPlot.setDrawGridBackground(false);

        // set an alternative background color
        mPlot.setBackgroundColor(Color.argb(0, 255, 255, 255));

        mPlot.setOnChartValueSelectedListener(this);

        Legend legend = mPlot.getLegend();
        legend.setEnabled(false);

        XAxis xAxis = mPlot.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextSize(12f);
        xAxis.setTextColor(Color.BLACK);
        xAxis.setDrawAxisLine(false);
        xAxis.setDrawGridLines(false);
        xAxis.setCenterAxisLabels(false);
        xAxis.setGranularity(convertDateToXAxisValue(TimeUnit.MINUTES.toMillis(5L))); // same unit as x axis values
        xAxis.setDrawLimitLinesBehindData(true);
        xAxis.setLabelCount(4);

        xAxis.setValueFormatter(new IAxisValueFormatter() {
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                long date = convertXAxisValueToDate(value);
                return mFormatTimeShort.format(new Date(date));
            }
        });

        YAxis leftAxis = mPlot.getAxisLeft();
        leftAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        leftAxis.setTextSize(12f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGranularityEnabled(true);
        leftAxis.setGranularity(5f);
        leftAxis.setTextColor(Color.BLACK);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(500f);

        YAxis rightAxis = mPlot.getAxisRight();
        rightAxis.setEnabled(false);

        updateTargetArea();

        try {
            mPlot.setHardwareAccelerationEnabled(true);
        } catch (Exception e) {
            Log.d(TAG, "Hardware acceleration for data plot failed: " + e.toString());
        }
    }

    private void updateTargetArea() {
        YAxis leftAxis = mPlot.getAxisLeft();
        leftAxis.removeAllLimitLines();
        LimitLine limitLineMax = new LimitLine(
                GLUCOSE_TARGET_MAX
        );
        limitLineMax.setLineColor(Color.TRANSPARENT);
        leftAxis.addLimitLine(limitLineMax);

        LimitLine limitLineMin = new LimitLine(
                GLUCOSE_TARGET_MIN,
                getResources().getString(R.string.pref_glucose_target_area)
        );
        limitLineMin.setTextSize(10f);
        limitLineMin.setLineColor(Color.argb(60, 100, 100, 120));
        limitLineMin.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
        leftAxis.addLimitLine(limitLineMin);
    }

    public void clearScanData() {
        findViewById(R.id.sapp_scan_data).setVisibility(View.GONE);
    }

    public void showMultipleScans(List<ReadingData> readingDataList) {
        updateTargetArea();
        mPlot.clear();

        for (ReadingData readingData : readingDataList) {
            addLineData(readingData.getHistory(), readingData.getTrend());
        }

        updatePlotTitle(false);
        updateChartViewConstrains();
        ((TextView) findViewById(R.id.sapp_tv_infobox)).setText("");
    }

    void showHistory(List<GlucoseData> history) {
        updateTargetArea();
        mPlot.clear();

        updatePlot(history, null);
    }

    void showScan(ReadingData readData) {
        updateTargetArea();
        mPlot.clear();

        updateScanData(readData.getTrend());
        updatePlot(readData.getHistory(), readData.getTrend());
    }

    private void updateScanData(List<GlucoseData> trend) {
        if (trend.size() == 0) {
            Toast.makeText(getApplicationContext(), "No current data available!", Toast.LENGTH_LONG).show();
            return;
        }

        findViewById(R.id.sapp_scan_data).setVisibility(View.VISIBLE);

        GlucoseData currentGlucose = trend.get(trend.size() - 1);
        tv_glucose_read.setText(currentGlucose.glucoseString());

        String readvalue = currentGlucose.glucoseString();
        if(GLUCOSE_UNIT_IS_MMOL)
            readvalue = readvalue.replace("," , ".");
        READ_VALUE = Float.parseFloat(readvalue);

        PredictionData predictedGlucose = new PredictionData(trend);

        tv_glucose_prediction.setText(String.valueOf(predictedGlucose.glucoseData.glucoseString()));
        tv_glucose_prediction.setAlpha((float) min(1, 0.1 + predictedGlucose.confidence()));

        String predictvalue = predictedGlucose.glucoseData.glucoseString();
        if(GLUCOSE_UNIT_IS_MMOL)
            predictvalue = predictvalue.replace("," , ".");
        PREDICTION_VALUE = Float.parseFloat(predictvalue);

        ImageView iv_unit = (ImageView) findViewById(R.id.iv_unit);
        if (GLUCOSE_UNIT_IS_MMOL) {
            iv_unit.setImageResource(R.drawable.ic_unit_mmoll);
        } else {
            iv_unit.setImageResource(R.drawable.ic_unit_mgdl);
        }

        ImageView iv_predictionArrow = (ImageView) findViewById(R.id.iv_glucose_prediction);

        // rotate trend arrow according to glucose prediction slope
        float rotationDegrees = -90f * max(-1f, min(1f, (float) (predictedGlucose.glucoseSlopeRaw / TREND_UP_DOWN_LIMIT)));
        iv_predictionArrow.setRotation(rotationDegrees);

        ROTATION_VALUE = rotationDegrees;

        // reduce trend arrow visibility according to prediction confidence
        iv_predictionArrow.setAlpha((float) min(1, 0.1 + predictedGlucose.confidence()));

        if(GLUCOSE_UNIT_IS_MMOL){
            READ_VALUE = READ_VALUE *18f;
            PREDICTION_VALUE = PREDICTION_VALUE *18f;
        }

        if(READ_VALUE < 71){
            tv_glucose_read.setBackgroundColor(Color.CYAN);
        }
        if(READ_VALUE >= 71 && READ_VALUE <= 160) {
            tv_glucose_read.setBackgroundColor(Color.GREEN);
        }
        if(READ_VALUE > 160 && READ_VALUE <= 240) {
            tv_glucose_read.setBackgroundColor(Color.YELLOW);
        }
        if(READ_VALUE > 240){
            tv_glucose_read.setBackgroundColor(Color.RED);
        }

        if(PREDICTION_VALUE < 71){
            tv_glucose_prediction.setBackgroundColor(Color.CYAN);
        }
        if(PREDICTION_VALUE >= 71 && PREDICTION_VALUE <= 160) {
            tv_glucose_prediction.setBackgroundColor(Color.GREEN);
        }
        if(PREDICTION_VALUE > 160 && PREDICTION_VALUE <= 240) {
            tv_glucose_prediction.setBackgroundColor(Color.YELLOW);
        }
        if(PREDICTION_VALUE > 240){
            tv_glucose_prediction.setBackgroundColor(Color.RED);
        }

        if(READ_VALUE<71 || PREDICTION_VALUE<71)
            blaubotService.sendMessage(BlaubotService.CHANNEL_ID_EMERGENCY,
                    BlaubotService.MSG_ID_EMERGENCY_CALL, m_userName, m_userRole,
                    unitformat(READ_VALUE), unitformat(PREDICTION_VALUE), "LOW",
                    new Date(System.currentTimeMillis()).getTime(), false, false);
        if(READ_VALUE>159 || PREDICTION_VALUE>159)
            blaubotService.sendMessage(BlaubotService.CHANNEL_ID_EMERGENCY,
                    BlaubotService.MSG_ID_EMERGENCY_CALL, m_userName, m_userRole,
                    unitformat(READ_VALUE), unitformat(PREDICTION_VALUE), "HIGH",
                    new Date(System.currentTimeMillis()).getTime(), false, false);
        if(READ_VALUE>240 || PREDICTION_VALUE>240)
            blaubotService.sendMessage(BlaubotService.CHANNEL_ID_EMERGENCY,
                    BlaubotService.MSG_ID_EMERGENCY_CALL, m_userName, m_userRole,
                    unitformat(READ_VALUE), unitformat(PREDICTION_VALUE), "HIGHER",
                    new Date(System.currentTimeMillis()).getTime(), false, false);

        if(GLUCOSE_UNIT_IS_MMOL){
            READ_VALUE = READ_VALUE /18f;
            PREDICTION_VALUE = PREDICTION_VALUE /18f;
        }

        String sendData = Float.toString(READ_VALUE)+Double.toString(ROTATION_VALUE);
        byte[] bytes = sendData.getBytes();
        Wearable.getMessageClient(getApplicationContext()).sendMessage(
                "phone", "/newScan", bytes);
    }


    private void updatePlot(List<GlucoseData> history, List<GlucoseData> trend) {
        Log.d(TAG, String.format("#history: %d, #trend: %d", history.size(), trend == null ? 0 : trend.size()));

        if (history.size() == 0) {
            Toast.makeText(getApplicationContext(), "No historical data available!", Toast.LENGTH_LONG).show();
            return;
        }

        mPlotColorIndex = 0;
        addLineData(history, trend);

        updatePlotTitle(trend != null);
        updateChartViewConstrains();
        ((TextView) findViewById(R.id.sapp_tv_infobox)).setText("");
    }

    private void addLineData(List<GlucoseData> history, List<GlucoseData> trend) {
        if (mFirstDate < 0) {
            mFirstDate = history.get(0).getDate();
            mDateTimeMarkerView.setFirstDate(mFirstDate);
        }

        LineData lineData = mPlot.getData();
        if (lineData == null) {
            lineData = new LineData();
        }
        lineData.addDataSet(makeLineData(history));
        if (trend != null)
            lineData.addDataSet(makeLineData(trend));

        mPlotColorIndex++;
        mPlot.setData(lineData);
    }

    private void setPlotTitleUpdateTimer() {
        // update 3 minutes after most recent data timestamp
        Date updateTime = new Date(3 * 60 * 1000 + convertXAxisValueToDate(mPlot.getData().getXMax()));
        if (mUpdatePlotTitleTask != null) {
            mUpdatePlotTitleTask.cancel();
        }
        mUpdatePlotTitleTask = new TimerTask() {
            @Override
            public void run() {
                if (getApplicationContext() != null) {
                    MainActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            TextView tv_plotTitle = (TextView) findViewById(R.id.tv_plot_title);
                            tv_plotTitle.setTextColor(Color.RED);
                        }
                    });
                }
            }
        };
        mUpdatePlotTitleTimer.schedule(mUpdatePlotTitleTask, updateTime);
    }

    private void updatePlotTitle(boolean isScanData) {
        TextView tv_plotTitle = (TextView) findViewById(R.id.tv_plot_title);
        String plotTitle = new String();
        if (isScanData) {
            plotTitle = mFormatDateTime.format(new Date(convertXAxisValueToDate(mPlot.getData().getXMax())));
            TIME_VALUE = new Date(convertXAxisValueToDate(mPlot.getData().getXMax())).getTime();
        }
        tv_plotTitle.setTextColor(Color.BLACK);
        setPlotTitleUpdateTimer();
        tv_plotTitle.setText(plotTitle);

    }

    private void updateChartViewConstrains() {
        mPlot.fitScreen();

        final float minGlucoseShown = convertGlucoseMGDLToDisplayUnit(20);
        final float maxGlucoseShown = minGlucoseShown * maxZoomFactor;

        mPlot.setVisibleYRangeMinimum(minGlucoseShown, mPlot.getAxisLeft().getAxisDependency());
        mPlot.setVisibleYRangeMaximum(maxGlucoseShown, mPlot.getAxisLeft().getAxisDependency());

        final float maxMinutesShown = ReadingData.historyIntervalInMinutes * ReadingData.numHistoryValues + 2 * ReadingData.numTrendValues;
        final float minMinutesShown = maxMinutesShown / maxZoomFactor;

        mPlot.setVisibleXRangeMinimum(minMinutesShown);
        mPlot.setVisibleXRangeMaximum(maxMinutesShown);

        zoomOutMax();

        mPlot.invalidate();
    }

    private void zoomOutMax() {
        mPlot.zoom(1 / maxZoomFactor, 1 / maxZoomFactor, mPlot.getData().getXMax(), (mPlot.getData().getYMax() + mPlot.getData().getYMin()) / 2, mPlot.getAxisLeft().getAxisDependency());
        isZoomedToTrend = false;
    }

    private void zoomToTrend() {
        ILineDataSet lineDataSet = mPlot.getData().getDataSetByIndex(mPlot.getData().getDataSetCount() - 1);
        Entry lastEntry = lineDataSet.getEntryForIndex(lineDataSet.getEntryCount() - 1);
        float yCenter = (lineDataSet.getYMin() + lineDataSet.getYMax()) / 2;

        // zoom in max to the last data points
        mPlot.zoom(maxZoomFactor, maxZoomFactor, lastEntry.getX(), yCenter, mPlot.getAxisLeft().getAxisDependency());
        isZoomedToTrend = true;
    }

    private LineDataSet makeLineData(List<GlucoseData> glucoseDataList) {
        String title = "History";
        if (glucoseDataList.get(0).isTrendData()) title = "Trend";

        LineDataSet lineDataSet = new LineDataSet(new ArrayList<Entry>(), title);
        for (GlucoseData gd : glucoseDataList) {
            float x = convertDateToXAxisValue(gd.getDate());
            float y = gd.glucose();
            lineDataSet.addEntryOrdered(new Entry(x, y));
        }

        lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        lineDataSet.setDrawCircles(true);
        lineDataSet.setCircleRadius(2f);

        lineDataSet.setDrawCircleHole(false);
        lineDataSet.setDrawValues(false);

        lineDataSet.setDrawHighlightIndicators(true);

        int baseColor = PLOT_COLORS[mPlotColorIndex % NUM_PLOT_COLORS][0];
        int softColor = Color.argb(150, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
        int hardColor = PLOT_COLORS[mPlotColorIndex % NUM_PLOT_COLORS][1];
        if (glucoseDataList.get(0).isTrendData()) {
            lineDataSet.setColor(hardColor);
            lineDataSet.setLineWidth(2f);

            lineDataSet.setCircleColor(softColor);

            lineDataSet.setMode(LineDataSet.Mode.LINEAR);
        } else {
            lineDataSet.setColor(softColor);
            lineDataSet.setLineWidth(4f);

            lineDataSet.setCircleColor(hardColor);

            lineDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            lineDataSet.setCubicIntensity(0.1f);
        }

        return lineDataSet;
    }

    private float convertDateToXAxisValue(long date) {
        return (date - mFirstDate) / TimeUnit.MINUTES.toMillis(1L);
    }

    private long convertXAxisValueToDate(float value) {
        return mFirstDate + (long) (value * TimeUnit.MINUTES.toMillis(1L));
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        Log.d(TAG, "Selected: " + e.toString() + " : " +
                mFormatDateTime.format(new Date(convertXAxisValueToDate(e.getX()))));
    }

    @Override
    public void onNothingSelected() {

    }

    @Override
    public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {

    }

    @Override
    public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {

    }

    @Override
    public void onChartLongPressed(MotionEvent me) {

    }

    @Override
    public void onChartDoubleTapped(MotionEvent me) {

    }

    @Override
    public void onChartSingleTapped(MotionEvent me) {

    }

    @Override
    public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {

    }

    @Override
    public void onChartScale(MotionEvent me, float scaleX, float scaleY) {

    }

    @Override
    public void onChartTranslate(MotionEvent me, float dX, float dY) {
        updatePlotDate();
    }

    public void updatePlotDate() {
        TextView plotTitle = (TextView) findViewById(R.id.sapp_tv_infobox);
        if (convertXAxisValueToDate(mPlot.getData().getXMax()) -
                convertXAxisValueToDate(mPlot.getData().getXMin())
                > TimeUnit.HOURS.toMillis(12L)) {
            String minDate = mFormatDate.format(new Date(convertXAxisValueToDate(mPlot.getLowestVisibleX())));
            String maxDate = mFormatDate.format(new Date(convertXAxisValueToDate(mPlot.getHighestVisibleX())));
            if (minDate.compareTo(maxDate) == 0 || mPlot.getLowestVisibleX() > mPlot.getHighestVisibleX()) {
                plotTitle.setText(maxDate);
            } else {
                plotTitle.setText(minDate + " - " + maxDate);
            }
        } else {
            // no need to show the date, if showing less then 12 hours of data (e.g. a single scan)
            plotTitle.setText("");
        }
    }
}
