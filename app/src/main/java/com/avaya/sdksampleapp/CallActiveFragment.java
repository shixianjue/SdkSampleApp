package com.avaya.sdksampleapp;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.avaya.clientservices.call.Call;
import com.avaya.clientservices.call.conference.ActiveParticipant;
import com.avaya.clientservices.common.DataSet;
import com.avaya.clientservices.common.DataSetChangeListener;
import com.avaya.clientservices.common.DataSetChangeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


/**
 * CallActiveFragment is used to display active call
 */
public class CallActiveFragment extends Fragment {

    private final String LOG_TAG = this.getClass().getSimpleName();

    private int callId;
    private boolean isVideoCall;

    private Timer durationTimer;
    private static int count = 0;

    private static final long TIMER_INITIAL_DELAY_MS = 1000;
    private static final long TIMER_STEP_PERIOD_MS = 1000;
    private static final long CALL_ENDED_EXIT_DELAY_MS = 2000;

    private TextView callState;
    private TextView callDuration;
    private ListView participantList;

    private ViewGroup videoFrame;

    private Button endCall;
    private EditText dtmfDigits;
    private SDKManager sdkManagerInstance;

    private VideoFrameFragment videoFrameFragment;
    private CallEventsReceiver callEventsReceiver;

    private boolean isFragmentPaused;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "Fragment#onCreate");
        // Initialize broadcast receiver for event messages that will be shown in toast
        callEventsReceiver = new CallEventsReceiver();

        callId = getArguments().getInt(SDKManager.CALL_ID);
        isVideoCall = getArguments().getBoolean(SDKManager.IS_VIDEO_CALL, false);
        // Get instance of SDKManager
        sdkManagerInstance = SDKManager.getInstance(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(LOG_TAG, "Fragment#onCreateView");
        return inflater.inflate(R.layout.active_call_fragment, container, false);
    }

    @Override
    public void onDestroyView() {
        Log.d(LOG_TAG, "Fragment#onDestroyView");
        // We should end all active calls if application destroys. It should be done in VOIP
        // service. Remove this code when you implement it in your application
        endCall(callId);
        super.onDestroyView();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(LOG_TAG, "Fragment#onViewCreated");

        callDuration = (TextView) view.findViewById(R.id.call_duration);
        participantList = (ListView) view.findViewById(R.id.participants_list);
        callState = (TextView) view.findViewById(R.id.call_state);
        videoFrame = (ViewGroup) view.findViewById(R.id.video_frame);

        if (isVideoCall) {
            upgradeToVideo();
        }

        // Send DTMF view initialization
        dtmfDigits = (EditText) view.findViewById(R.id.dtmf_digits);
        dtmfDigits.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                final int length = s.length();
                if (length > 0) {
                    // get last inputted symbol from EditText
                    char digit = s.charAt(length - 1);
                    // get the call
                    Call call = sdkManagerInstance.getCallWrapperByCallId(callId).getCall();
                    // Send the digit to the call
                    sdkManagerInstance.sendDTMF(call, digit);
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        // End call button initialization
        endCall = (Button) view.findViewById(R.id.end_call);
        endCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endCall(callId);
            }
        });
    }

    private void endCall(int callId) {
        // clean up video resources
        destroyVideoFragment();

        // Check if the call is not removed yet
        CallWrapper callWrapper = sdkManagerInstance.getCallWrapperByCallId(callId);
        if (callWrapper != null) {
            Call call = callWrapper.getCall();
            if (call != null) {
                // End the call
                call.end();
            }
        }
    }

    private void updateRemoteDisplayName(Call call) {
        //Update far-end name in the Participants view for P2P call
        if (call != null && !call.isConference()) {
            participantList.setAdapter(new ArrayAdapter<>(getActivity(),
                    android.R.layout.simple_list_item_1,
                    Collections.singletonList(call.getRemoteDisplayName())));
        }
    }

    private final DataSetChangeListener<ActiveParticipant> participantsChangedListener =
            new DataSetChangeListener<ActiveParticipant>() {
                @Override
                public void onDataSetChanged(DataSet<ActiveParticipant> dataSet,
                                             DataSetChangeType dataSetChangeType, List<Integer> list) {
                    // Update participants view for conference call
                    List<String> participants = new ArrayList<>();
                    for (ActiveParticipant activeParticipant : dataSet) {
                        participants.add(activeParticipant.getDisplayName());
                    }
                    participantList.setAdapter(new ArrayAdapter<>(getActivity(),
                            android.R.layout.simple_list_item_1, participants));
                }

                @Override
                public void onDataSetInvalidated(DataSet<ActiveParticipant> dataSet) {
                }
            };


    @Override
    public void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "Fragment#onResume");
        isFragmentPaused = false;
        // Set fragment title
        getActivity().setTitle(R.string.active_call);
        // Register receiver for call events
        getActivity().registerReceiver(callEventsReceiver,
                new IntentFilter(SDKManager.CALL_EVENTS_RECEIVER));

        // Destroy video frame if both video streams were stopped while the fragment was not active
        CallWrapper callWrapper = sdkManagerInstance.getCallWrapperByCallId(callId);
        if (callWrapper != null && !callWrapper.isLocalVideoActive() && !callWrapper.isRemoteVideoActive()) {
            destroyVideoFragment();
        }
        // Check if call ended to close current fragment if it was finished while fragment was not active.
        if (sdkManagerInstance.getCallWrapperByCallId(callId) == null) {
            getFragmentManager().popBackStack();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(LOG_TAG, "Fragment#onPause");
        isFragmentPaused = true;
        // Unregister broadcast receiver when leaving current screen
        getActivity().unregisterReceiver(callEventsReceiver);
    }

    // Upgrade call from Audio to Video
    private void upgradeToVideo() {
        Log.d(LOG_TAG, "Upgrading audio call to video");
        videoFrame.setVisibility(View.VISIBLE);
        Bundle bundle = new Bundle();
        bundle.putInt(SDKManager.CALL_ID, callId);
        videoFrameFragment = new VideoFrameFragment();
        videoFrameFragment.setArguments(bundle);
        getFragmentManager().beginTransaction().replace(R.id.video_frame, videoFrameFragment).commit();
    }

    // Downgrade call from Video to Audio
    private void destroyVideoFragment() {
        Log.d(LOG_TAG, "Destroying video frame");
        if (videoFrameFragment != null) {
            getFragmentManager().beginTransaction().detach(videoFrameFragment).commit();
            videoFrameFragment = null;
        }
        videoFrame.setVisibility(View.GONE);
    }

    // Receiver of Broadcast messages. Used to handle call events.
    class CallEventsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Call call = null;
            CallWrapper callWrapper = sdkManagerInstance.getCallWrapperByCallId(callId);
            if (callWrapper != null) {
                call = callWrapper.getCall();
            }

            String message = intent.getStringExtra(SDKManager.CALL_EVENT_TAG);
            Log.d(LOG_TAG, "Received event: " + message);
            switch (message) {
                case SDKManager.CALL_EVENT_RINGING:
                    // Update call state
                    callState.setText(R.string.ringing);
                    // Update participants view
                    updateRemoteDisplayName(call);
                    break;
                case SDKManager.CALL_EVENT_ESTABLISHED:
                    // Update call state
                    callState.setText(R.string.established);
                    // Allow input DTMF
                    dtmfDigits.setEnabled(true);
                    // Start call duration timer
                    durationTimer = new Timer();
                    durationTimer.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String result = String
                                            .format("%02d:%02d", (count / 60) % 60, count % 60);
                                    callDuration.setText(result);
                                    count++;
                                }
                            });
                        }
                    }, TIMER_INITIAL_DELAY_MS, TIMER_STEP_PERIOD_MS);
                    // Update participants view
                    if (call != null) {
                        if (!call.isConference()) {
                            updateRemoteDisplayName(call);
                        } else {
                            // We should subscribe to participants updates if the call has been started
                            // as conference for updating participants view
                            call.getConference().getParticipants()
                                    .addDataSetChangeListener(participantsChangedListener);
                        }
                    }
                    break;
                case SDKManager.CALL_EVENT_ENDED:
                    // Update call state
                    callState.setText(R.string.ended);
                    // Stop duration timer
                    if (durationTimer != null) {
                        durationTimer.cancel();
                    }
                    count = 0;
                    // Hide the end button
                    endCall.setVisibility(View.GONE);
                    // Disallow input new DTMF
                    dtmfDigits.setEnabled(false);
                    // Call has been stopped. Stop capturing and destroy video
                    // fragment. Video will be destroyed in VideoFrameFragment#onStop()
                    destroyVideoFragment();
                    // Unsubscribe from participants updates
                    if (call != null && call.isConference()) {
                        call.getConference().getParticipants()
                                .removeDataSetChangeListener(participantsChangedListener);
                    }

                    // We are on Active call screen and the call is ended. Let's close this screen after 2 sec
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // Check if fragment not paused to prevent popBackStack() execution in background
                            if (!isFragmentPaused) {
                                getFragmentManager().popBackStack();
                            }
                        }
                    }, CALL_ENDED_EXIT_DELAY_MS);
                    break;
                case SDKManager.CALL_EVENT_FAILED:
                    // Update call state and show the error
                    String error = intent.getStringExtra(SDKManager.EXCEPTION_TAG);
                    callState.setText(error);
                    break;
                case SDKManager.CALL_EVENT_CAPABILITIES_CHANGED:
                    updateRemoteDisplayName(call);
                    break;
                case SDKManager.CALL_EVENT_REMOTE_ADDRESS_CHANGED:
                    updateRemoteDisplayName(call);
                    break;
                case SDKManager.CALL_EVENT_REDIRECTED:
                    updateRemoteDisplayName(call);
                    break;
                case SDKManager.CALL_EVENT_VIDEO_CHANNELS_UPDATED:
                    int channelId = intent.getIntExtra(SDKManager.CHANNEL_ID_TAG, -1);
                    Log.d(LOG_TAG, "Video channel update received for channelId = " + channelId);
                    if (channelId != -1) {
                        // Starting video fragment as video channel is not empty now
                        // VideoFrameFragment will get start params from SDKManager and register
                        // it's own event receiver once started.
                        if (videoFrameFragment == null) {
                            upgradeToVideo();
                        }
                    }
                    if (intent.getBooleanExtra(SDKManager.STOP_VIDEO_TAG, false)) {
                        // Video has been removed from the call. Stop capturing and destroy video
                        // fragment. Video will be destroyed in VideoFrameFragment#onStop()
                        destroyVideoFragment();
                    }
                    break;

                case SDKManager.CALL_EVENT_CONFERENCE_STATUS_CHANGED:
                    // We should subscribe to participantsChangedListener for updating Participants
                    // view if the call has been moved to conference
                    if (call != null) {
                        if (intent.getBooleanExtra(SDKManager.CONFERENCE_TAG, false)) {
                            call.getConference().getParticipants()
                                    .addDataSetChangeListener(participantsChangedListener);
                        } else {
                            call.getConference().getParticipants()
                                    .removeDataSetChangeListener(participantsChangedListener);
                        }
                    }
                    break;
                default:
                    break;
            }
        }

    }
}
