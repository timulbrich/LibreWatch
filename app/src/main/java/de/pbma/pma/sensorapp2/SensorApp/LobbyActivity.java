package de.pbma.pma.sensorapp2.SensorApp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import de.pbma.pma.sensorapp2.R;

public class LobbyActivity extends Activity {

    public static EditText et_user_name_input = null;
    public static Spinner sp_role = null;
    private Button btn_ok_user_name_input = null;
    private TextView tv_infobox_user_name_input = null;
    public static EditText et_minimum = null;
    public static EditText et_maximum = null;
    public static CheckBox cb_unitmmol = null;
    private SharedPreferences appPreferences = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        // Get the widgets
        et_user_name_input = (EditText)findViewById(R.id.sapp_et_user_name_input);
        btn_ok_user_name_input = (Button)findViewById(R.id.sapp_btn_ok_user_name_input);
        tv_infobox_user_name_input = (TextView)findViewById(R.id.sapp_tv_infobox_username_input);
        sp_role = (Spinner)findViewById(R.id.sapp_spinner_role);
        et_maximum = (EditText)findViewById(R.id.et_lobby_max);
        et_minimum = (EditText)findViewById(R.id.et_lobby_min);
        cb_unitmmol = (CheckBox)findViewById(R.id.cb_lobby_unitmmol);

        // Associate the listeners
        btn_ok_user_name_input.setOnClickListener(btn_ok_user_name_input_clicked);

        // Get the shared preferences
        appPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        if(!appPreferences.getBoolean(getString(R.string.key_first_run), true)){
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }

    }


    private View.OnClickListener btn_ok_user_name_input_clicked = new View.OnClickListener(){
        @Override
        public void onClick(View view) {
            String userName = et_user_name_input.getText().toString();
            // Check if the username has not only whitespaces
            if(userName.trim().length() > 0){
                // Save the username to shared preferences
                SharedPreferences.Editor editor = appPreferences.edit();
                editor.putString(getString(R.string.key_user_name), userName);
                editor.putBoolean(getString(R.string.key_first_run), false);
                if (sp_role.getSelectedItemPosition() == 0)//Patient
                    editor.putString(getString(R.string.key_start_role), sp_role.getSelectedItem().toString());
                else if(sp_role.getSelectedItemPosition() == 1)
                    editor.putString(getString(R.string.key_start_role), sp_role.getSelectedItem().toString());
                else
                    editor.putString(getString(R.string.key_start_role), sp_role.getSelectedItem().toString());
                editor.putBoolean(getString(R.string.pref_glucose_unit_is_mmol), cb_unitmmol.isChecked());
                if(et_minimum.getText().toString().isEmpty())
                    editor.putString(getString(R.string.pref_glucose_target_min), et_minimum.getHint().toString());
                else
                    editor.putString(getString(R.string.pref_glucose_target_min), et_minimum.getText().toString());
                if(et_maximum.getText().toString().isEmpty())
                    editor.putString(getString(R.string.pref_glucose_target_max), et_maximum.getHint().toString());
                else
                    editor.putString(getString(R.string.pref_glucose_target_max), et_maximum.getText().toString());
                editor.commit();
                startActivity(new Intent(LobbyActivity.this, MainActivity.class));
                finish();
            } else {
                tv_infobox_user_name_input.setText(getString(R.string.name_please_enter_user_name));
            }
        }
    };
}
