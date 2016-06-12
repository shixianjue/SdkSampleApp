package com.avaya.sdksampleapp;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;

import com.avaya.clientservices.call.Call;
import com.avaya.clientservices.call.feature.BusyIndicator;
import com.avaya.clientservices.call.feature.CallFeatureService;
import com.avaya.clientservices.call.feature.CallFeatureServiceListener;
import com.avaya.clientservices.call.feature.CallPickupAlertParameters;
import com.avaya.clientservices.call.feature.EnhancedCallForwardingStatus;
import com.avaya.clientservices.call.feature.FeatureCompletionHandler;
import com.avaya.clientservices.call.feature.FeatureException;
import com.avaya.clientservices.call.feature.FeatureStatusParameters;
import com.avaya.clientservices.call.feature.FeatureType;

import java.util.List;

/**
 * CallInitFragment is used to show call options available for the user and can be used to make outgoing audio\video call
 */
public class CallInitFragment extends Fragment implements CallFeatureServiceListener {

    private final String LOG_TAG = this.getClass().getSimpleName();

    private CallActiveFragment callActiveFragment;
    private CallFeatureService featureService;
    private SDKManager sdkManagerInstance;
    private CompoundButton.OnCheckedChangeListener mListener;
    private Switch sendAllCalls;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.call_init_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get instance of SDKManager
        sdkManagerInstance = SDKManager.getInstance(getActivity());

        // Initializing all controls on screen when it ready
        final EditText calledParty = (EditText) view.findViewById(R.id.called_party_number);
        final Button makeAudioCall = (Button) view.findViewById(R.id.make_audio_call);
        final Button makeVideoCall = (Button) view.findViewById(R.id.make_video_call);
        sendAllCalls = (Switch) view.findViewById(R.id.send_all_call_switch);

        // Adding control listeners
        makeAudioCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                makeCall(calledParty.getText().toString(), false);
                hideKeyboard(view);
            }
        });

        makeVideoCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                makeCall(calledParty.getText().toString(), true);
                hideKeyboard(view);
            }
        });

        // Initializing send all call feature
        featureService = sdkManagerInstance.getUser().getCallFeatureService();

        // Register CallFeatureServiceListener. It will be used to handle Send All Calls status changed outside the app.
        featureService.addListener(this);

        Log.d(LOG_TAG, "getSendAllCallsCapability = " + featureService.getFeatureCapability(FeatureType.SEND_ALL_CALLS).toString());

        boolean isSendAllCallsAllowed = featureService.getFeatureCapability(FeatureType.SEND_ALL_CALLS).isAllowed();

        if (!isSendAllCallsAllowed) {
            // Disable button if feature is not allowed
            sendAllCalls.setEnabled(false);
        } else {
            // Initialize switch view with current feature status
            sendAllCalls.setChecked(featureService.isSendAllCallsEnabled());
            // Create listener for switch view
            mListener = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(final CompoundButton sendAllCalls, boolean isChecked) {
                    featureService.setSendAllCallsEnabled(isChecked, new FeatureCompletionHandler() {
                        @Override
                        public void onSuccess() {
                            Log.d(LOG_TAG, "SAC feature state is changed");
                        }

                        @Override
                        public void onError(FeatureException error) {
                            Log.e(LOG_TAG, "SAC feature state cannot be changed. Error: " + error.getMessage());
                            // Back to previous switch view state
                            sendAllCalls.setOnCheckedChangeListener(null);
                            sendAllCalls.toggle();
                            sendAllCalls.setOnCheckedChangeListener(mListener);
                        }
                    });
                }
            };
            sendAllCalls.setOnCheckedChangeListener(mListener);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Remove CallFeatureServiceListener when close fragment
        featureService.removeListener(this);
    }

    private void makeCall(String calledParty, boolean isVideoCall) {

        // Initializing callActiveFragment which will be used to show active call details
        callActiveFragment = new CallActiveFragment();

        // Create call.
        CallWrapper callWrapper = sdkManagerInstance.createCall(calledParty);

        Bundle bundle = new Bundle();
        bundle.putInt(SDKManager.CALL_ID, callWrapper.getCall().getCallId());
        bundle.putBoolean(SDKManager.IS_VIDEO_CALL, isVideoCall);
        callActiveFragment.setArguments(bundle);

        // Open active call fragment
        getFragmentManager().beginTransaction().replace(R.id.dynamic_view, callActiveFragment, SDKManager.ACTIVE_CALL_FRAGMENT_TAG).addToBackStack(null).commit();

        callWrapper.setVideoCall(isVideoCall);

        // Start the call
        sdkManagerInstance.startCall(callWrapper);
    }

    private void hideKeyboard(View view) {
        // Hide keyboard
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (!imm.isActive()) {
            return;
        }

        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        view.clearFocus();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Set fragment title
        getActivity().setTitle(R.string.calls_item);
    }

    /*
     * CallFeatureServiceListener section.
     */

    @Override
    public void onSendAllCallsStatusChanged(CallFeatureService callFeatureService, boolean b, String s) {
        Log.d(LOG_TAG, "onSendAllCallsStatusChanged to enabled=" + b);
        sendAllCalls.setOnCheckedChangeListener(null);
        sendAllCalls.setChecked(b);
        sendAllCalls.setOnCheckedChangeListener(mListener);
    }

    @Override
    public void onCallFeatureServiceAvailable(CallFeatureService callFeatureService) {
        Log.d(LOG_TAG, "onCallFeatureServiceAvailable");
    }

    @Override
    public void onCallFeatureServiceUnavailable(CallFeatureService callFeatureService) {
        Log.d(LOG_TAG, "onCallFeatureServiceUnavailable");
    }

    @Override
    public void onFeatureListChanged(CallFeatureService callFeatureService) {
        Log.d(LOG_TAG, "onFeatureListChanged");
    }

    @Override
    public void onFeatureCapabilityChanged(CallFeatureService callFeatureService, FeatureType featureType) {
        Log.d(LOG_TAG, "onFeatureCapabilityChanged");
    }

    @Override
    public void onAvailableFeatures(CallFeatureService callFeatureService, List<FeatureType> list) {
        Log.d(LOG_TAG, "onAvailableFeatures");
    }

    @Override
    public void onFeatureStatus(CallFeatureService callFeatureService, List<FeatureStatusParameters> list) {
        Log.d(LOG_TAG, "onFeatureStatus");
    }

    @Override
    public void onFeatureStatusChanged(CallFeatureService callFeatureService, FeatureStatusParameters featureStatusParameters) {
        Log.d(LOG_TAG, "onFeatureStatusChanged");
    }

    @Override
    public void onCallForwardingStatusChanged(CallFeatureService callFeatureService, boolean b, String s, String s1) {
        Log.d(LOG_TAG, "onCallForwardingStatusChanged");
    }

    @Override
    public void onCallForwardingBusyNoAnswerStatusChanged(CallFeatureService callFeatureService, boolean b, String s, String s1) {
        Log.d(LOG_TAG, "onCallForwardingBusyNoAnswerStatusChanged");
    }

    @Override
    public void onEnhancedCallForwardingStatusChanged(CallFeatureService callFeatureService, String s, EnhancedCallForwardingStatus enhancedCallForwardingStatus) {
        Log.d(LOG_TAG, "onEnhancedCallForwardingStatusChanged");
    }

    @Override
    public void onCallPickupAlertStatusChanged(CallFeatureService callFeatureService, CallPickupAlertParameters callPickupAlertParameters) {
        Log.d(LOG_TAG, "onCallPickupAlertStatusChanged");
    }

    @Override
    public void onEC500StatusChanged(CallFeatureService callFeatureService, boolean b) {
        Log.d(LOG_TAG, "onEC500StatusChanged");
    }

    @Override
    public void onAutoCallbackStatusChanged(CallFeatureService callFeatureService, boolean b) {
        Log.d(LOG_TAG, "onAutoCallbackStatusChanged");
    }

    @Override
    public void onBusyIndicatorChanged(CallFeatureService callFeatureService, BusyIndicator busyIndicator) {
        Log.d(LOG_TAG, "onBusyIndicatorChanged");
    }
}