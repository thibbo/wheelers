package com.cooper.wheellog;

import android.Manifest;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import com.cooper.wheellog.utils.Constants;
import com.cooper.wheellog.utils.Constants.WHEEL_TYPE;
import com.cooper.wheellog.utils.SettingsUtil;
import com.cooper.wheellog.utils.Typefaces;
import com.cooper.wheellog.views.WheelView;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.database.FirebaseListAdapter;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.slel.ChatMessage;
import com.slel.KeyGenerator;
import com.slel.MessagingActivity;
import com.viewpagerindicator.LinePageIndicator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.RuntimePermissions;
import timber.log.Timber;

import static com.cooper.wheellog.utils.MathsUtil.kmToMiles;

@RuntimePermissions
public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener {

    protected static final int SIGN_IN_REQUEST_CODE = 10;
    protected static final int RESULT_DEVICE_SCAN_REQUEST = 20;
    protected static final int RESULT_REQUEST_ENABLE_BT = 30;
    protected static final int REQUEST_CODE_RESOLUTION = 40;
    Menu mMenu;
    ViewPager pager;
    MenuItem miSearch;
    MenuItem miWheel;
    MenuItem miWatch;
    MenuItem miLogging;
    TextView tvSpeed;
    TextView tvTemperature;
    TextView tvCurrent;
    TextView tvPower;
    TextView tvVoltage;
    TextView tvBattery;
    TextView tvFanStatus;
    TextView tvTopSpeed;
    TextView tvDistance;
    TextView tvModel;
    TextView tvName;
    TextView tvVersion;
    TextView tvSerial;
    TextView tvTotalDistance;
    TextView tvRideTime;
    TextView tvMode;
    LineChart chart1;
    WheelView wheelView;
    ListView messageListView;
    RelativeLayout disconnectedView;
    FloatingActionButton sendingButton;
    int viewPagerPage = 1;
    private BluetoothLeService mBluetoothLeService;
    private BluetoothAdapter mBluetoothAdapter;
    private String mDeviceAddress;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Timber.e(getResources().getString(R.string.error_bluetooth_not_initialised));
                Toast.makeText(MainActivity.this, R.string.error_bluetooth_not_initialised, Toast.LENGTH_SHORT).show();
                finish();
            }

            if (mBluetoothLeService.getConnectionState() == BluetoothLeService.STATE_DISCONNECTED &&
                    mDeviceAddress != null && !mDeviceAddress.isEmpty()) {
                mBluetoothLeService.setDeviceAddress(mDeviceAddress);
                toggleConnectToWheel();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
            finish();
        }
    };
    private int mConnectionState = BluetoothLeService.STATE_DISCONNECTED;
    private boolean doubleBackToExitPressedOnce = false;
    private Snackbar snackbar;
    private ArrayList<String> xAxis_labels = new ArrayList<>();
    IAxisValueFormatter chartAxisValueFormatter = new IAxisValueFormatter() {
        @Override
        public String getFormattedValue(float value, AxisBase axis) {
            if (value < xAxis_labels.size())
                return xAxis_labels.get((int) value);
            else
                return "";
        }

        // we don't draw numbers, so no decimal digits needed
        @Override
        public int getDecimalDigits() {
            return 0;
        }
    };
    private boolean use_mph = false;
    ViewPager.SimpleOnPageChangeListener pageChangeListener = new ViewPager.SimpleOnPageChangeListener() {
        @Override
        public void onPageSelected(int position) {
            super.onPageSelected(position);
            viewPagerPage = position;
            updateScreen(true);
        }
    };
    private GoogleApiClient mGoogleApiClient;
    private final BroadcastReceiver mBluetoothUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            switch (intent.getAction()) {
                case Constants.ACTION_BLUETOOTH_CONNECTION_STATE:
                    int connectionState = intent.getIntExtra(Constants.INTENT_EXTRA_CONNECTION_STATE, BluetoothLeService.STATE_DISCONNECTED);
                    Timber.i("Bluetooth state = %d", connectionState);
                    setConnectionState(connectionState);
                    break;
                case Constants.ACTION_WHEEL_DATA_AVAILABLE:
                    if (WheelData.getInstance().getWheelType() == WHEEL_TYPE.KINGSONG) {
                        if (WheelData.getInstance().getName().isEmpty())
                            sendBroadcast(new Intent(Constants.ACTION_REQUEST_KINGSONG_NAME_DATA));
                        else if (WheelData.getInstance().getSerial().isEmpty())
                            sendBroadcast(new Intent(Constants.ACTION_REQUEST_KINGSONG_SERIAL_DATA));
                    }
                    updateScreen(intent.hasExtra(Constants.INTENT_EXTRA_GRAPH_UPDATE_AVILABLE));
                    break;
                case Constants.ACTION_PEBBLE_SERVICE_TOGGLED:
                    setMenuIconStates();
                    break;
                case Constants.ACTION_LOGGING_SERVICE_TOGGLED:
                    boolean running = intent.getBooleanExtra(Constants.INTENT_EXTRA_IS_RUNNING, false);
                    if (intent.hasExtra(Constants.INTENT_EXTRA_LOGGING_FILE_LOCATION)) {
                        String filepath = intent.getStringExtra(Constants.INTENT_EXTRA_LOGGING_FILE_LOCATION);
                        if (running)
                            showSnackBar(getResources().getString(R.string.started_logging, filepath), 5000);
                    }

                    setMenuIconStates();
                    break;
                case Constants.ACTION_PREFERENCE_CHANGED:
                    loadPreferences();
                    if(intent.hasExtra("parameter")) {
                        String param = intent.getStringExtra("parameter");
                        WheelData.getInstance().writeConfig(param);
                    }
                    break;
            }
        }
    };
    private DrawerLayout mDrawer;
    private FirebaseListAdapter<ChatMessage> adapter;
    private DatabaseReference mDatabase;

    private void setConnectionState(int connectionState) {

        switch (connectionState) {
            case BluetoothLeService.STATE_CONNECTED:
                configureDisplay(WheelData.getInstance().getWheelType());
                if (mDeviceAddress != null && !mDeviceAddress.isEmpty())
                    SettingsUtil.setLastAddress(getApplicationContext(), mDeviceAddress);
                hideSnackBar();
                break;
            case BluetoothLeService.STATE_CONNECTING:
                if (mConnectionState == BluetoothLeService.STATE_CONNECTING)
                    showSnackBar(R.string.bluetooth_direct_connect_failed);
                else {
                    if (mBluetoothLeService.getDisconnectTime() != null) {
                        showSnackBar(
                                getString(R.string.connection_lost_at,
                                        new SimpleDateFormat("HH:mm:ss", Locale.US).format(mBluetoothLeService.getDisconnectTime())),
                                Snackbar.LENGTH_INDEFINITE);
                    }
                }
                break;
            case BluetoothLeService.STATE_DISCONNECTED:
                break;
        }
        mConnectionState = connectionState;
        setMenuIconStates();
    }

    private void setMenuIconStates() {
        if (mMenu == null)
            return;

        if (mDeviceAddress == null || mDeviceAddress.isEmpty()) {
            miWheel.setEnabled(false);
            miWheel.getIcon().setAlpha(64);
        } else {
            miWheel.setEnabled(true);
            miWheel.getIcon().setAlpha(255);
        }

        switch (mConnectionState) {
            case BluetoothLeService.STATE_CONNECTED:
                miWheel.setIcon(R.drawable.ic_action_wheel_orange);
                miWheel.setTitle(R.string.disconnect_from_wheel);
                miSearch.setEnabled(false);
                miSearch.getIcon().setAlpha(64);
                break;
            case BluetoothLeService.STATE_CONNECTING:
                miWheel.setIcon(R.drawable.anim_wheel_icon);
                miWheel.setTitle(R.string.disconnect_from_wheel);
                ((AnimationDrawable) miWheel.getIcon()).start();
                miSearch.setEnabled(false);
                miSearch.getIcon().setAlpha(64);
                break;
            case BluetoothLeService.STATE_DISCONNECTED:
                miWheel.setIcon(R.drawable.ic_action_wheel_white);
                miWheel.setTitle(R.string.connect_to_wheel);
                miSearch.setEnabled(true);
                miSearch.getIcon().setAlpha(255);
                break;
        }

        if (PebbleService.isInstanceCreated()) {
            miWatch.setIcon(R.drawable.ic_action_watch_orange);
        } else {
            miWatch.setIcon(R.drawable.ic_action_watch_white);
        }

        if (LoggingService.isInstanceCreated()) {
            miLogging.setTitle(R.string.stop_data_service);
            miLogging.setIcon(R.drawable.ic_action_logging_orange);
        } else {
            miLogging.setTitle(R.string.start_data_service);
            miLogging.setIcon(R.drawable.ic_action_logging_white);
        }
    }

    private void configureDisplay(WHEEL_TYPE wheelType) {
        TextView tvWaitText = (TextView) findViewById(R.id.tvWaitText);
        TextView tvTitleSpeed = (TextView) findViewById(R.id.tvTitleSpeed);
        TextView tvTitleMaxSpeed = (TextView) findViewById(R.id.tvTitleTopSpeed);
        TextView tvTitleBattery = (TextView) findViewById(R.id.tvTitleBattery);
        TextView tvTitleDistance = (TextView) findViewById(R.id.tvTitleDistance);
        TextView tvTitleRideTime = (TextView) findViewById(R.id.tvTitleRideTime);
        TextView tvTitleVoltage = (TextView) findViewById(R.id.tvTitleVoltage);
        TextView tvTitleCurrent = (TextView) findViewById(R.id.tvTitleCurrent);
        TextView tvTitlePower = (TextView) findViewById(R.id.tvTitlePower);
        TextView tvTitleTemperature = (TextView) findViewById(R.id.tvTitleTemperature);
        TextView tvTitleFanStatus = (TextView) findViewById(R.id.tvTitleFanStatus);
        TextView tvTitleMode = (TextView) findViewById(R.id.tvTitleMode);
        TextView tvTitleTotalDistance = (TextView) findViewById(R.id.tvTitleTotalDistance);
        TextView tvTitleName = (TextView) findViewById(R.id.tvTitleName);
        TextView tvTitleModel = (TextView) findViewById(R.id.tvTitleModel);
        TextView tvTitleVersion = (TextView) findViewById(R.id.tvTitleVersion);
        TextView tvTitleSerial = (TextView) findViewById(R.id.tvTitleSerial);

        switch (wheelType) {
            case KINGSONG:
                tvWaitText.setVisibility(View.GONE);
                tvTitleSpeed.setVisibility(View.VISIBLE);
                tvSpeed.setVisibility(View.VISIBLE);
                tvTitleMaxSpeed.setVisibility(View.VISIBLE);
                tvTopSpeed.setVisibility(View.VISIBLE);
                tvTitleBattery.setVisibility(View.VISIBLE);
                tvBattery.setVisibility(View.VISIBLE);
                tvTitleDistance.setVisibility(View.VISIBLE);
                tvDistance.setVisibility(View.VISIBLE);
                tvTitleRideTime.setVisibility(View.VISIBLE);
                tvRideTime.setVisibility(View.VISIBLE);
                tvTitleVoltage.setVisibility(View.VISIBLE);
                tvVoltage.setVisibility(View.VISIBLE);
                tvTitleCurrent.setVisibility(View.VISIBLE);
                tvCurrent.setVisibility(View.VISIBLE);
                tvTitlePower.setVisibility(View.VISIBLE);
                tvPower.setVisibility(View.VISIBLE);
                tvTitleTemperature.setVisibility(View.VISIBLE);
                tvTemperature.setVisibility(View.VISIBLE);
                tvTitleFanStatus.setVisibility(View.VISIBLE);
                tvFanStatus.setVisibility(View.VISIBLE);
                tvTitleMode.setVisibility(View.VISIBLE);
                tvMode.setVisibility(View.VISIBLE);
                tvTitleTotalDistance.setVisibility(View.VISIBLE);
                tvTotalDistance.setVisibility(View.VISIBLE);
                tvTitleName.setVisibility(View.VISIBLE);
                tvName.setVisibility(View.VISIBLE);
                tvTitleModel.setVisibility(View.VISIBLE);
                tvModel.setVisibility(View.VISIBLE);
                tvTitleVersion.setVisibility(View.VISIBLE);
                tvVersion.setVisibility(View.VISIBLE);
                tvTitleSerial.setVisibility(View.VISIBLE);
                tvSerial.setVisibility(View.VISIBLE);
                break;
            case GOTWAY:
                tvWaitText.setVisibility(View.GONE);
                tvTitleSpeed.setVisibility(View.VISIBLE);
                tvSpeed.setVisibility(View.VISIBLE);
                tvTitleMaxSpeed.setVisibility(View.VISIBLE);
                tvTopSpeed.setVisibility(View.VISIBLE);
                tvTitleBattery.setVisibility(View.VISIBLE);
                tvBattery.setVisibility(View.VISIBLE);
                tvTitleDistance.setVisibility(View.VISIBLE);
                tvDistance.setVisibility(View.VISIBLE);
                tvTitleRideTime.setVisibility(View.VISIBLE);
                tvRideTime.setVisibility(View.VISIBLE);
                tvTitleVoltage.setVisibility(View.VISIBLE);
                tvVoltage.setVisibility(View.VISIBLE);
                tvTitleCurrent.setVisibility(View.VISIBLE);
                tvCurrent.setVisibility(View.VISIBLE);
                tvTitlePower.setVisibility(View.VISIBLE);
                tvPower.setVisibility(View.VISIBLE);
                tvTitleTemperature.setVisibility(View.VISIBLE);
                tvTemperature.setVisibility(View.VISIBLE);
                tvTitleTotalDistance.setVisibility(View.VISIBLE);
                tvTotalDistance.setVisibility(View.VISIBLE);
                break;
            default:
                tvWaitText.setVisibility(View.VISIBLE);
                tvTitleSpeed.setVisibility(View.GONE);
                tvSpeed.setVisibility(View.GONE);
                tvTitleMaxSpeed.setVisibility(View.GONE);
                tvTopSpeed.setVisibility(View.GONE);
                tvTitleBattery.setVisibility(View.GONE);
                tvBattery.setVisibility(View.GONE);
                tvTitleDistance.setVisibility(View.GONE);
                tvDistance.setVisibility(View.GONE);
                tvTitleRideTime.setVisibility(View.GONE);
                tvRideTime.setVisibility(View.GONE);
                tvTitleVoltage.setVisibility(View.GONE);
                tvVoltage.setVisibility(View.GONE);
                tvTitleCurrent.setVisibility(View.GONE);
                tvCurrent.setVisibility(View.GONE);
                tvTitlePower.setVisibility(View.GONE);
                tvPower.setVisibility(View.GONE);
                tvTitleTemperature.setVisibility(View.GONE);
                tvTemperature.setVisibility(View.GONE);
                tvTitleFanStatus.setVisibility(View.GONE);
                tvFanStatus.setVisibility(View.GONE);
                tvTitleMode.setVisibility(View.GONE);
                tvMode.setVisibility(View.GONE);
                tvTitleTotalDistance.setVisibility(View.GONE);
                tvTotalDistance.setVisibility(View.GONE);
                tvTitleName.setVisibility(View.GONE);
                tvName.setVisibility(View.GONE);
                tvTitleModel.setVisibility(View.GONE);
                tvModel.setVisibility(View.GONE);
                tvTitleVersion.setVisibility(View.GONE);
                tvVersion.setVisibility(View.GONE);
                tvTitleSerial.setVisibility(View.GONE);
                tvSerial.setVisibility(View.GONE);
                break;
        }
    }

    private void updateScreen(boolean updateGraph) {
        switch (viewPagerPage) {
            case 0:
                if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                    // Start sign in/sign up activity
                    startActivityForResult(AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setProviders(Arrays.asList(new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                                            new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build()
                                    ))
                                    .setLogo(R.mipmap.logo)
                                    .setTheme(R.style.AppTheme)
                                    .build(),
                            SIGN_IN_REQUEST_CODE);
                }
                else {
                    // Load chat room contents
                    displayChatMessages();
                }
                FloatingActionButton fab =
                        (FloatingActionButton)findViewById(R.id.fab);

                fab.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        EditText input = (EditText)findViewById(R.id.input);
                        if(input.getText().toString().equals(""))
                            return;

                        // Read the input field and push a new instance
                        // of ChatMessage to the Firebase database
                        mDatabase = FirebaseDatabase.getInstance().getReference();
                        mDatabase.child("Global").push()
                                .setValue(new ChatMessage(input.getText().toString(),
                                        FirebaseAuth.getInstance()
                                                .getCurrentUser()
                                                .getDisplayName())
                                );

                        // Clear the input
                        input.setText("");
                    }
                });
                break;
            case 1: // GUI View
                wheelView.setSpeed(WheelData.getInstance().getSpeed());
                wheelView.setBattery(WheelData.getInstance().getBatteryLevel());
                wheelView.setTemperature(WheelData.getInstance().getTemperature());
                wheelView.setRideTime(WheelData.getInstance().getRideTimeString());
                wheelView.setTopSpeed(WheelData.getInstance().getTopSpeedDouble());
                wheelView.setDistance(WheelData.getInstance().getDistanceDouble());
                wheelView.setTotalDistance(WheelData.getInstance().getTotalDistanceDouble());
                wheelView.setVoltage(WheelData.getInstance().getVoltageDouble());
                wheelView.setCurrent(WheelData.getInstance().getPowerDouble());
                break;
            case 2: // Text View
                if (use_mph) {
                    tvSpeed.setText(String.format(Locale.US, "%.1f mph", kmToMiles(WheelData.getInstance().getSpeedDouble())));
                    tvTopSpeed.setText(String.format(Locale.US, "%.1f mph", kmToMiles(WheelData.getInstance().getTopSpeedDouble())));
                    tvDistance.setText(String.format(Locale.US, "%.2f mi", kmToMiles(WheelData.getInstance().getDistanceDouble())));
                    tvTotalDistance.setText(String.format(Locale.US, "%.2f mi", kmToMiles(WheelData.getInstance().getTotalDistanceDouble())));
                } else {
                    tvSpeed.setText(String.format(Locale.US, "%.1f km/h", WheelData.getInstance().getSpeedDouble()));
                    tvTopSpeed.setText(String.format(Locale.US, "%.1f km/h", WheelData.getInstance().getTopSpeedDouble()));
                    tvDistance.setText(String.format(Locale.US, "%.2f km", WheelData.getInstance().getDistanceDouble()));
                    tvTotalDistance.setText(String.format(Locale.US, "%.2f km", WheelData.getInstance().getTotalDistanceDouble()));
                }

                tvVoltage.setText(String.format(Locale.US, "%.2fV", WheelData.getInstance().getVoltageDouble()));
                tvTemperature.setText(String.format(Locale.US, "%d°C", WheelData.getInstance().getTemperature()));
                tvCurrent.setText(String.format(Locale.US, "%.2fA", WheelData.getInstance().getCurrentDouble()));
                tvPower.setText(String.format(Locale.US, "%.2fW", WheelData.getInstance().getPowerDouble()));
                tvBattery.setText(String.format(Locale.US, "%d%%", WheelData.getInstance().getBatteryLevel()));
                tvFanStatus.setText(WheelData.getInstance().getFanStatus() == 0 ? "Off" : "On");
                tvVersion.setText(String.format(Locale.US, "%.2f", WheelData.getInstance().getVersion() / 100.0));
                tvName.setText(WheelData.getInstance().getName());
                tvModel.setText(WheelData.getInstance().getModel());
                tvSerial.setText(WheelData.getInstance().getSerial());
                tvRideTime.setText(WheelData.getInstance().getRideTimeString());
                tvMode.setText(getResources().getStringArray(R.array.modes)[WheelData.getInstance().getMode()]);
                break;
            case 3: // Graph  View
                if (updateGraph) {
                    xAxis_labels = WheelData.getInstance().getXAxis();

                    if (xAxis_labels.size() > 0) {

                        LineDataSet dataSetSpeed;
                        LineDataSet dataSetCurrent;

                        if (chart1.getData() == null) {
                            dataSetSpeed = new LineDataSet(null, "speed");
                            dataSetCurrent = new LineDataSet(null, "current");
                            dataSetSpeed.setLineWidth(2);
                            dataSetCurrent.setLineWidth(2);
                            dataSetSpeed.setAxisDependency(YAxis.AxisDependency.LEFT);
                            dataSetCurrent.setAxisDependency(YAxis.AxisDependency.RIGHT);
                            dataSetSpeed.setMode(LineDataSet.Mode.CUBIC_BEZIER);
                            dataSetCurrent.setMode(LineDataSet.Mode.CUBIC_BEZIER);
                            dataSetSpeed.setColor(getResources().getColor(android.R.color.white));
                            dataSetCurrent.setColor(getResources().getColor(R.color.accent));
                            dataSetSpeed.setDrawCircles(false);
                            dataSetCurrent.setDrawCircles(false);
                            dataSetSpeed.setDrawValues(false);
                            dataSetCurrent.setDrawValues(false);
                            LineData chart1_lineData = new LineData();
                            chart1_lineData.addDataSet(dataSetCurrent);
                            chart1_lineData.addDataSet(dataSetSpeed);
                            chart1.setData(chart1_lineData);
                            findViewById(R.id.leftAxisLabel).setVisibility(View.VISIBLE);
                            findViewById(R.id.rightAxisLabel).setVisibility(View.VISIBLE);
                        } else {
                            dataSetSpeed = (LineDataSet) chart1.getData().getDataSetByLabel("speed", true);
                            dataSetCurrent = (LineDataSet) chart1.getData().getDataSetByLabel("current", true);
                        }

                        dataSetSpeed.clear();
                        dataSetCurrent.clear();

                        ArrayList<Float> currentAxis = new ArrayList<>(WheelData.getInstance().getCurrentAxis());
                        ArrayList<Float> speedAxis = new ArrayList<>(WheelData.getInstance().getSpeedAxis());

                        for (Float d : currentAxis) {
                            float value = 0;
                            if (d != null)
                                value = d;

                            dataSetCurrent.addEntry(new Entry(dataSetCurrent.getEntryCount(), value));
                        }

                        for (Float d : speedAxis) {
                            float value = 0;

                            if (d != null)
                                value = d;

                            if (use_mph)
                                dataSetSpeed.addEntry(new Entry(dataSetSpeed.getEntryCount(), kmToMiles(value)));
                            else
                                dataSetSpeed.addEntry(new Entry(dataSetSpeed.getEntryCount(), value));
                        }

                        dataSetCurrent.notifyDataSetChanged();
                        dataSetSpeed.notifyDataSetChanged();
                        chart1.getData().notifyDataChanged();
                        chart1.notifyDataSetChanged();
                        chart1.invalidate();
                        break;
                    }
                }
        }
    }

    private void displayChatMessages() {
        adapter = new FirebaseListAdapter<ChatMessage>(this, ChatMessage.class,
                R.layout.message, FirebaseDatabase.getInstance().getReference().child("Global")) {
            @Override
            protected void populateView(View v, ChatMessage model, int position) {
                // Get references to the views of message.xml
                TextView messageText = (TextView)v.findViewById(R.id.message_text);
                TextView messageUser = (TextView)v.findViewById(R.id.message_user);
                TextView messageTime = (TextView)v.findViewById(R.id.message_time);

                // Set their text
                messageText.setText(model.getMessageText());
                messageUser.setText(model.getMessageUser());

                // Format the date before showing it
                messageTime.setText(DateFormat.format("dd-MM-yyyy (HH:mm:ss)",
                        model.getMessageTime()));
            }
        };

        messageListView.setAdapter(adapter);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateScreen(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WheelData.initiate();

        getFragmentManager().beginTransaction()
                .replace(R.id.settings_frame, getPreferencesFragment(), Constants.PREFERENCES_FRAGMENT_TAG)
                .commit();

        ViewPageAdapter adapter = new ViewPageAdapter(this);
        pager = (ViewPager) findViewById(R.id.pager);
        pager.setAdapter(adapter);
        pager.setOffscreenPageLimit(4);

        LinePageIndicator titleIndicator = (LinePageIndicator) findViewById(R.id.indicator);
        titleIndicator.setViewPager(pager);
        pager.setCurrentItem(1);
        pager.addOnPageChangeListener(pageChangeListener);

        mDeviceAddress = SettingsUtil.getLastAddress(getApplicationContext());
        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tvSpeed = (TextView) findViewById(R.id.tvSpeed);
        tvCurrent = (TextView) findViewById(R.id.tvCurrent);
        tvPower = (TextView) findViewById(R.id.tvPower);
        tvTemperature = (TextView) findViewById(R.id.tvTemperature);
        tvVoltage = (TextView) findViewById(R.id.tvVoltage);
        tvBattery = (TextView) findViewById(R.id.tvBattery);
        tvFanStatus = (TextView) findViewById(R.id.tvFanStatus);
        tvTopSpeed = (TextView) findViewById(R.id.tvTopSpeed);
        tvDistance = (TextView) findViewById(R.id.tvDistance);
        tvTotalDistance = (TextView) findViewById(R.id.tvTotalDistance);
        tvModel = (TextView) findViewById(R.id.tvModel);
        tvName = (TextView) findViewById(R.id.tvName);
        tvVersion = (TextView) findViewById(R.id.tvVersion);
        tvSerial = (TextView) findViewById(R.id.tvSerial);
        tvRideTime = (TextView) findViewById(R.id.tvRideTime);
        tvMode = (TextView) findViewById(R.id.tvMode);
        wheelView = (WheelView) findViewById(R.id.wheelView);
        mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        messageListView = (ListView) findViewById(R.id.list_of_messages);
        disconnectedView = (RelativeLayout) findViewById(R.id.disconnected_view);
        sendingButton = (FloatingActionButton) findViewById(R.id.fab);

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(".info/connected");
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                boolean connected = snapshot.getValue(Boolean.class);
                if (connected) {
                    disconnectedView.setVisibility(View.GONE);
                    sendingButton.setClickable(true);
                } else {
                    disconnectedView.setVisibility(View.VISIBLE);
                    sendingButton.setClickable(false);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                System.err.println("Listener was cancelled");
            }
        });

        mDrawer.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
            }

            @Override
            public void onDrawerOpened(View drawerView) {
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                ((PreferencesFragment) getPreferencesFragment()).show_main_menu();
            }

            @Override
            public void onDrawerStateChanged(int newState) {
            }
        });

        Typeface typefacePrime = Typefaces.get(this, "fonts/prime.otf");
        TextClock textClock = (TextClock) findViewById(R.id.textClock);
        TextView tvWaitText = (TextView) findViewById(R.id.tvWaitText);
        textClock.setTypeface(typefacePrime);
        tvWaitText.setTypeface(typefacePrime);

        chart1 = (LineChart) findViewById(R.id.chart);
        chart1.setDrawGridBackground(false);
        chart1.getDescription().setEnabled(false);
        chart1.setHardwareAccelerationEnabled(true);
        chart1.setHighlightPerTapEnabled(false);
        chart1.setHighlightPerDragEnabled(false);
        chart1.getLegend().setTextColor(getResources().getColor(android.R.color.white));
        chart1.setNoDataTextColor(getResources().getColor(android.R.color.white));

        YAxis leftAxis = chart1.getAxisLeft();
        YAxis rightAxis = chart1.getAxisRight();
        leftAxis.setAxisMinValue(0f);
        rightAxis.setAxisMinValue(0f);
        leftAxis.setDrawGridLines(false);
        rightAxis.setDrawGridLines(false);
        leftAxis.setTextColor(getResources().getColor(android.R.color.white));
        rightAxis.setTextColor(getResources().getColor(android.R.color.white));

        XAxis xAxis = chart1.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(getResources().getColor(android.R.color.white));
        xAxis.setValueFormatter(chartAxisValueFormatter);

        loadPreferences();

        if (SettingsUtil.isFirstRun(this)) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mDrawer.openDrawer(GravityCompat.START, true);
                }
            }, 1000);
        }

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        } else if (!mBluetoothAdapter.isEnabled()) {
            // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
            // fire an intent to display a dialog asking the user to grant permission to enable it.
            if (!mBluetoothAdapter.isEnabled())
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), RESULT_REQUEST_ENABLE_BT);
        } else {
            startBluetoothService();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (SettingsUtil.isAutoUploadEnabled(this))
            getGoogleApiClient().connect();

        if (mBluetoothLeService != null &&
                mConnectionState != mBluetoothLeService.getConnectionState())
            setConnectionState(mBluetoothLeService.getConnectionState());

        if (WheelData.getInstance().getWheelType() != WHEEL_TYPE.Unknown)
            configureDisplay(WheelData.getInstance().getWheelType());

        registerReceiver(mBluetoothUpdateReceiver, makeIntentFilter());
        updateScreen(true);
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        setMenuIconStates();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mBluetoothUpdateReceiver);
        if (SettingsUtil.isAutoUploadEnabled(this))
            getGoogleApiClient().disconnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopPebbleService();
        stopLoggingService();

        if (mBluetoothLeService != null) {
            unbindService(mServiceConnection);
            stopService(new Intent(getApplicationContext(), BluetoothLeService.class));
            mBluetoothLeService = null;
        }
        WheelData.getInstance().reset();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        mMenu = menu;
        miSearch = mMenu.findItem(R.id.miSearch);
        miWheel = mMenu.findItem(R.id.miWheel);
        miWatch = mMenu.findItem(R.id.miWatch);
        miLogging = mMenu.findItem(R.id.miLogging);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.miSearch:
                MainActivityPermissionsDispatcher.startScanActivityWithCheck(this);
                return true;
            case R.id.miWheel:
                toggleConnectToWheel();
                return true;
            case R.id.miLogging:
                MainActivityPermissionsDispatcher.toggleLoggingServiceWithCheck(this);
                return true;
            case R.id.miWatch:
                togglePebbleService();
                return true;
            case R.id.menu_sign_out:
                signOut();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void signOut() {
        AuthUI.getInstance().signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Toast.makeText(MainActivity.this,
                                "You have been signed out.",
                                Toast.LENGTH_LONG)
                                .show();
                    }
                });
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        View settings_layout = findViewById(R.id.settings_layout);
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                if (mDrawer.isDrawerOpen(settings_layout)) {
                    mDrawer.closeDrawers();
                } else {
                    mDrawer.openDrawer(GravityCompat.START, true);
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (mDrawer.isDrawerOpen(settings_layout)) {
                    if (!((PreferencesFragment) getPreferencesFragment()).show_main_menu())
                        mDrawer.closeDrawer(GravityCompat.START, true);
                } else {
                    if (doubleBackToExitPressedOnce) {
                        finish();
                        return true;
                    }

                    doubleBackToExitPressedOnce = true;
                    showSnackBar(R.string.back_to_exit);

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            doubleBackToExitPressedOnce = false;
                        }
                    }, 2000);
                }
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void loadPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        use_mph = sharedPreferences.getBoolean(getString(R.string.use_mph), false);
        int max_speed = sharedPreferences.getInt(getString(R.string.max_speed), 30);
        wheelView.setMaxSpeed(max_speed);
        wheelView.setUseMPH(use_mph);
        wheelView.invalidate();

        // attempt to write alarms
        int alarm1 = SettingsUtil.getAlarm1(this);
        int alarm2 = SettingsUtil.getAlarm2(this);
        int alarm3 = SettingsUtil.getAlarm3(this);
        int tilt_back = SettingsUtil.getTiltBack(this);

        boolean color_light_enabled = SettingsUtil.getColorLightEnabled(this);
        Constants.COLOR_LIGHT_MODE color_light_mode = SettingsUtil.getColorLightMode(this);
        boolean light_enabled = SettingsUtil.getLightEnabled(this);
        Constants.LIGHT_MODE light_mode = SettingsUtil.getLightMode(this);
        Constants.CYCLING_MODE cycling_mode = SettingsUtil.getCyclingMode(this);
        boolean voice_enabled = SettingsUtil.getVoiceEnabled(this);


        boolean alarms_enabled = sharedPreferences.getBoolean(getString(R.string.alarms_enabled), false);

        WheelData.getInstance().setAlarmsEnabled(alarms_enabled);

        if (alarms_enabled) {
            int alarm1Speed = sharedPreferences.getInt(getString(R.string.alarm_1_speed), 0);
            int alarm2Speed = sharedPreferences.getInt(getString(R.string.alarm_2_speed), 0);
            int alarm3Speed = sharedPreferences.getInt(getString(R.string.alarm_3_speed), 0);
            int alarm1Battery = sharedPreferences.getInt(getString(R.string.alarm_1_battery), 0);
            int alarm2Battery = sharedPreferences.getInt(getString(R.string.alarm_2_battery), 0);
            int alarm3Battery = sharedPreferences.getInt(getString(R.string.alarm_3_battery), 0);
            int current_alarm = sharedPreferences.getInt(getString(R.string.alarm_current), 0);
            boolean disablePhoneVibrate = sharedPreferences.getBoolean(getString(R.string.disable_phone_vibrate), false);

            WheelData.getInstance().setPreferences(
                alarm1Speed, alarm1Battery,
                alarm2Speed, alarm2Battery,
                alarm3Speed, alarm3Battery,
                current_alarm, disablePhoneVibrate,
                light_enabled, color_light_enabled,
                light_mode, color_light_mode,
                tilt_back, alarm1, alarm2, alarm3, cycling_mode, voice_enabled);
            wheelView.setWarningSpeed(alarm1Speed);
        } else {
            WheelData.getInstance().setPreferences(
                0, 0,
                0, 0,
                0, 0,
                0, false,
                light_enabled, color_light_enabled,
                light_mode, color_light_mode,
                tilt_back, alarm1, alarm2, alarm3, cycling_mode, voice_enabled);
            wheelView.setWarningSpeed(0);
        }

        boolean auto_log = sharedPreferences.getBoolean(getString(R.string.auto_log), false);
        boolean log_location = sharedPreferences.getBoolean(getString(R.string.log_location_data), false);
        boolean auto_upload = sharedPreferences.getBoolean(getString(R.string.auto_upload), false);

        if (auto_log)
            MainActivityPermissionsDispatcher.acquireStoragePermissionWithCheck(this);

        if (log_location)
            MainActivityPermissionsDispatcher.acquireLocationPermissionWithCheck(this);

        if (auto_upload)
            getGoogleApiClient().connect();

        boolean unlockSpeedLimit = sharedPreferences.getBoolean(getString(R.string.unlocker_enabled), false);

        if(unlockSpeedLimit)
            unlockSpeedLimit();

        updateScreen(true);
    }

    private void unlockSpeedLimit() {
        if(mDeviceAddress == null) {
            Toast.makeText(this, R.string.device_connection_required, Toast.LENGTH_LONG).show();
            return;
        }

        if(!SettingsUtil.isUnlockerEnabled(getApplicationContext())) {
            //unlock
            KeyGenerator keyGen = new KeyGenerator(mDeviceAddress);
            String result = keyGen.FindKey();
        }

    }

    @NeedsPermission({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void acquireStoragePermission() {
    }

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    void acquireLocationPermission() {
    }

    @OnPermissionDenied({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void storagePermissionDenied() {
        SettingsUtil.setAutoLog(this, false);
        ((PreferencesFragment) getPreferencesFragment()).refreshVolatileSettings();
    }

    @OnPermissionDenied(Manifest.permission.ACCESS_FINE_LOCATION)
    void locationPermissionDenied() {
        SettingsUtil.setLogLocationEnabled(this, false);
        ((PreferencesFragment) getPreferencesFragment()).refreshVolatileSettings();
    }

    private void showSnackBar(int msg) {
        showSnackBar(getString(msg));
    }

    private void showSnackBar(String msg) {
        showSnackBar(msg, 2000);
    }

    private void showSnackBar(String msg, int timeout) {
        if (snackbar == null) {
            View mainView = findViewById(R.id.main_view);
            snackbar = Snackbar
                    .make(mainView, "", Snackbar.LENGTH_LONG);
            snackbar.getView().setBackgroundResource(R.color.primary_dark);
            snackbar.setAction(android.R.string.ok, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                }
            });
        }
        snackbar.setDuration(timeout);
        snackbar.setText(msg);
        snackbar.show();
    }

    private void hideSnackBar() {
        if (snackbar == null)
            return;

        snackbar.dismiss();
    }

    private void stopLoggingService() {
        if (LoggingService.isInstanceCreated())
            toggleLoggingService();
    }

    @NeedsPermission({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void toggleLoggingService() {
        Intent dataLoggerServiceIntent = new Intent(getApplicationContext(), LoggingService.class);

        if (LoggingService.isInstanceCreated())
            stopService(dataLoggerServiceIntent);
        else
            startService(dataLoggerServiceIntent);
    }

    private void stopPebbleService() {
        if (PebbleService.isInstanceCreated())
            togglePebbleService();
    }

    private void togglePebbleService() {
        Intent pebbleServiceIntent = new Intent(getApplicationContext(), PebbleService.class);
        if (PebbleService.isInstanceCreated())
            stopService(pebbleServiceIntent);
        else
            startService(pebbleServiceIntent);
    }

    private void startBluetoothService() {
        Intent bluetoothServiceIntent = new Intent(getApplicationContext(), BluetoothLeService.class);
        startService(bluetoothServiceIntent);
        bindService(bluetoothServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    private void toggleConnectToWheel() {
        sendBroadcast(new Intent(Constants.ACTION_REQUEST_CONNECTION_TOGGLE));
    }

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    void startScanActivity() {
        startActivityForResult(new Intent(MainActivity.this, ScanActivity.class), RESULT_DEVICE_SCAN_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // NOTE: delegate the permission handling to generated method
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Timber.i("onActivityResult");
        switch (requestCode) {
            case RESULT_DEVICE_SCAN_REQUEST:
                if (resultCode == RESULT_OK) {
                    mDeviceAddress = data.getStringExtra("MAC");
                    Timber.i("Device selected = %s", mDeviceAddress);
                    mBluetoothLeService.setDeviceAddress(mDeviceAddress);
                    WheelData.getInstance().full_reset();
                    updateScreen(true);
                    setMenuIconStates();
                    mBluetoothLeService.close();
                    toggleConnectToWheel();
                }
                break;
            case RESULT_REQUEST_ENABLE_BT:
                if (mBluetoothAdapter.isEnabled())
                    startBluetoothService();
//                else {
//                    Toast.makeText(this, R.string.bluetooth_required, Toast.LENGTH_LONG).show();
//                    finish();
//                }
                break;
            case REQUEST_CODE_RESOLUTION:
                if (resultCode == RESULT_OK)
                    getGoogleApiClient().connect();
                else {
                    SettingsUtil.setAutoUploadEnabled(this, false);
                    ((PreferencesFragment) getPreferencesFragment()).refreshVolatileSettings();
                }
                break;
            case SIGN_IN_REQUEST_CODE:
                if(resultCode == RESULT_OK) {
                    Toast.makeText(this, "Successfully signed in. Welcome!", Toast.LENGTH_LONG).show();
                    displayChatMessages();
                }
                else {
                    pager.setCurrentItem(1);
                }
                break;
        }
    }

    private IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.ACTION_BLUETOOTH_CONNECTION_STATE);
        intentFilter.addAction(Constants.ACTION_WHEEL_DATA_AVAILABLE);
        intentFilter.addAction(Constants.ACTION_LOGGING_SERVICE_TOGGLED);
        intentFilter.addAction(Constants.ACTION_PEBBLE_SERVICE_TOGGLED);
        intentFilter.addAction(Constants.ACTION_PREFERENCE_CHANGED);
        return intentFilter;
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Timber.i("GoogleApiClient connection failed: %s", connectionResult.toString());
        if (!connectionResult.hasResolution()) {
            // show the localized error dialog.
            GoogleApiAvailability.getInstance().getErrorDialog(this, connectionResult.getErrorCode(), 0).show();
            SettingsUtil.setAutoUploadEnabled(this, false);
            ((PreferencesFragment) getPreferencesFragment()).refreshVolatileSettings();
            return;
        }
        try {
            connectionResult.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            Timber.e("Exception while starting resolution activity");
        }
    }

    public GoogleApiClient getGoogleApiClient() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        return mGoogleApiClient;
    }

    private Fragment getPreferencesFragment() {
        Fragment frag = getFragmentManager().findFragmentByTag(Constants.PREFERENCES_FRAGMENT_TAG);

        if (frag == null)
            frag = new PreferencesFragment();

        return frag;
    }
}