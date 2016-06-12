package com.avaya.sdksampleapp;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ToggleButton;

/**
 * SettingsCallServiceFragment is used save and display application configuration and login status details.
 */
public class SettingsCallServiceFragment extends Fragment {
    private EditText addressEditText;
    private EditText portEditText;
    private EditText domainEditText;
    private ToggleButton tlsButton;
    private EditText extensionEditText;
    private EditText passwordEditText;

    private TextView loginStatusTextView;
    private FragmentReceiver fragmentReceiver;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.settings_call_service_fragment, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initializing all controls on screen when it ready
        addressEditText = (EditText) view.findViewById(R.id.setting_address);
        portEditText = (EditText) view.findViewById(R.id.setting_port);
        domainEditText = (EditText) view.findViewById(R.id.setting_domain);
        tlsButton = (ToggleButton) view.findViewById(R.id.setting_tls);
        extensionEditText = (EditText) view.findViewById(R.id.setting_extension);
        passwordEditText = (EditText) view.findViewById(R.id.setting_password);
        loginStatusTextView = (TextView) view.findViewById(R.id.login_status);
        final Button applyButton = (Button) view.findViewById(R.id.apply_button);

        // Adding listener for apply button, which will save new details and force recreate the user
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences settings = getActivity().getSharedPreferences(SDKManager.CLIENTSDK_TEST_APP_PREFS, Context.MODE_PRIVATE);
                SharedPreferences.Editor settingsEditor = settings.edit();
                settingsEditor.putString(SDKManager.ADDRESS, addressEditText.getText().toString());
                settingsEditor.putInt(SDKManager.PORT, Integer.valueOf(portEditText.getText().toString()));
                settingsEditor.putString(SDKManager.DOMAIN, domainEditText.getText().toString());
                settingsEditor.putBoolean(SDKManager.USE_TLS, tlsButton.isChecked());
                settingsEditor.putString(SDKManager.EXTENSION, extensionEditText.getText().toString());
                settingsEditor.putString(SDKManager.PASSWORD, passwordEditText.getText().toString());
                settingsEditor.apply();

                // We need to delete old user, because settings may changed. We can handle this by OnSharedPreferenceChangeListener
                // but as we don't have separate 'login' button let's recreate and re-login user each time 'apply' button pressed.
                // The following function will force logout if user was logged in. User will be recreated in SDKManager.onClientUserRemoved() once old user deleted.
                // Attempt to login will be done after new user creation.
                if (SDKManager.getInstance(getActivity()).getUser() == null) {
                    // If current user is already null we are just creating new user here, as we don't need to remove old one
                    SDKManager.getInstance(getActivity()).setupUserConfiguration();
                } else {
                    SDKManager.getInstance(getActivity()).delete(SDKManager.getInstance(getActivity()).isUserLoggedIn());
                }
            }
        });
    }

    private void setUserLoggedIn(boolean status) {
        // Based on loginStatus received let's update TextView to show "Logged in" / "Logged off" label
        loginStatusTextView.setText(status ? R.string.logged_in : R.string.logged_off);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Initialize broadcast receiver for login status and register receiver when resume to the screen
        fragmentReceiver = new FragmentReceiver();
        getActivity().registerReceiver(fragmentReceiver, new IntentFilter(SDKManager.LOGIN_RECEIVER));

        // Updating controls with values saved in SharedPreferences
        SharedPreferences settings = getActivity().getSharedPreferences(SDKManager.CLIENTSDK_TEST_APP_PREFS, Context.MODE_PRIVATE);
        addressEditText.setText(settings.getString(SDKManager.ADDRESS, ""));
        portEditText.setText(String.valueOf(settings.getInt(SDKManager.PORT, 5061)));
        domainEditText.setText(settings.getString(SDKManager.DOMAIN, ""));
        tlsButton.setChecked(settings.getBoolean(SDKManager.USE_TLS, true));
        extensionEditText.setText(settings.getString(SDKManager.EXTENSION, ""));
        passwordEditText.setText(settings.getString(SDKManager.PASSWORD, ""));

        // Check user login status and update UI when resume to the SettingsCallService screen
        setUserLoggedIn(SDKManager.getInstance(getActivity()).isUserLoggedIn());

        // Set fragment title
        getActivity().setTitle(R.string.call_service);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister broadcast receiver each time we are leaving current screen
        getActivity().unregisterReceiver(fragmentReceiver);
    }

    // Receiver of Broadcast messages. Used to update login status when settingsFragment is active.
    class FragmentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean loginStatus = intent.getBooleanExtra(SDKManager.LOGIN_TAG, false);
            setUserLoggedIn(loginStatus);
        }
    }
}
