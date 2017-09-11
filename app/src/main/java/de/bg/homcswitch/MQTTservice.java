package de.bg.homcswitch;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.util.Log;
import org.eclipse.paho.client.mqttv3.*;

import java.util.Collections;
import java.util.Vector;

public class MQTTservice extends Service {
    private static boolean serviceRunning = false;
    private static int mid = 0;
    private static MQTTConnection connection = null;
    private final Messenger clientMessenger = new Messenger(new ClientHandler());

    @Override
    public void onCreate() {
        super.onCreate();
        connection = new MQTTConnection();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning()) {
            return START_STICKY;
        }

        super.onStartCommand(intent, flags, startId);
        /*
         * Start the MQTT Thread.
		 */
        connection.start();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        connection.end();
    }

    @Override
    public IBinder onBind(Intent intent) {
        /*
         * Return a reference to our client handler.
		 */
        return clientMessenger.getBinder();
    }

    private synchronized static boolean isRunning() {
         /*
          * Only run one instance of the service.
		  */
        if (serviceRunning == false) {
            serviceRunning = true;
            return false;
        } else {
            return true;
        }
    }

    /*
     * These are the supported messages from bound clients
     */
    public static final int REGISTER = 0;
    public static final int SUBSCRIBE = 1;
    public static final int PUBLISH = 2;

    /*
     * Fixed strings for the supported messages.
     */
    public static final String TOPIC = "topic";
    public static final String MESSAGE = "message";
    public static final String STATUS = "status";
    public static final String CLASSNAME = "classname";
    public static final String INTENTNAME = "intentname";
    public static final String STATUS_TEXT = "staus_text";

    /*
     * This class handles messages sent to the service by
     * bound clients.
     */
    class ClientHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            boolean status = false;
            String status_text = "";

            switch (msg.what) {
                case SUBSCRIBE:
                case PUBLISH:
           		 	/*
           		 	 * These two requests should be handled by
           		 	 * the connection thread, call makeRequest
           		 	 */
                    connection.makeRequest(msg);
                    break;
                case REGISTER: {
                    Bundle b = msg.getData();
                    if (b != null) {
                        Object target = b.getSerializable(CLASSNAME);
                        if (target != null) {
        				 /*
        				  * This request can be handled in-line
        				  * call the API
        				  */
                            connection.setPushCallback((Class<?>) target);
                            status = true;
                            status_text = "Push Callback Registered";
                        }
                        CharSequence cs = b.getCharSequence(INTENTNAME);
                        if (cs != null) {
                            String name = cs.toString().trim();
                            if (name.isEmpty() == false) {
            				 /*
            				  * This request can be handled in-line
            				  * call the API
            				  */
                                connection.setIntentName(name);
                                status = true;
                                status_text = "Intent Registered";

                            }
                        }
                    }
                    ReplytoClient(msg.replyTo, msg.what, status, status_text);
                    break;
                }
            }
        }
    }

    private void ReplytoClient(Messenger responseMessenger, int type, boolean status, String status_text) {
		 /*
		  * A response can be sent back to a requester when
		  * the replyTo field is set in a Message, passed to this
		  * method as the first parameter.
		  */
        if (responseMessenger != null) {
            Bundle data = new Bundle();
            data.putBoolean(STATUS, status);
            data.putString(STATUS_TEXT, status_text);
            Message reply = Message.obtain(null, type);
            reply.setData(data);

            try {
                responseMessenger.send(reply);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    enum CONNECT_STATE {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    private class MQTTConnection extends Thread {
        private Class<?> launchActivity = null;
        private String intentName = null;
        private MsgHandler msgHandler = null;
        private static final int STOP = PUBLISH + 1;
        private static final int CONNECT = PUBLISH + 2;
        private static final int RESETTIMER = PUBLISH + 3;
        private CONNECT_STATE connState = CONNECT_STATE.DISCONNECTED;

        MQTTConnection() {
            msgHandler = new MsgHandler();
            msgHandler.sendMessage(Message.obtain(null, CONNECT));
        }

        public void end() {
            msgHandler.sendMessage(Message.obtain(null, STOP));
        }

        public void makeRequest(Message msg) {
			/*
			 * It is expected that the caller only invokes
			 * this method with valid msg.what.
			 */
            msgHandler.sendMessage(Message.obtain(msg));
        }

        public void setPushCallback(Class<?> activityClass) {
            launchActivity = activityClass;
        }

        public void setIntentName(String name) {
            intentName = name;
        }

        private class MsgHandler extends Handler implements MqttCallback {
            private final String HOST = "odroidnas.lyk3nnyrasmj0efc.myfritz.net";
            private final int PORT = 1883;
            private final String uri = "tcp://" + HOST + ":" + PORT;
            private final int MINTIMEOUT = 2000;
            private final int MAXTIMEOUT = 32000;
            private int timeout = MINTIMEOUT;
            private MqttClient client = null;
            private MqttConnectOptions options = new MqttConnectOptions();
            private Vector<String> topics = new Vector<String>();


            MsgHandler() {
                options.setCleanSession(true);
                try {
                    client = new MqttClient(uri, MqttClient.generateClientId(), null);
                    client.setCallback(this);
                } catch (MqttException e1) {
                    e1.printStackTrace();
                }
            }

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case STOP: {
					/*
					 * Clean up, and terminate.
					 */
                        client.setCallback(null);
                        if (client.isConnected()) {
                            try {
                                client.disconnect();
                                client.close();
                            } catch (MqttException e) {
                                e.printStackTrace();
                            }
                        }
                        getLooper().quit();
                        break;
                    }
                    case CONNECT: {
                        if (connState != CONNECT_STATE.CONNECTED) {
                            try {
                                client.connect(options);
                                connState = CONNECT_STATE.CONNECTED;
                                Log.d(getClass().getCanonicalName(), "Connected");
                                timeout = MINTIMEOUT;
                            } catch (MqttException e) {
                                Log.d(getClass().getCanonicalName(), "Connection attemp failed with reason code = " + e.getReasonCode() + e.getCause());
                                if (timeout < MAXTIMEOUT) {
                                    timeout *= 2;
                                }
                                this.sendMessageDelayed(Message.obtain(null, CONNECT), timeout);
                                return;
                            }
					    
					    /*
					     * Re-subscribe to previously subscribed topics 
					     */
                            for (String topic : topics) {
                                subscribe(topic);
                            }
                        }
                        break;
                    }
                    case RESETTIMER: {
                        timeout = MINTIMEOUT;
                        break;
                    }
                    case SUBSCRIBE: {
                        boolean status = false;
                        Bundle b = msg.getData();
                        if (b != null) {
                            String[] received_topics = b.getStringArray(TOPIC);
                            for (String received_topic : received_topics) {
                                status = subscribe(received_topic);
                            }
	        					/*
                                 * Save this topic for re-subscription if needed.
	        					 */
                            if (status) {
                                Collections.addAll(topics, received_topics);
                            }
                        }
                        ReplytoClient(msg.replyTo, msg.what, status, "Subscribe");
                        break;
                    }
                    case PUBLISH: {
                        boolean status = false;
                        Bundle b = msg.getData();
                        if (b != null) {
                            CharSequence cs = b.getCharSequence(TOPIC);
                            if (cs != null) {
                                String topic = cs.toString().trim();
                                if (topic.isEmpty() == false) {
                                    cs = b.getCharSequence(MESSAGE);
                                    if (cs != null) {
                                        String message = cs.toString().trim();
                                        status = publish(topic, message);
                                    }
                                }
                            }
                        }
                        ReplytoClient(msg.replyTo, msg.what, status, "PUBLISH");
                        break;
                    }
                }
            }

            private boolean subscribe(String topic) {
                try {
                    client.subscribe(topic);
                } catch (MqttException e) {
                    Log.d(getClass().getCanonicalName(), "Subscribe failed with reason code = " + e.getReasonCode());
                    return false;
                }
                return true;
            }

            private boolean publish(String topic, String msg) {
                try {
                    MqttMessage message = new MqttMessage();
                    message.setPayload(msg.getBytes());
                    client.publish(topic, message);
                } catch (MqttException e) {
                    Log.d(getClass().getCanonicalName(), "Publish failed with reason code = " + e.getReasonCode());
                    return false;
                }
                return true;
            }

            @Override
            public void connectionLost(Throwable arg0) {
                Log.d(getClass().getCanonicalName(), "connectionLost");
                connState = CONNECT_STATE.DISCONNECTED;
                sendMessageDelayed(Message.obtain(null, CONNECT), timeout);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken arg0) {
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.d(getClass().getCanonicalName(), topic + ":" + message.toString());

                if (intentName != null) {
                    Intent intent = new Intent();
                    intent.setAction(intentName);
                    intent.putExtra(TOPIC, topic);
                    intent.putExtra(MESSAGE, message.toString());
                    sendBroadcast(intent);
                }


                Context context = getBaseContext();
                PendingIntent pendingIntent = null;

                if (launchActivity != null) {
                    Intent intent = new Intent(context, launchActivity);
                    intent.setAction(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_LAUNCHER);

                    //build the pending intent that will start the appropriate activity
                    pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
                }

                //build the notification
//                Builder notificationCompat = new Builder(context);
//                notificationCompat.setAutoCancel(true)
//                        .setContentIntent(pendingIntent)
//                        .setContentText(message.toString())
//                        .setSmallIcon(R.drawable.ic_launcher);
//
//                Notification notification = notificationCompat.build();
//                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//                nm.notify(mid++, notification);
            }
        }
    }
}
