package com.tuenti.voice.example.service;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.media.AudioManager;
import android.content.Context;

import com.tuenti.voice.core.BuddyListState;
import com.tuenti.voice.core.CallState;
import com.tuenti.voice.core.VoiceClient;
import com.tuenti.voice.core.VoiceClientEventCallback;
import com.tuenti.voice.core.VoiceClientEventHandler;
import com.tuenti.voice.core.XmppError;
import com.tuenti.voice.core.XmppState;
import com.tuenti.voice.core.IVoiceClientServiceInt;
import com.tuenti.voice.example.R;
import com.tuenti.voice.example.data.Call;
import com.tuenti.voice.example.util.RingManager;
import com.tuenti.voice.example.ui.dialog.IncomingCallDialog;
import com.tuenti.voice.example.ui.activity.CallInProgressActivity;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.lang.Long;
import android.os.Build;
import android.preference.PreferenceManager;

public class VoiceClientService extends Service implements
        IVoiceClientServiceInt, VoiceClientEventCallback {
    private VoiceClient mClient;

    private static final String TAG = "s-libjingle-webrtc";

    private HashMap<Long, Call> mCallMap = new HashMap<Long, Call>();

    private boolean mCallInProgress = false;

    private long mCurrentCallId = 0;

    private Handler mHandler;

    private AudioManager mAudioManager;

    private RingManager mRingManager;

    private SharedPreferences mSettings;
    
    //Pending login values
    private String mUsername;
    private String mPassword;
    private String mXmppHost;
    private int mXmppPort = 0;
    private boolean mXmppUseSsl = false;

    /**
     * This is a list of callbacks that have been registered with the service.
     * Note that this is package scoped (instead of private) so that it can be
     * accessed more efficiently from inner classes.
     */
    final RemoteCallbackList<IVoiceClientServiceCallback> mCallbacks = new RemoteCallbackList<IVoiceClientServiceCallback>();

    // --------------------- Service Methods
    // ---------------------------------------
    @Override
    public void onCreate() {
        super.onCreate();
        // Set default preferences
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        initClientWrapper();
        initAudio();
        // Set default preferences
        // mSettings = PreferenceManager.getDefaultSharedPreferences( this );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.e(TAG, "onBind Received");
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCallbacks.kill();
        // mBuddyList.clear();
        mClient = null;
    }

    private static String cleanJid(String jid) {
        if (jid == null) {
            return "";
        }

        int index = jid.indexOf('/');
        if (index > 0) {
            return jid.substring(0, index);
        }
        return jid;
    }

    private void initAudio() {
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    private void setAudioForCall() {
        mAudioManager
                .setMode((Build.VERSION.SDK_INT < 11) ? AudioManager.MODE_IN_CALL
                        : AudioManager.MODE_IN_COMMUNICATION);
        mAudioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
    }

    private void resetAudio() {
        mAudioManager.setMode(AudioManager.MODE_NORMAL);
        mAudioManager.abandonAudioFocus(null);
    }

    public Intent getCallIntent(String intentString, long callId,
            String remoteJid) {
        Intent intent = new Intent(intentString);
        intent.putExtra("callId", callId);
        intent.putExtra("remoteJid", remoteJid);
        Call call = mCallMap.get(new Long(callId));
        intent.putExtra("isHeld", call.isHeld());
        intent.putExtra("isMuted", call.isMuted());
        return intent;
    }

    public void initCallState(long callId, String remoteJid) {
        mCallInProgress = true;
        mCurrentCallId = callId;
        mCallMap.put(new Long(callId), new Call(callId, remoteJid));
    }

    public void outgoingCall(long callId, String remoteJid) {
        initCallState(callId, remoteJid);

        Intent dialogIntent = new Intent(getBaseContext(),
                CallInProgressActivity.class);
        dialogIntent.putExtra("callId", callId);
        dialogIntent.putExtra("remoteJid", remoteJid);
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getApplication().startActivity(dialogIntent);
        startRing(false, false);

        // Intent call started, show call in progress activity
        // Ring in the headphone
    }

    public void incomingCall(long callId, String remoteJid) {
        initCallState(callId, remoteJid);
        startRing(true, false);
        startIncomingCallDialog(callId, remoteJid);
        // show alert pop up for incoming call + tray notification
        // Ringer.
    }

    public void rejectCall(long callId) {
        // Cancel notification alert for incoming call + tray
    }

    public void callStarted(long callId) {
        Log.i("TAG", "call started-----");
        stopRing();
        Call call = mCallMap.get(new Long(callId));
        call.startCallTimer();
        String remoteJid = call.getRemoteJid();
        setAudioForCall();
        startCallInProgressActivity(callId, remoteJid);
        dispatchCallState(CallUIIntent.CALL_STARTED, callId,
                call.getRemoteJid());
        // Intent call started
        // start timer on method updateCallUI every second.
        // Change notification to call in progress notification, that points to
        // call in progress activity on click.
    }

    public void updateCallUI() {
        if (mCurrentCallId > 0) {
            // update duration of call in tray notification via changeData.
            // send message to controller, in case there is one, to tell it to
            // update call duration on the UI.
        }
    }

    public void endCall(long callId, int reason) {
        // Makes sure we don't change state for calls
        // we decline as busy while in a call.
        if (mCallMap.containsKey(new Long(callId))) {
            mCallInProgress = false;
            mCurrentCallId = 0;
            Call call = mCallMap.get(new Long(callId));
            mCallMap.remove(new Long(callId));
            stopRing();
            resetAudio();
            dispatchCallState(CallUIIntent.CALL_ENDED, callId,
                    call.getRemoteJid());
            // Store reason in call history with jid.
            // Intent call ended, store in history, return call time
            // cancel notification
            long callTime = call.getElapsedTime();
        }
    }

    public void acceptCall(long callId, String remoteJid) {
        // dispatchIntent(getCallIntent(CallUIIntent.CALL_PROGRESS, callId,
        // remoteJid));
    }

    /*
     * Only called on XMPP disconnect as a cleanup operation.
     */
    public void endAllCalls() {
        Iterator iter = mCallMap.keySet().iterator();
        while (iter.hasNext()) {
            Long key = (Long) iter.next();
            endCall(key, 0);
            // TODO(Luke): Add reason
        }
    }

    public void startCallInProgressActivity(long callId, String remoteJid) {
        Intent dialogIntent = new Intent(getBaseContext(),
                CallInProgressActivity.class);
        dialogIntent.putExtra("callId", callId);
        dialogIntent.putExtra("remoteJid", remoteJid);
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getApplication().startActivity(dialogIntent);
    }

    public void startIncomingCallDialog(long callId, String remoteJid) {
        Intent dialogIntent = new Intent(getBaseContext(),
                IncomingCallDialog.class);
        dialogIntent.putExtra("callId", callId);
        dialogIntent.putExtra("remoteJid", remoteJid);
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getApplication().startActivity(dialogIntent);
    }

    public void dispatchCallState(String callState, long callId,
            String remoteJid) {
        Intent intent = new Intent(callState);
        intent.putExtra("callId", callId);
        intent.putExtra("remoteJid", remoteJid);
        dispatchLocalIntent(intent);
    }

    public void startRing(boolean isIncoming, boolean callInProgress) {
        stopRing();
        mRingManager = new RingManager(getApplicationContext(), isIncoming,
                callInProgress);
    }

    public void stopRing() {
        if (mRingManager != null) {
            mRingManager.stop();
            mRingManager = null;
        }
    }

    // --------------------- Interface VoiceClientEventCallback
    // ---------------------
    @Override
    public void handleCallStateChanged(int state, String remoteJid, long callId) {
        remoteJid = cleanJid(remoteJid);
        switch (CallState.fromInteger(state)) {
        case SENT_INITIATE:
            Log.i(TAG, "Outgoing call");
            outgoingCall(callId, remoteJid);
            break;
        case RECEIVED_INITIATE:
            Log.i(TAG, "Incoming call");
            if (mCallInProgress == false) {
                incomingCall(callId, remoteJid);
            } else {
                mClient.declineCall(callId, true);// Decline busy;
            }
            break;
        case SENT_TERMINATE:
        case RECEIVED_TERMINATE:
        case SENT_BUSY:
        case RECEIVED_BUSY:
        case SENT_REJECT:
        case RECEIVED_REJECT:
            Log.i(TAG, "Call ended");
            endCall(callId, 0);// Add reason to end call.
            break;
        case RECEIVED_ACCEPT:
            Log.i(TAG, "Call accepted");
            acceptCall(callId, remoteJid);
        case IN_PROGRESS:
            Log.i(TAG, "IN_PROGRESS");
            callStarted(callId);
            break;
        case DE_INIT:
            Log.i(TAG, "DE_INIT");
            break;
        }
        Log.i(TAG, "call state ------------------" + state);
    }

    @Override
    public void handleXmppError(int error) {
        switch (XmppError.fromInteger(error)) {
        case XML:
            Log.e(TAG, "Malformed XML or encoding error");
            break;
        case STREAM:
            Log.e(TAG, "XMPP stream error");
            break;
        case VERSION:
            Log.e(TAG, "XMPP version error");
            break;
        case UNAUTHORIZED:
            Log.e(TAG,
                    "User is not authorized (Check your username and password)");
            break;
        case TLS:
            Log.e(TAG, "TLS could not be negotiated");
            break;
        case AUTH:
            Log.e(TAG, "Authentication could not be negotiated");
            break;
        case BIND:
            Log.e(TAG, "Resource or session binding could not be negotiated");
            break;
        case CONNECTION_CLOSED:
            Log.e(TAG, "Connection closed by output handler.");
            break;
        case DOCUMENT_CLOSED:
            Log.e(TAG, "Closed by </stream:stream>");
            break;
        case SOCKET:
            Log.e(TAG, "Socket error");
            break;
        }
    }

    @Override
    public void handleXmppStateChanged(int state) {
        Intent intent;
        switch (XmppState.fromInteger(state)) {
        case NONE:
            Log.e(TAG, "xmpp None state");
            if (mUsername != null){
                mClient.login(mUsername, mPassword, mXmppHost, mXmppPort, mXmppUseSsl);
                mUsername = null;
                mPassword = null;
                mXmppHost = null;
                mXmppPort = 0;
                mXmppUseSsl = false;
            }
        case START:
            // changeStatus( "connecting..." );
            break;
        case OPENING:
            // changeStatus( "logging in..." );
            break;
        case OPEN:
            intent = new Intent(CallUIIntent.LOGGED_IN);
            dispatchLocalIntent(intent);
            break;
        case CLOSED:
            intent = new Intent(CallUIIntent.LOGGED_OUT);
            dispatchLocalIntent(intent);
            endAllCalls();
            mClient.release();
            // Intent disconnected.
            // - Connection listener can handle this event.
            // - When we have a connection, it will try to
            // login again.
            break;
        }
    }

    @Override
    public void handleBuddyListChanged(int state, String remoteJid) {
        switch (BuddyListState.fromInteger(state)) {
        case ADD:
            Log.v(TAG, "Adding buddy " + remoteJid);
            // Intent add buddy
            // mBuddyList.add(remoteJid);
            break;
        case REMOVE:
            Log.v(TAG, "Removing buddy" + remoteJid);
            // Intent remove buddy
            // mBuddyList.remove(remoteJid);
            break;
        case RESET:
            Log.v(TAG, "Reset buddy list");
            // intent reset buddy list
            // mBuddyList.clear();
            break;
        }
    }

    public void dispatchLocalIntent(Intent intent) {
        final int N = mCallbacks.beginBroadcast();
        for (int i = 0; i < N; i++) {
            try {
                mCallbacks.getBroadcastItem(i).dispatchLocalIntent(intent);
            } catch (RemoteException e) {
                // The RemoteCallbackList will take care of removing
                // the dead object for us.
            }
        }
        mCallbacks.finishBroadcast();
    }

    public void sendBundle(Bundle bundle) {
        final int N = mCallbacks.beginBroadcast();
        for (int i = 0; i < N; i++) {
            try {
                mCallbacks.getBroadcastItem(i).sendBundle(bundle);
            } catch (RemoteException e) {
                // The RemoteCallbackList will take care of removing
                // the dead object for us.
            }
        }
        mCallbacks.finishBroadcast();
    }

    /*
     * Binder Interface implementation.
     */
    private final IVoiceClientService.Stub mBinder = new IVoiceClientService.Stub() {
        public void acceptCall(long callId) throws RemoteException {
            mClient.acceptCall(callId);
        }

        public void call(String remoteJid) throws RemoteException {
            mClient.call(remoteJid);
        }

        public void declineCall(long callId, boolean busy)
                throws RemoteException {
            mClient.declineCall(callId, busy);
        }

        public void toggleMute(long callId) throws RemoteException {
            Call call = mCallMap.get(new Long(callId));
            if (call != null) {
                call.setMute(call.isMuted());
                mClient.muteCall(callId, call.isMuted());
            }
        }

        public void toggleHold(long callId) throws RemoteException {
            Call call = mCallMap.get(new Long(callId));
            if (call != null) {
                call.setHold(call.isHeld());
                mClient.holdCall(callId, call.isHeld());
            }
        }

        public void endCall(long callId) throws RemoteException {
            mClient.endCall(callId);
        }

        public void login(String username, String password, String xmppHost,
                int xmppPort, boolean xmppUseSsl) throws RemoteException {
            String stunServer = getStringPref(R.string.stunserver_key,
                    R.string.stunserver_value);
            String relayServer = getStringPref(R.string.relayserver_key,
                    R.string.relayserver_value);
            String turnServer = getStringPref(R.string.turnserver_key,
                    R.string.turnserver_value);
            mClient.init(stunServer, relayServer, relayServer, relayServer,
                    turnServer);
            mUsername = username;
            mPassword = password;
            mXmppHost = xmppHost;
            mXmppPort = xmppPort;
            mXmppUseSsl = xmppUseSsl;
        }

        public void logout() throws RemoteException {
            mClient.logout();
        }

        /*
         * public void getBuddyList() throws RemoteException { return
         * mBuddyList; //Implement me. }
         */
        /*
         * public CallHistoryList getCallHistory() throws RemoteException {
         * return mCallHistory; // Implement me., list of remoteJids with
         * states, call duration. // Should probably be stored in phone storage.
         * }
         */
        public void registerCallback(IVoiceClientServiceCallback cb) {
            if (cb != null)
                mCallbacks.register(cb);
        }

        public void unregisterCallback(IVoiceClientServiceCallback cb) {
            if (cb != null)
                mCallbacks.unregister(cb);
        }
    };

    private void initClientWrapper() {
        mClient = VoiceClient.getInstance();
        mHandler = new VoiceClientEventHandler(this);
        mClient.setHandler(mHandler);
    }

    private boolean getBooleanPref(int key, int defaultValue) {
        return Boolean.valueOf(getStringPref(key, defaultValue));
    }

    private int getIntPref(int key, int defaultValue) {
        return Integer.valueOf(getStringPref(key, defaultValue));
    }

    private String getStringPref(int key, int defaultValue) {
        return mSettings.getString(getString(key), getString(defaultValue));
    }
}
