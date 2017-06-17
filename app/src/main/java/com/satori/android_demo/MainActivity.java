package com.satori.android_demo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.*;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import java.lang.ref.WeakReference;


/**
 * Main activity of chat application.
 * <p>
 * When user enters chat text to send, the activity notifies the service and the service constructs
 * and publishes chat message (using the SDK). When the SDK receives a new message from RTM, the
 * service notifies all bound activities about it.
 */
public class MainActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";
    private final Messenger mIncomingMessenger = new Messenger(new IncomingHandler(this));
    private boolean mInitialized = false;
    private Messenger mService = null;
    private boolean mIsBound;
    private TextView mTextView;
    private MenuItem mClientConnectivityState;

    // Object to monitor the state of an application service
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "Service connected.");
            mService = new Messenger(service);
            try {
                // Sends initial event to the service and pass event handler to allow service to
                // notify us about new events
                Message msg = Message.obtain(null, SatoriService.EVENT_BIND_ACTIVITY);
                msg.replyTo = mIncomingMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even do anything with it
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.i(TAG, "Service disconnected.");
            mService = null;
        }
    };

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        String html = "";
        if (null != mTextView.getEditableText()) {
            html = Html.toHtml(mTextView.getEditableText());
        }
        outState.putString("history", html);
        outState.putBoolean("initialized", mInitialized);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        String history = savedInstanceState.getString("history");
        mTextView.setText(Html.fromHtml(history), TextView.BufferType.EDITABLE);
        mInitialized = savedInstanceState.getBoolean("initialized");
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = (TextView) findViewById(R.id.chatHistory);
        EditText inputField = (EditText) findViewById(R.id.message);
        inputField.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if ((keyEvent != null && (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                    CharSequence text = textView.getText();
                    sendMessageToService(text.toString());
                    textView.setText("");
                }
                return false;
            }
        });

        doBindService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void sendMessageToService(String message) {
        if (mIsBound) {
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null, SatoriService.EVENT_SEND_TEXT, 0, 0, message);
                    msg.replyTo = mIncomingMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                }
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mClientConnectivityState = menu.findItem(R.id.action_settings);
        return super.onPrepareOptionsMenu(menu);
    }

    void doBindService() {
        Intent intent = new Intent(this, SatoriService.class);
        startService(intent);
        bindService(new Intent(this, SatoriService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        Log.i(TAG, "Bind Service.");
    }

    void doUnbindService() {
        if (mIsBound) {
            // If we have received the service, and hence registered with it, then now is the time to unregister.
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null, SatoriService.EVENT_UNBIND_ACTIVITY);
                    msg.replyTo = mIncomingMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            }
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    // Handler of all incoming events from Service
    static class IncomingHandler extends Handler {
        private final WeakReference<MainActivity> mActivityRef;


        IncomingHandler(MainActivity activity) {
            mActivityRef = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message event) {
            MainActivity activity = mActivityRef.get();
            if (null == activity) {
                return;
            }

            String html = "";
            if (null != activity.mTextView.getEditableText()) {
                html = Html.toHtml(activity.mTextView.getEditableText());
            }

            switch (event.what) {
                case SatoriService.EVENT_RECEIVE_CHAT_MESSAGE: {
                    String nick = event.getData().getString("nick");
                    String message = event.getData().getString("text");
                    String text = String.format("<b>&lt;%s&gt;</b> %s", nick, message);
                    activity.mTextView.setText(Html.fromHtml(text + "<br/>" + html), TextView.BufferType.EDITABLE);
                    break;
                }
                case SatoriService.EVENT_USER_JOIN: {
                    String nick = event.getData().getString("nick");
                    String text = String.format("<font color=#cc0000><i>User &lt;%s&gt; joined the channel</i></font>", nick);
                    activity.mTextView.setText(Html.fromHtml(text + "<br/>" + html), TextView.BufferType.EDITABLE);
                    break;
                }
                case SatoriService.EVENT_USER_LEFT: {
                    String nick = event.getData().getString("nick");
                    String text = String.format("<font color=#cc0000><i>User &lt;%s&gt; left the channel</i></font>", nick);
                    activity.mTextView.setText(Html.fromHtml(text + "<br/>" + html), TextView.BufferType.EDITABLE);
                    break;
                }
                case SatoriService.EVENT_INFO: {
                    String info = event.getData().getString("info");
                    String text = String.format("<font color=#a8a8a8 size=5><i>%s</i></font>", info);
                    activity.mTextView.setText(Html.fromHtml(text + "<br/>" + html), TextView.BufferType.EDITABLE);
                    break;
                }
                case SatoriService.EVENT_CLIENT_STATE: {
                    Boolean isConnected = event.getData().getBoolean("is_connected");
                    int iconId = isConnected ? R.drawable.connected_white : R.drawable.disconnected_white;
                    activity.mClientConnectivityState.setIcon(activity.getResources().getDrawable(iconId));

                    if (!activity.mInitialized) {
                        String endpoint = event.getData().getString("endpoint");
                        String appkey = event.getData().getString("appkey");
                        StringBuilder buffer = new StringBuilder();
                        buffer.append("<font color=#0000cc size=5>RTM client configuration<br/>");
                        buffer.append(String.format("- endpoint: %s<br/>", endpoint));
                        buffer.append(String.format("- appkey: %s<br/>", appkey));
                        buffer.append("</font>");
                        activity.mTextView.setText(Html.fromHtml(buffer.toString() + "<br/>" + html), TextView.BufferType.EDITABLE);
                        activity.mInitialized = true;
                    }
                    break;
                }
                default:
                    super.handleMessage(event);
            }
        }
    }
}
