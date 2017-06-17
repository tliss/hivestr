package com.satori.android_demo;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.os.*;
import android.provider.ContactsContract;
import android.util.Log;

import com.satori.rtm.Ack;
import com.satori.rtm.RtmClient;
import com.satori.rtm.RtmClientAdapter;
import com.satori.rtm.RtmClientBuilder;
import com.satori.rtm.SubscriptionAdapter;
import com.satori.rtm.SubscriptionMode;
import com.satori.rtm.model.AnyJson;
import com.satori.rtm.model.SubscribeReply;
import com.satori.rtm.model.SubscribeRequest;
import com.satori.rtm.model.SubscriptionData;
import com.satori.rtm.model.SubscriptionError;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * Service for interaction with Satori RTM.
 * <p>
 * Service allows bidirectional interaction with application components via event handlers.
 * <p>
 * The activity binds to the service when the activity is created, and unbinds from the service
 * before the activity is destroyed. The service and the activity interact with each other by
 * sending events via internal Android messaging (not to confuse it with RTM messages).
 * <p>
 * The app uses two channels to interact with other chat users. The first channel is used as chat
 * room to send and receive messages. The second channel is used to track user presence.
 * The background Service automatically sends a presence message to the channel indicating that
 * the user is online (every {@value PRESENCE_INTERVAL_MS} milliseconds). If the service does not
 * receive a presence message for a specific user for {@value OFFLINE_USER_THRESHOLD_MS}
 * milliseconds, the app considers such user to be offline.
 */
public class SatoriService extends Service {
    private static final String TAG = "SatoriService";
    static final int EVENT_SEND_TEXT = 1;
    static final int EVENT_BIND_ACTIVITY = 2;
    static final int EVENT_UNBIND_ACTIVITY = 3;
    static final int EVENT_RECEIVE_CHAT_MESSAGE = 4;
    static final int EVENT_USER_JOIN = 5;
    static final int EVENT_USER_LEFT = 6;
    static final int EVENT_INFO = 7;
    static final int EVENT_CLIENT_STATE = 8;

    private static final int PRESENCE_INTERVAL_MS = 5000;
    private static final int OFFLINE_USER_THRESHOLD_MS = (PRESENCE_INTERVAL_MS * 3);

    private final Messenger mIncomingEventHandler = new Messenger(new IncomingHandler(this));
    private final List<Messenger> mConsumers = new ArrayList<Messenger>();
    private final Map<String, Long> mUserPresence = new HashMap<String, Long>();
    private final Timer mPresenceTimer = new Timer();

    private boolean isServiceStarted = false;
    private RtmClient mRtmClient;
    private String mUsername;

    @Override
    public IBinder onBind(Intent intent) {
        return mIncomingEventHandler.getBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service Started.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        // Initialize service if needed. Create RTM client and timer for presence
        if (!isServiceStarted) {
            isServiceStarted = true;
            mUsername = getUserName();
            new CreateRtmClientTask().execute();
            mPresenceTimer.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    onTimerTick();
                }
            }, PRESENCE_INTERVAL_MS, PRESENCE_INTERVAL_MS);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service Destroyed.");
        if (null != mRtmClient) {
            mRtmClient.stop();
            isServiceStarted = false;
        }
    }

    private RtmClient createRtmClient() {
        final String endpoint = getString(R.string.satori_endpoint);
        final String appkey = getString(R.string.satori_appkey);
        final String messageChannelName = getString(R.string.satori_message_channel_name);
        final String presenceChannelName = getString(R.string.satori_presence_channel_name);

        final RtmClient client = new RtmClientBuilder(endpoint, appkey)
                .setListener(new RtmClientAdapter() {
                    @Override
                    public void onEnterConnected(RtmClient client) {
                        sendEventToUI(buildEventInfo("RTM client is connected"));
                        sendEventToUI(buildEventClientState(true));
                    }

                    @Override
                    public void onConnectingError(RtmClient client, Exception ex) {
                        sendEventToUI(buildEventInfo("RTM client failed to connect: " + ex.getMessage()));
                    }

                    @Override
                    public void onTransportError(RtmClient client, Exception ex) {
                        sendEventToUI(buildEventInfo("RTM client failed: " + ex.getMessage()));
                    }

                    @Override
                    public void onError(RtmClient client, Exception ex) {
                        sendEventToUI(buildEventInfo("RTM client failed: " + ex.getMessage()));
                    }

                    @Override
                    public void onLeaveConnected(RtmClient client) {
                        sendEventToUI(buildEventInfo("RTM client is disconnected."));
                        sendEventToUI(buildEventClientState(false));
                    }
                })
                .build();

        client.start();

        client.createSubscription(messageChannelName, SubscriptionMode.SIMPLE, new SubscriptionAdapter() {
            @Override
            public void onEnterSubscribed(SubscribeRequest request, SubscribeReply reply) {
                sendEventToUI(buildEventInfo("RTM client is subscribed to " + reply.getSubscriptionId()));
            }

            @Override
            public void onLeaveSubscribed(SubscribeRequest request, SubscribeReply reply) {
                sendEventToUI(buildEventInfo("RTM client is unsubscribed from " + reply.getSubscriptionId()));
            }

            @Override
            public void onSubscriptionData(SubscriptionData subscriptionData) {
                for (AnyJson json : subscriptionData.getMessages()) {
                    try {
                        ChatMessage msg = json.convertToType(ChatMessage.class);
                        sendEventToUI(buildEventNewChatMessage(msg.user, msg.text));
                    } catch (Exception ex) {
                        Log.e(TAG, "Received malformed message: " + json, ex);
                    }
                }
            }

            @Override
            public void onSubscriptionError(SubscriptionError error) {
                String msg = String.format("RTM subscription failed: %s (%s)", error.getError(), error.getReason());
                sendEventToUI(buildEventInfo(msg));
            }
        });

        client.createSubscription(presenceChannelName, SubscriptionMode.SIMPLE, new SubscriptionAdapter() {
            public void onEnterSubscribed(SubscribeRequest request, SubscribeReply reply) {
                sendEventToUI(buildEventInfo("RTM client is subscribed to " + reply.getSubscriptionId()));
            }

            @Override
            public void onLeaveSubscribed(SubscribeRequest request, SubscribeReply reply) {
                sendEventToUI(buildEventInfo("RTM client is unsubscribed from " + reply.getSubscriptionId()));
            }

            @Override
            public void onSubscriptionData(SubscriptionData channelData) {
                for (ChatPresence presence : channelData.getMessagesAsType(ChatPresence.class)) {
                    if (!mUserPresence.containsKey(presence.user)) {
                        sendEventToUI(buildEventUserJoin(presence.user));
                    }
                    mUserPresence.put(presence.user, System.currentTimeMillis());
                }
            }

            @Override
            public void onSubscriptionError(SubscriptionError error) {
                String msg = String.format("RTM subscription failed: %s (%s)", error.getError(), error.getReason());
                sendEventToUI(buildEventInfo(msg));
            }
        });
        return client;
    }

    private void sendEventToUI(Message event) {
        for (int i = mConsumers.size() - 1; i >= 0; i--) {
            try {
                Messenger messenger = mConsumers.get(i);
                messenger.send(event);
            } catch (RemoteException e) {
                // The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
                mConsumers.remove(i);
            }
        }
    }

    private Message buildEventNewChatMessage(String nick, String messageText) {
        Bundle b = new Bundle();
        b.putString("nick", nick);
        b.putString("text", messageText);
        Message msg = Message.obtain(null, EVENT_RECEIVE_CHAT_MESSAGE);
        msg.setData(b);
        Log.i(TAG, "Send to UI [msg] " + messageText);
        return msg;
    }

    private Message buildEventClientState() {
        return buildEventClientState(null != mRtmClient && mRtmClient.isConnected());
    }

    private Message buildEventClientState(boolean isConnected) {
        Bundle b = new Bundle();
        b.putBoolean("is_connected", isConnected);
        b.putString("endpoint", getString(R.string.satori_endpoint));
        b.putString("appkey", getString(R.string.satori_appkey));
        Message msg = Message.obtain(null, EVENT_CLIENT_STATE);
        msg.setData(b);
        return msg;
    }

    private Message buildEventUserJoin(String nick) {
        Bundle b = new Bundle();
        b.putString("nick", nick);
        Message msg = Message.obtain(null, EVENT_USER_JOIN);
        msg.setData(b);
        Log.i(TAG, "Send to UI [join] " + nick);
        return msg;
    }

    private Message buildEventUserLeft(String nick) {
        Bundle b = new Bundle();
        b.putString("nick", nick);
        Message msg = Message.obtain(null, EVENT_USER_LEFT);
        msg.setData(b);
        Log.i(TAG, "Send to UI [left] " + nick);
        return msg;
    }

    private Message buildEventInfo(String info) {
        Bundle b = new Bundle();
        b.putString("info", info);
        Message msg = Message.obtain(null, EVENT_INFO);
        msg.setData(b);
        Log.i(TAG, "Send to UI [info] " + info);
        return msg;
    }

    private void onTimerTick() {
        String presenceChannelName = getString(R.string.satori_presence_channel_name);
        if (null != mRtmClient && mRtmClient.isConnected()) {
            mRtmClient.publish(presenceChannelName, new ChatPresence(mUsername), Ack.NO);
        }
        for (Iterator<Map.Entry<String, Long>> it = mUserPresence.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> entry = it.next();
            long last = entry.getValue();
            long current = System.currentTimeMillis();
            if (OFFLINE_USER_THRESHOLD_MS < current - last) {
                sendEventToUI(buildEventUserLeft(entry.getKey()));
                it.remove();
            }
        }
    }

    private String getUserName() {
        Cursor c = null;
        try {
            c = getApplication().getContentResolver().query(ContactsContract.Profile.CONTENT_URI, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                return c.getString(idx);
            } else {
                return "noname";
            }
        } finally {
            if (null != c) {
                c.close();
            }
        }
    }

    static class ChatMessage {
        String user;
        String text;

        ChatMessage() {
        }

        ChatMessage(String user, String text) {
            this.user = user;
            this.text = text;
        }
    }

    static class ChatPresence {
        String user;

        ChatPresence() {
        }

        ChatPresence(String user) {
            this.user = user;
        }
    }

    static class IncomingHandler extends Handler {
        private final WeakReference<SatoriService> mServiceRef;

        IncomingHandler(SatoriService service) {
            this.mServiceRef = new WeakReference<SatoriService>(service);
        }

        @Override
        public void handleMessage(Message event) {
            Log.i(TAG, "Receive message + " + event.what);
            SatoriService service = mServiceRef.get();
            if (null == service) {
                return;
            }
            switch (event.what) {
                case EVENT_BIND_ACTIVITY:
                    service.mConsumers.add(event.replyTo);
                    service.sendEventToUI(service.buildEventClientState());
                    break;
                case EVENT_UNBIND_ACTIVITY:
                    service.mConsumers.remove(event.replyTo);
                    break;
                case EVENT_SEND_TEXT:
                    String channelName = service.getString(R.string.satori_message_channel_name);
                    String msgText = (String) event.obj;
                    ChatMessage message = new ChatMessage(service.mUsername, msgText);
                    service.mRtmClient.publish(channelName, message, Ack.NO);
                    break;
                default:
                    super.handleMessage(event);
            }
        }
    }

    // Android policy prohibits you to create a network connection on main thread.
    // Use AsyncTask to call off network operation from main thread
    private class CreateRtmClientTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            mRtmClient = createRtmClient();
            return null;
        }
    }
}
