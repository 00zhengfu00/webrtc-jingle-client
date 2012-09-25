package com.tuenti.voice.example;

import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;

import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.tuenti.voice.core.BuddyListState;
import com.tuenti.voice.core.CallState;
import com.tuenti.voice.core.VoiceClient;
import com.tuenti.voice.core.VoiceClientEventCallback;
import com.tuenti.voice.core.VoiceClientEventHandler;
import com.tuenti.voice.core.XmppError;
import com.tuenti.voice.core.XmppState;

import com.tuenti.voice.example.service.VoiceClientService;
import com.tuenti.voice.example.service.IVoiceClientService;
import com.tuenti.voice.example.service.IVoiceClientServiceCallback;

public class VoiceClientController
{
    IVoiceClientService mService;
    private Context mContext;
    
    private static final String TAG = "controller-libjingle-webrtc";

    private boolean mIsBound = false;
    
    public VoiceClientController(Context context){
        mContext = context;
    }
    
    public void bind() {
        mContext.bindService(new Intent(IVoiceClientService.class.getName()),
                mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }
 
    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = IVoiceClientService.Stub.asInterface(service);
            try {
                mService.registerCallback(mCallback);
            } catch ( RemoteException $e ) {

            }
            // We want to monitor the service for as long as we are
            // connected to it.
            Log.i( TAG, "Connected to service" );
        }

        public void onServiceDisconnected(ComponentName className) {
            try {
                mService.unregisterCallback(mCallback);
            } catch ( RemoteException $e ) {

            }
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
            Log.i( TAG, "Disconnected from service" );
        }
    };

    /**
     * This implementation is used to receive callbacks from the remote
     * service.
     */
    private IVoiceClientServiceCallback mCallback = new IVoiceClientServiceCallback.Stub() {
        /**
         * This is called by the remote service regularly to tell us about
         * new values.  Note that IPC calls are dispatched through a thread
         * pool running in each process, so the code executing here will
         * NOT be running in our main thread like most other things -- so,
         * to update the UI, we need to use a Handler to hop over there.
         */
        public void sendBundle( Bundle bundle ){
            /*Message msg = Message.obtain();
            msg.what = bundle.getInt("what");
            msg.setData(bundle);
            mHandler.sendMessage(msg);*/
            //Implement me for intent or whatever we do here.
        }

        public void dispatchIntent( Intent intent ){
            Intent newIntent = intent.cloneFilter();
            mContext.startActivity(newIntent);
        }
    };
    
    public void onDestroy(){
        if( mIsBound ){
            mContext.unbindService(mConnection);
            mIsBound = false;
        }
    }
}
