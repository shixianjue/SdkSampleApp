package com.avaya.sdksampleapp;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.avaya.clientservices.media.VideoInterface;
import com.avaya.clientservices.media.capture.VideoCamera;
import com.avaya.clientservices.media.capture.VideoCaptureController;
import com.avaya.clientservices.media.gui.Destroyable;
import com.avaya.clientservices.media.gui.PlaneViewGroup;
import com.avaya.clientservices.media.gui.VideoLayerLocal;
import com.avaya.clientservices.media.gui.VideoLayerRemote;
import com.avaya.clientservices.media.gui.VideoPlaneLocal;
import com.avaya.clientservices.media.gui.VideoPlaneRemote;
import com.avaya.clientservices.media.gui.VideoSink;
import com.avaya.clientservices.media.gui.VideoSource;

import java.util.ArrayList;
import java.util.List;

/**
 * VideoFrameFragment is used to display active call
 */
public class VideoFrameFragment extends Fragment {

    private final String LOG_TAG = this.getClass().getSimpleName();

    private int callId;

    private final List<Destroyable> destroyables = new ArrayList<>();
    private PlaneViewGroup videoPlaneViewGroup;
    private VideoLayerLocal videoLayerLocal;
    private VideoLayerRemote videoLayerRemote;
    private VideoPlaneLocal videoPlaneLocal;
    private VideoPlaneRemote videoPlaneRemote;
    private SDKManager sdkManagerInstance;

    private CallEventsReceiver callEventsReceiver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "Fragment#onCreate");

        callId = getArguments().getInt(SDKManager.CALL_ID, 0);

        // Initialize broadcast receiver for video events
        callEventsReceiver = new CallEventsReceiver();

        videoPlaneViewGroup = ((BaseActivity) getActivity()).getPlaneViewGroup();

        // Get instance of SDKManager
        sdkManagerInstance = SDKManager.getInstance(getActivity());

        // create the renders
        videoLayerLocal = new VideoLayerLocal();
        videoLayerRemote = new VideoLayerRemote();

        // create the local video plane
        videoPlaneLocal = new VideoPlaneLocal(getActivity());
        videoPlaneLocal.setLocalVideoLayer(videoLayerLocal);

        // create the remote video plane
        videoPlaneRemote = new VideoPlaneRemote(getActivity());
        videoPlaneRemote.setRemoteVideoLayer(videoLayerRemote);

        // set the remote video plane as a child beneath the local video plane
        videoPlaneLocal.setPlane(videoPlaneRemote);

        // maintain a list of all objects we need to destroy
        destroyables.add(videoLayerLocal);
        destroyables.add(videoLayerRemote);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(LOG_TAG, "Fragment#onCreateView");
        return inflater.inflate(R.layout.video_frame_layout, container, false);
    }

    private void startVideoTransmission(int channelId) {
        Log.d(LOG_TAG, "Start capturing.");
        VideoCaptureController videoCaptureController = SDKManager.getVideoCaptureController();
        VideoInterface videoInterface = sdkManagerInstance.getMediaServiceInstance()
                .getVideoInterface();
        final VideoSink videoSink = videoInterface.getLocalVideoSink(channelId);
        videoCaptureController.getVideoSource().setVideoSink(videoSink);
        videoCaptureController.setLocalVideoLayer(videoLayerLocal);
        // No need to destroy video capture and local video source as we are using a single instance of it
    }

    private void startVideoReception(int channelId) {
        Log.d(LOG_TAG, "Start receiving.");
        VideoInterface videoInterface = sdkManagerInstance.getMediaServiceInstance()
                .getVideoInterface();
        VideoSource videoSourceRemote = videoInterface.getRemoteVideoSource(channelId);
        if (videoSourceRemote != null) {
            videoSourceRemote.setVideoSink(videoLayerRemote);
            destroyables.add(videoSourceRemote);
        }
    }

    private void onCameraSelected(final VideoCamera videoCamera) {
        Log.d(LOG_TAG, "onCameraSelected = " + videoCamera);
        SDKManager.getVideoCaptureController().useVideoCamera(videoCamera, new Runnable() {
            public void run() {
                if (videoPlaneLocal != null) {
                    videoPlaneLocal.setLocalVideoHidden(videoCamera == null);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "Fragment#onResume");
        // Set fragment title
        getActivity().setTitle(R.string.active_call);

        // Register broadcast receiver for video events when resume to the screen

        getActivity().registerReceiver(callEventsReceiver,
                new IntentFilter(SDKManager.CALL_EVENTS_RECEIVER));
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(LOG_TAG, "Fragment#onPause");
        // Unregister broadcast receiver each time we are leaving current screen
        getActivity().unregisterReceiver(callEventsReceiver);
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(LOG_TAG, "Fragment#onStart");

        onCameraSelected(SDKManager.currentCamera);

        videoPlaneViewGroup.setPlane(videoPlaneLocal);
        videoPlaneViewGroup.setVisibility(ViewGroup.VISIBLE);

        // Get location of videoFrame where we are going to show video stream and set them to
        // videoPlaneViewGroup
        videoPlaneViewGroup.setLayoutParams(getView().getLayoutParams());

        // If video streams initialized earlier than fragment started then let's start send/receive
        // video otherwise we will be waiting for broadcast notification to start
        CallWrapper callWrapper = sdkManagerInstance.getCallWrapperByCallId(callId);
        if(callWrapper != null) {
            if (callWrapper.isLocalVideoActive()) {
                startVideoTransmission(SDKManager.getActiveVideoChannel());
            }

            if (callWrapper.isRemoteVideoActive()) {
                startVideoReception(SDKManager.getActiveVideoChannel());
            }
        }

    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(LOG_TAG, "Fragment#onStop");
        videoPlaneViewGroup.setVisibility(ViewGroup.INVISIBLE);
        videoPlaneViewGroup.setPlane(null);

        // Restore render to the whole screen
        videoPlaneViewGroup.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        onCameraSelected(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(LOG_TAG, "Fragment#onDestroyView");
        // Cleanup limited resources of video render manually
        for (Destroyable destroyable : destroyables) {
            destroyable.destroy();
        }
    }

    // Receiver of Broadcast messages. Used to handle call events.
    class CallEventsReceiver extends BroadcastReceiver {

        // Fragment already created and shown on the screen at the time. We still need to update video fragment
        // when receive updates for video channel.
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra(SDKManager.CALL_EVENT_TAG);
            int channelId = intent.getIntExtra(SDKManager.CHANNEL_ID_TAG, -1);
            if (message != null && message.equals(SDKManager.CALL_EVENT_VIDEO_CHANNELS_UPDATED)) {

                if (intent.getBooleanExtra(SDKManager.START_LOCAL_VIDEO_TAG, false)) {
                    startVideoTransmission(channelId);
                }
                if (intent.getBooleanExtra(SDKManager.START_REMOTE_VIDEO_TAG, false)) {
                    startVideoReception(channelId);
                }
            }
        }
    }
}

