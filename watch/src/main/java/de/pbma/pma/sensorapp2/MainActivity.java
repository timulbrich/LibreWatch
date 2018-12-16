package de.pbma.pma.sensorapp2;

import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.Set;

public class MainActivity extends WearableActivity {

    private TextView mTextView;
    final static String CAPABILITY_PHONE_APP = "phone";
    Set<Node> nodes;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = (TextView) findViewById(R.id.text);

        // Enables Always-on
        setAmbientEnabled();


        Task<CapabilityInfo> capabilityInfoTask = Wearable.getCapabilityClient(this.getApplicationContext())
                .getCapability(CAPABILITY_PHONE_APP, CapabilityClient.FILTER_REACHABLE);

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

        Wearable.getMessageClient(getApplicationContext()).addListener(this::handleMessage);

    }

    public void handleMessage(MessageEvent messageEvent){

        String msg = new String(messageEvent.getData());
        //System.out.println(msg);
        mTextView.setText(msg);


    }
}
