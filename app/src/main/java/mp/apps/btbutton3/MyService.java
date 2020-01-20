package mp.apps.btbutton3;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.icu.util.Calendar;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MyService extends Service {
    public MyService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        String channelId = "my_service";
        String channelName = "My Background Service";
        NotificationChannel chan = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
        chan.setLightColor(Color.BLUE);
        chan.setImportance(NotificationManager.IMPORTANCE_NONE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager service = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(chan);

        //Intent notificationIntent = new Intent(this, MainActivity.class);
        //PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification notification = new Notification.Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("My Awesome App")
                .setContentText("Doing some work...")
                //.setContentIntent(pendingIntent)
                .build();

        startForeground(1337, notification);
    }




    private TextToSpeech tts;
    private boolean ttsInitialized;

    private void prepareTTS() {
        ttsInitialized = false;
        Log.d(">>>>>>>>>>>", "prepare TTS START");
        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                    int m = Calendar.getInstance().get(Calendar.MINUTE);
                    String time = h + ":" + m;
                    Toast.makeText(MyService.this, time, Toast.LENGTH_LONG).show();
                    tts.setLanguage(Locale.US);
                    Log.d(">>>>>>>>>>>", "TTS loaded. " + time);

                    ttsInitialized = true;
                    goTTS();
                }
            }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String s) {
            }

            @Override
            public void onDone(String s) {
                Log.d(">>>>>>>>>>>>>>>>>", "TTS SPEAK DONE. CALL shutdown()");
                shutdown();
            }

            @Override
            public void onError(String s) {
                Log.d(">>>>>>>>>>>>>>>>>>>>>>>>", "ERROR");
            }
        });
        Log.d(">>>>>>>>>>>", "prepare TTS DONE");
    }

    private void goTTS() {
        if (!ttsInitialized) {
            Log.d(">>>>>", "goTTS(): wait for tts to initialize!");
            return;
        }
        if (!voiceRecDone) {
            Log.d(">>>>>", "goTTS(): wait for voiceRec to end!");
            return;
        }
                int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                int m = Calendar.getInstance().get(Calendar.MINUTE);
                String time = h + ":" + m;
                Log.d(">>>>>>>>>>", "SAY NOW.");
                tts.speak(time, TextToSpeech.QUEUE_FLUSH, null, "MESSAGEID");
    }
    private void shutdown() {
        Log.d(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>", ">>>> SHUTDOWN CALLED <<<<");
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
        //if (mHeadsetBroadcastReceiver != null)
        //    unregisterReceiver(mHeadsetBroadcastReceiver);

        stopSelf();
    }



    private String TAG = ">>>>>>";

    private boolean voiceRecDone;

    protected BluetoothAdapter mBluetoothAdapter;
    protected BluetoothHeadset mBluetoothHeadset;
    protected BluetoothDevice mConnectedHeadset;
    protected AudioManager mAudioManager;

    private AudioFocusRequest mAudioFocusRequest;

    public int onStartCommand(Intent intent, int flags, int startId) {

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // pause music
        AudioAttributes mPlaybackAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        mAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(mPlaybackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                    @Override
                    public void onAudioFocusChange(int focusChange) {
                    }
                })
                .build();
        mAudioManager.requestAudioFocus(mAudioFocusRequest);



        prepareTTS();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null) {
            if (mAudioManager.isBluetoothScoAvailableOffCall()) {
                mBluetoothAdapter.getProfileProxy(this, mHeadsetProfileListener, BluetoothProfile.HEADSET);
            }
        }

        return Service.START_NOT_STICKY;
    }


    protected BluetoothProfile.ServiceListener mHeadsetProfileListener = new BluetoothProfile.ServiceListener() {

        @Override
        public void onServiceDisconnected(int profile) {
            Log.d(TAG, "onServieDisconnected");
            mBluetoothHeadset.stopVoiceRecognition(mConnectedHeadset);
            unregisterReceiver(mHeadsetBroadcastReceiver);
            mBluetoothHeadset = null;
            shutdown();
        }

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            Log.d(TAG, "onServieConnected; begin");
            // mBluetoothHeadset is just a head set profile,
            // it does not represent a head set device.
            mBluetoothHeadset = (BluetoothHeadset) proxy;

            // If a head set is connected before this application starts,
            // ACTION_CONNECTION_STATE_CHANGED will not be broadcast.
            // So we need to check for already connected head set.
            List<BluetoothDevice> devices = mBluetoothHeadset.getConnectedDevices();
            if (devices.size() > 0) {
                // Only one head set can be connected at a time,
                // so the connected head set is at index 0.
                mConnectedHeadset = devices.get(0);

                String log;

                // The audio should not yet be connected at this stage.
                // But just to make sure we check.
                if (mBluetoothHeadset.isAudioConnected(mConnectedHeadset)) {
                    Log.d(TAG, "Profile listener audio already connected"); //$NON-NLS-1$
                } else {
                    // The if statement is just for debug. So far startVoiceRecognition always
                    // returns true here. What can we do if it returns false? Perhaps the only
                    // sensible thing is to inform the user.
                    // Well actually, it only returns true if a call to stopVoiceRecognition is
                    // call somewhere after a call to startVoiceRecognition. Otherwise, if
                    // stopVoiceRecognition is never called, then when the application is restarted
                    // startVoiceRecognition always returns false whenever it is called.
                    if (mBluetoothHeadset.startVoiceRecognition(mConnectedHeadset)) {
                        Log.d(TAG, "Profile listener startVoiceRecognition returns true"); //$NON-NLS-1$
                        mBluetoothHeadset.stopVoiceRecognition(mConnectedHeadset);
                    } else {
                        Log.d(TAG,"Profile listener startVoiceRecognition returns false"); //$NON-NLS-1$
                    }
                }
            }

            voiceRecDone = false;

            // During the active life time of the app, a user may turn on and off the head set.
            // So register for broadcast of connection states.
            registerReceiver(mHeadsetBroadcastReceiver, new IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED));

            // Calling startVoiceRecognition does not result in immediate audio connection.
            // So register for broadcast of audio connection states. This broadcast will
            // only be sent if startVoiceRecognition returns true.
            IntentFilter f = new IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
            f.setPriority(Integer.MAX_VALUE);
            registerReceiver(mHeadsetBroadcastReceiver, f);
            Log.d(TAG, "onServieConnected; registered; end.");
        }
    };


    protected BroadcastReceiver mHeadsetBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive() START");
            String action = intent.getAction();
            int state;
            int previousState = intent.getIntExtra(BluetoothHeadset.EXTRA_PREVIOUS_STATE, BluetoothHeadset.STATE_DISCONNECTED);

            if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
                state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED);
                if (state == BluetoothHeadset.STATE_CONNECTED) {
                    mConnectedHeadset = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    // Audio should not be connected yet but just to make sure.
                    if (mBluetoothHeadset.isAudioConnected(mConnectedHeadset)) {
                        Log.d(TAG, "Headset connected audio already connected");
                    } else {

                        // Calling startVoiceRecognition always returns false here,
                        // that why a count down timer is implemented to call
                        // startVoiceRecognition in the onTick and onFinish.
                        if (mBluetoothHeadset.startVoiceRecognition(mConnectedHeadset)) {
                            Log.d(TAG, "Headset connected startVoiceRecognition returns true"); //$NON-NLS-1$
                        } else {
                            Log.d(TAG, "Headset connected startVoiceRecognition returns false");
                        }
                    }
                } else if (state == BluetoothHeadset.STATE_DISCONNECTED) {
                    // Calling stopVoiceRecognition always returns false here
                    // as it should since the headset is no longer connected.
                    mConnectedHeadset = null;
                }
            } else // audio
            {
                state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_AUDIO_DISCONNECTED);

                mBluetoothHeadset.stopVoiceRecognition(mConnectedHeadset);

                if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                    Log.d(TAG, "Head set audio connected, cancel countdown timer");
                } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                    // The headset audio is disconnected, but calling
                    // stopVoiceRecognition always returns true here.
                    boolean returnValue = mBluetoothHeadset.stopVoiceRecognition(mConnectedHeadset);
                    Log.d(TAG, "Audio disconnected stopVoiceRecognition return " + returnValue);
                }

                if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED && previousState == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                    Log.d(">>>>>>>>>>>>>>", "audio connected?");
                    unregisterReceiver(mHeadsetBroadcastReceiver);
                    voiceRecDone = true;
                    goTTS();
                }
            }

            Log.d(TAG, previousState + " --> " + state + " | Action = " + action);

        }
    };
}
