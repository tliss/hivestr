package com.satori.android_demo;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.icu.util.Calendar;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.RequiresApi;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.style.SubscriptSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import java.lang.ref.WeakReference;
import java.util.Random;


/**
 * Main activity of chat application.
 * <p>
 * When user enters chat text to send, the activity notifies the service and the service constructs
 * and publishes chat message (using the SDK). When the SDK receives a new message from RTM, the
 * service notifies all bound activities about it.
 */
public class MainActivity extends AppCompatActivity implements View.OnCreateContextMenuListener {
    private final String TAG = "MainActivity";
    private final Messenger mIncomingMessenger = new Messenger(new IncomingHandler(this));
    private boolean mInitialized = false;
    private Messenger mService = null;
    private boolean mIsBound;
    private TextView mTextView;
    private MenuItem mClientConnectivityState;
    private String newTag = "";
    private String userName;
    private EditText msgField;
    private TextView countTxt;
    Location mLocation;
    LocationManager mLocationManager;

    private final LocationListener mLocationListener = new LocationListener() {

        @Override
        public void onLocationChanged(final Location location) {
            mLocation = location;
            SubscriptionChangeMessage scm = new SubscriptionChangeMessage(newTag, mLocation);
            sendSubscriptionChangeMessageToService(scm);
            Log.i(TAG, "Location: "+location.toString());
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };
    // Object to monitor the state of an application service
    private ServiceConnection mConnection = new ServiceConnection() {
        @RequiresApi(api = Build.VERSION_CODES.N)
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

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Random rand = new Random();
        userName = "bee" + (rand.nextInt(999) + 1);



        //Intent intent = new Intent(this, MainActivity.class);
        //startActivity(intent);
        //finish();



        TextView beeButton = (TextView) findViewById(R.id.beebutton);
        countTxt = (TextView) findViewById(R.id.num_txt);
        beeButton.setText(new String(Character.toChars(0x1F41D)));
        //beeButton.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        mTextView = (TextView) findViewById(R.id.chatHistory);

        EditText inputField = (EditText) findViewById(R.id.message);
        msgField = inputField;
        inputField.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if ((keyEvent != null && (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                    CharSequence text = textView.getText();

                    ChatMessage message = new ChatMessage(userName, text.toString(), mLocation, newTag);
                    sendMessageToService(message);
                    textView.setText("");
                }
                return false;
            }
        });

        updateLocation();
        doBindService();
    }

    @TargetApi(Build.VERSION_CODES.N)
    private void updateLocation(){
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
    }

    @Override
    protected void onResume(){
        super.onResume();
        mLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, mLocationListener);
    }

    @Override
    protected void onPause(){
        super.onPause();
        mLocationManager.removeUpdates(mLocationListener);
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

        if(id == R.id.add_hive){

            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
            alertDialog.setTitle("Join a Hive");
            alertDialog.setMessage("Which hive would you like to join?");

            final EditText input = new EditText(MainActivity.this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT);
            input.setLayoutParams(lp);
            alertDialog.setView(input);

            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Cancel",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }
            );
            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Go",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            //Toast.makeText(getApplicationContext(), "Sup", Toast.LENGTH_SHORT).show();
                            newTag = input.getText().toString();

                            mTextView.setText("Entered hive #"+newTag);;
                            SubscriptionChangeMessage subChangeMessage = new SubscriptionChangeMessage(newTag, mLocation);
                            sendSubscriptionChangeMessageToService(subChangeMessage);

                            final Runnable r = new Runnable() {
                                public void run() {
                                    sendMessageToService(new ChatMessage("Queen Bee", "User "+userName+" has joined.", mLocation, newTag));
                                }
                            };
                            Handler handler = new Handler();
                            handler.postDelayed(r, 1000);

                            if (newTag.equals("")){
                                setTitle(("Hivestr").trim());
                            }
                            else {
                                setTitle(("Hivestr #" + (newTag).toLowerCase()).trim());
                            }
                        }
                    }
            );

            alertDialog.show();
            return true;
        }


        return super.onOptionsItemSelected(item);
    }

    private void sendMessageToService(ChatMessage message) {
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

    private void sendSubscriptionChangeMessageToService(SubscriptionChangeMessage subChangeMessage){
        if(mIsBound){
            if(mService != null){
                try{
                    Message msg = Message.obtain(null, SatoriService.EVENT_CHANGE_SUBSCRIPTION, 0, 0, subChangeMessage);
                    msg.replyTo = mIncomingMessenger;
                    mService.send(msg);
                }catch(RemoteException e){

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
                case SatoriService.EVENT_RECEIVE_USER_COUNT: {
                    int count = event.getData().getInt("count");
//                    String text = String.format("%s users in chat", count);
//                    final Animation animation = new AlphaAnimation(1, 0); // Change alpha from fully visible to invisible
//                    animation.setDuration(500); // duration - half a second
//                    animation.setInterpolator(new LinearInterpolator()); // do not alter animation rate
//                    animation.setRepeatCount(Animation.INFINITE); // Repeat animation infinitely
//                    animation.setRepeatMode(Animation.REVERSE); // Reverse animation at the end so the button will fade back in
//                    activity.countTxt.setAnimation(animation);
                    activity.countTxt.setText(count);
                    //activity.mTextView.setText(Html.fromHtml(text + "<br/>" + html), TextView.BufferType.EDITABLE);
                    break;
                }
                case SatoriService.EVENT_USER_JOIN: {
                    String nick = event.getData().getString("nick");
                    String text = String.format("<font color=#cc0000><i>User &lt;%s&gt; joined the channel</i></font>", nick);
                    //activity.mTextView.setText(Html.fromHtml(text + "<br/>" + html), TextView.BufferType.EDITABLE);
                    break;
                }
                case SatoriService.EVENT_USER_LEFT: {
                    String nick = event.getData().getString("nick");
                    String text = String.format("<font color=#cc0000><i>User &lt;%s&gt; left the channel</i></font>", nick);
                    //activity.mTextView.setText(Html.fromHtml(text + "<br/>" + html), TextView.BufferType.EDITABLE);
                    break;
                }
                case SatoriService.EVENT_INFO: {
                    String info = event.getData().getString("info");
                    String text = String.format("<font color=#a8a8a8 size=5><i>%s</i></font>", info);
                    //activity.mTextView.setText(Html.fromHtml(text + "<br/>" + html), TextView.BufferType.EDITABLE);
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
                        //activity.mTextView.setText(Html.fromHtml(buffer.toString() + "<br/>" + html), TextView.BufferType.EDITABLE);
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
