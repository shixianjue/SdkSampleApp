package com.avaya.sdksampleapp;

import com.avaya.clientservices.call.Call;

/**
 * CallWrapper class is used to store video state flags required to manage video frame on UI
 */
public class CallWrapper {

    private Call call;

    private boolean videoCall = false;
    private boolean localVideoActive = false;
    private boolean remoteVideoActive = false;

    public CallWrapper(Call call) {
        this.call = call;
    }

    public CallWrapper(Call call, boolean videoCall) {
        this.call = call;
        setVideoCall(videoCall);
    }

    public Call getCall() {
        return call;
    }

    public boolean isLocalVideoActive() {
        return localVideoActive;
    }

    public boolean isRemoteVideoActive() {
        return remoteVideoActive;
    }

    public boolean isVideoCall() {
        return videoCall;
    }

    public void setVideoCall(boolean videoCall) {
        this.videoCall = videoCall;
    }

    public void setLocalVideoActive(boolean localVideoActive) {
        this.localVideoActive = localVideoActive;
    }

    public void setRemoteVideoActive(boolean remoteVideoActive) {
        this.remoteVideoActive = remoteVideoActive;
    }
}
