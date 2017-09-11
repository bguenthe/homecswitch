package de.bg.homcswitch;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;

public class HomcSwitchMain extends Activity {
    static final String TAG = "de.bguenthe.mqtt.client";
    private JSONObject data = new JSONObject();

    private Messenger service = null;
    private final Messenger serviceHandler = new Messenger(new ServiceHandler());
    private IntentFilter intentFilter = null;
    private PushReceiver pushReceiver;

    public class PushReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent i) {
            String topic = i.getStringExtra(MQTTservice.TOPIC);
            String payload = i.getStringExtra(MQTTservice.MESSAGE);
            logMessage("Push message received - " + topic + ":" + payload);
//            if (topic.compareTo("/from/temperature") == 0) {
//                TextView temperature = (TextView) findViewById(R.id.temperature);
//                try {
//                    data = new JSONObject(payload);
//                    String stemperature = data.getString("temperature");
//                    temperature.setText(stemperature);
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//
//            } else if (topic.compareTo("/from/humidity") == 0) {
//                TextView humidity = (TextView) findViewById(R.id.humidity);
//                try {
//                    data = new JSONObject(payload);
//                    String shumidity = data.getString("humidity");
//                    humidity.setText(shumidity);
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//            }
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder binder) {
            service = new Messenger(binder);
            Bundle data = new Bundle();
            //data.putSerializable(MQTTservice.CLASSNAME, MainActivity.class);
            data.putCharSequence(MQTTservice.INTENTNAME, "com.example.MQTT.PushReceived");
            Message msg = Message.obtain(null, MQTTservice.REGISTER);
            msg.setData(data);
            msg.replyTo = serviceHandler;
            try {
                service.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            subscribe();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };

    class ServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MQTTservice.SUBSCRIBE:
                    break;
                case MQTTservice.PUBLISH:
                    break;
                case MQTTservice.REGISTER:
                    break;
                default:
                    super.handleMessage(msg);
                    return;
            }

            Bundle b = msg.getData();
            if (b != null) {
                Boolean status = b.getBoolean(MQTTservice.STATUS);
                String status_text = b.getString(MQTTservice.STATUS_TEXT);
                if (status == false) {
                    logMessage("Fail: " + status_text);
                } else {
                    logMessage("Success: " + status_text);
                }
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        logMessage("onCreate");

        intentFilter = new IntentFilter();
        intentFilter.addAction("com.example.MQTT.PushReceived");
        pushReceiver = new PushReceiver();
        registerReceiver(pushReceiver, intentFilter, null, null);

        startService(new Intent(this, MQTTservice.class));

//        final ImageView im_led = (ImageView) findViewById(R.id.imageView_led);
//        im_led.setOnClickListener(new View.OnClickListener() {
//            boolean crossed_out = false;
//            @Override
//            public void onClick(View view) {
//                if (crossed_out) {
//                    im_led.setImageDrawable(getResources().getDrawable(R.drawable.led, getTheme()));
//                    crossed_out= false;
//                    sendMessage("/arduino/LEDswitch", "/LED1/on");
//                } else {
//                    im_led.setImageDrawable(getResources().getDrawable(R.drawable.led_crossed_out, getTheme()));
//                    crossed_out = true;
//                    sendMessage("/arduino/LEDswitch", "/LED1/off");
//                }
//            }
//        });

        final Switch led_wemos01 = (Switch) findViewById(R.id.LED_wemos01);
        led_wemos01.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    sendMessage("wemos01/to_device/LED", "{\"value\":\"on\"}");
                } else {
                    sendMessage("wemos01/to_device/LED", "{\"value\":\"off\"}");
                }
            }
        });

        final Switch relais_wemos01 = (Switch) findViewById(R.id.relais_wemos01);
        relais_wemos01.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    sendMessage("wemos01/to_device/switch", "{\"value\":\"on\"}");
                } else {
                    sendMessage("wemos01/to_device/switch", "{\"value\":\"off\"}");
                }
            }
        });

        final Switch led_wemos02 = (Switch) findViewById(R.id.LED_wemos02);
        led_wemos02.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()

        {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    sendMessage("wemos02/to_device/LED", "{\"value\":\"on\"}");
                } else {
                    sendMessage("wemos02/to_device/LED", "{\"value\":\"off\"}");
                }
            }
        });

        final Switch relais_wemos02 = (Switch) findViewById(R.id.relais_wemos02);
        relais_wemos02.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    sendMessage("wemos02/to_device/switch", "{\"value\":\"on\"}");
                } else {
                    sendMessage("wemos02/to_device/switch", "{\"value\":\"off\"}");
                }
            }
        });
    }

    private void subscribe() {
        Bundle data = new Bundle();
        String[] topics = {"wemos01/from_device/status", "wemos02/from_device/status"};
        data.putStringArray(MQTTservice.TOPIC, topics);
        Message msg = Message.obtain(null, MQTTservice.SUBSCRIBE);
        msg.setData(data);
        msg.replyTo = serviceHandler;
        try {
            service.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
            logMessage("Subscribe failed with exception:" + e.getMessage());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        logMessage("onStart");
        bindService(new Intent(this, MQTTservice.class), serviceConnection, 0);
    }

    protected void onStop() {
        super.onStop();
        logMessage("onStop");
        unbindService(serviceConnection);
    }

    @Override
    protected void onResume() {
        super.onResume();
        logMessage("onResume");
        registerReceiver(pushReceiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        logMessage("onPause");
        unregisterReceiver(pushReceiver);
    }


    protected void onDestroy() {
        super.onDestroy();
        logMessage("onDestroy");
    }

    static public String prepareMessage(String message) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        String cDateTime = dateFormat.format(new Date());
        return cDateTime + ": " + message + "\n";
    }

    public void logMessage(String message) {
        Log.d(TAG, message);
    }

    public void sendMessage(String topic, String payload) {
        Bundle data = new Bundle();
        data.putCharSequence(MQTTservice.TOPIC, topic);
        data.putCharSequence(MQTTservice.MESSAGE, payload);
        Message msg = Message.obtain(null, MQTTservice.PUBLISH);
        msg.setData(data);
        msg.replyTo = serviceHandler;
        try {
            service.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
            logMessage("Publish failed with exception:" + e.getMessage());
        }
    }
}
