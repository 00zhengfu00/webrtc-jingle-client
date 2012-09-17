package com.tuenti.voice.example.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.tuenti.voice.core.BuddyListState;
import com.tuenti.voice.core.CallState;
import com.tuenti.voice.core.VoiceClient;
import com.tuenti.voice.core.VoiceClientEventCallback;
import com.tuenti.voice.core.VoiceClientEventHandler;
import com.tuenti.voice.core.XmppError;
import com.tuenti.voice.core.XmppState;
import com.tuenti.voice.example.R;
import com.tuenti.voice.example.ui.dialog.IncomingCallDialog;

public class VoiceClientActivity
    extends Activity
    implements View.OnClickListener, VoiceClientEventCallback
{
// ------------------------------ FIELDS ------------------------------

    private static final String TAG = "VoiceClientActivity";

    // Template Google Settings
    //private static final String TO_USER = "user@gmail.com";
private static final String TO_USER = "nicktuentitesting2@gmail.com";
//private static final String TO_USER = "alexander@tuenti.com";

    //private static final String MY_USER = "username@mydomain.com";
private static final String MY_USER = "nicktuentitesting@gmail.com";

    //private static final String MY_PASS = "pass";
private static final String MY_PASS = "20testing";

    private AudioManager mAudioManager;

    private VoiceClient mClient;

    private Ringtone mRingerPlayer;

    private Vibrator mVibrator;

    private SharedPreferences mSettings;

    private long currentCallId = 0;

    private boolean callInProgress = false;

    private static String cleanJid( String jid )
    {
        if ( jid == null )
        {
            return "";
        }

        int index = jid.indexOf( '/' );
        if ( index > 0 )
        {
            return jid.substring( 0, index );
        }
        return jid;
    }

// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface OnClickListener ---------------------

    public void onClick( View view )
    {
        switch ( view.getId() )
        {
            case R.id.init_btn:
                initClient();
                break;
            case R.id.release_btn:
                mClient.release();
                break;
            case R.id.login_btn:
                login();
                break;
            case R.id.logout_btn:
                mClient.logout();
                break;
            case R.id.place_call_btn:
                mClient.call( TO_USER );
                break;
            case R.id.hang_up_btn:
                if( currentCallId > 0){
                   mClient.endCall(currentCallId);
                }
                break;
        }
    }

// --------------------- Interface VoiceClientEventCallback ---------------------

    @Override
    public void handleCallStateChanged( int state, String remoteJid, long callId )
    {
        switch ( CallState.fromInteger( state ) )
        {
            case SENT_INITIATE:
                currentCallId = callId;
                startOutgoingRinging();
                changeStatus( "calling..." );
                break;
            case SENT_TERMINATE:
                stopRinging();
                changeStatus( "call hang up" );
                break;
            case RECEIVED_INITIATE:
                if( callInProgress ) {
                    // Decline as busy, until we support UI to handle this case.
                    mClient.declineCall(callId, true);
                } else {
                    currentCallId = callId;
                    displayIncomingCall( remoteJid, callId );
                }
                break;
            case RECEIVED_ACCEPT:
                currentCallId = callId;
                stopRinging();
                changeStatus( "call answered" );
                playNotification();
                break;
            case RECEIVED_REJECT:
                callInProgress = false;
                currentCallId = 0;
                stopRinging();
                changeStatus( "call rejected" );
                //TODO(jreyes): Close the dialog if they haven't answered.
                break;
            case RECEIVED_BUSY:
                callInProgress = false;
                currentCallId = 0;
                stopRinging();
                changeStatus( "user busy" );
                //TODO(jreyes): Close the dialog if they haven't answered.
                break;
            case RECEIVED_TERMINATE:
                callInProgress = false;
                currentCallId = 0;
                stopRinging();
                changeStatus( "other side hung up" );
                playNotification();
                //TODO(jreyes): Close the dialog if they haven't answered.
                break;
            case IN_PROGRESS:
                callInProgress = true;
                stopRinging();
                setAudioForCall();
                changeStatus( "call in progress" );
                break;
            case DE_INIT:
                resetAudio();
                break;
        }
    }

    @Override
    public void handleXmppError( int error )
    {
        switch ( XmppError.fromInteger( error ) )
        {
            case XML:
                Log.e( TAG, "Malformed XML or encoding error" );
                break;
            case STREAM:
                Log.e( TAG, "XMPP stream error" );
                break;
            case VERSION:
                Log.e( TAG, "XMPP version error" );
                break;
            case UNAUTHORIZED:
                Log.e( TAG, "User is not authorized (Check your username and password)" );
                break;
            case TLS:
                Log.e( TAG, "TLS could not be negotiated" );
                break;
            case AUTH:
                Log.e( TAG, "Authentication could not be negotiated" );
                break;
            case BIND:
                Log.e( TAG, "Resource or session binding could not be negotiated" );
                break;
            case CONNECTION_CLOSED:
                Log.e( TAG, "Connection closed by output handler." );
                break;
            case DOCUMENT_CLOSED:
                Log.e( TAG, "Closed by </stream:stream>" );
                break;
            case SOCKET:
                Log.e( TAG, "Socket error" );
                break;
        }
    }

    @Override
    public void handleXmppStateChanged( int state )
    {
        switch ( XmppState.fromInteger( state ) )
        {
            case START:
                changeStatus( "connecting..." );
                break;
            case OPENING:
                changeStatus( "logging in..." );
                break;
            case OPEN:
                changeStatus( "logged in..." );
                break;
            case CLOSED:
                changeStatus( "logged out... " );
                break;
        }
    }

    @Override
    public void handleBuddyListChanged( int state , String remoteJid)
    {
        switch ( BuddyListState.fromInteger( state ) ){
            case ADD:
                Log.v( TAG, "Adding buddy " + remoteJid );
                break;
            case REMOVE:
                Log.v( TAG, "Removing buddy" + remoteJid );
                break;
            case RESET:
                Log.v( TAG, "Reset buddy list" );
                break;
        }

    }

// -------------------------- OTHER METHODS --------------------------

    @Override
    public void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.main );

        // Set default preferences
        mSettings = PreferenceManager.getDefaultSharedPreferences( this );

        initAudio();
        initClientWrapper();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        mClient.destroy();
    }

    private void changeStatus( String status )
    {
        ( (TextView) findViewById( R.id.status_view ) ).setText( status );
    }

    private void displayIncomingCall( String remoteJid, long callId )
    {
        // start ringing
        startIncomingRinging();

        // and display the incoming call dialog
        Dialog incomingCall = new IncomingCallDialog( this, mClient, cleanJid( remoteJid ), callId ).create();
        incomingCall.show();
    }

    private boolean getBooleanPref( int key, int defaultValue )
    {
        return Boolean.valueOf( getStringPref( key, defaultValue ) );
    }

    private int getIntPref( int key, int defaultValue )
    {
        return Integer.valueOf( getStringPref( key, defaultValue ) );
    }

    private String getStringPref( int key, int defaultValue )
    {
        return mSettings.getString( getString( key ), getString( defaultValue ) );
    }

    private void initAudio()
    {
        mAudioManager = (AudioManager) getSystemService( Context.AUDIO_SERVICE );
    }

    private void initClientWrapper()
    {
        VoiceClientEventHandler handler = new VoiceClientEventHandler( this );

        mClient = VoiceClient.getInstance();
        mClient.setHandler( handler );

        findViewById( R.id.init_btn ).setOnClickListener( this );
        findViewById( R.id.release_btn ).setOnClickListener( this );
        findViewById( R.id.login_btn ).setOnClickListener( this );
        findViewById( R.id.logout_btn ).setOnClickListener( this );
        findViewById( R.id.place_call_btn ).setOnClickListener( this );
        findViewById( R.id.hang_up_btn ).setOnClickListener( this );
    }

    private void initClient()
    {
        String stunServer = getStringPref( R.string.stunserver_key, R.string.stunserver_value );
        String relayServer = getStringPref( R.string.relayserver_key, R.string.relayserver_value );
        String turnServer = getStringPref( R.string.turnserver_key, R.string.turnserver_value );
        mClient.init( stunServer, relayServer, relayServer, relayServer, turnServer );
    }

    private void login()
    {
        String xmppHost = getStringPref( R.string.xmpp_host_key, R.string.xmpp_host_value );
        int xmppPort = getIntPref( R.string.xmpp_port_key, R.string.xmpp_port_value );
        boolean xmppUseSSL = getBooleanPref( R.string.xmpp_use_ssl_key, R.string.xmpp_use_ssl_value );
        mClient.login( MY_USER, MY_PASS, xmppHost, xmppPort, xmppUseSSL);
    }

    private void resetAudio()
    {
        mAudioManager.setMode( AudioManager.MODE_NORMAL );
    }

    private synchronized void ringIncoming( Uri uri )
    {
        int ringerMode = mAudioManager.getRingerMode();
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if( callInProgress ) {
            // Notify with single vibrate.
            mVibrator.vibrate((long)200);
        } else {
            if( AudioManager.RINGER_MODE_NORMAL == ringerMode) {
                mAudioManager.setMode( AudioManager.MODE_RINGTONE );
                ring(uri, AudioManager.STREAM_RING);
            } else if( AudioManager.RINGER_MODE_VIBRATE == ringerMode) {

                // Start immediately
                //Vibrate 400, break 200, Vibrate 400, break 1000
                long[] pattern = { 0, 400, 200, 400, 1000 };

                // Vibrate until cancelled.
                mVibrator.vibrate(pattern, 0);
            }  // else RINGER_MODE_SILENT
        }
    }

    private synchronized void ringOutgoing( Uri uri )
    {
        mAudioManager.setMode( AudioManager.MODE_NORMAL );
        ring(uri, AudioManager.STREAM_VOICE_CALL);
    }

    private synchronized void playNotification()
    {
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        ringOutgoing(notification);
    }

    private synchronized void ring( Uri uri, int streamType)
    {
        try
        {
            if ( mRingerPlayer != null )
            {
                mRingerPlayer.stop();
            }
            mRingerPlayer = RingtoneManager.getRingtone( getApplicationContext(), uri );
            mRingerPlayer.setStreamType(streamType);
            mRingerPlayer.play();
        }
        catch ( Exception e )
        {
            Log.e( TAG, "error ringing", e );
        }
    }

    private void setAudioForCall()
    {
        mAudioManager.setMode( ( Build.VERSION.SDK_INT < 11 )
                                   ? AudioManager.MODE_IN_CALL
                                   : AudioManager.MODE_IN_COMMUNICATION );
        mAudioManager.requestAudioFocus( null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT );
    }

    private synchronized void startIncomingRinging()
    {
        Uri notification = RingtoneManager.getDefaultUri( RingtoneManager.TYPE_RINGTONE );
        ringIncoming( notification );
    }

    private synchronized void startOutgoingRinging()
    {
        Uri notification = Uri.parse( "android.resource://com.tuenti.voice.example/raw/outgoing_call_ring" );
        ringOutgoing( notification );
    }

    private synchronized void stopRinging()
    {
        if ( mRingerPlayer != null )
        {
            mAudioManager.setMode( AudioManager.MODE_NORMAL );
            mRingerPlayer.stop();
            mRingerPlayer = null;
        }

        if( mVibrator != null )
        {
            mVibrator.cancel();
            mVibrator = null;
        }
    }
}
