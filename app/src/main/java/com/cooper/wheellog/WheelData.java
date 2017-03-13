package com.cooper.wheellog;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.os.Vibrator;

import com.cooper.wheellog.utils.Constants;
import com.cooper.wheellog.utils.Constants.ALARM_TYPE;
import com.cooper.wheellog.utils.Constants.WHEEL_TYPE;
import com.cooper.wheellog.utils.Constants.CYCLING_MODE;
import com.cooper.wheellog.utils.Constants.LIGHT_MODE;
import com.cooper.wheellog.utils.Constants.COLOR_LIGHT_MODE;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class WheelData {
    private static final int TIME_BUFFER = 10;
    private static WheelData mInstance;

    private BluetoothLeService mBluetoothLeService;

    private long graph_last_update_time;
    private static final int GRAPH_UPDATE_INTERVAL = 500; // milliseconds
    private static final int MAX_BATTERY_AVERAGE_COUNT = 150;
    private ArrayList<String> xAxis = new ArrayList<>();
    private ArrayList<Float> currentAxis = new ArrayList<>();
    private ArrayList<Float> speedAxis = new ArrayList<>();

    private int mSpeed;
    private long mTotalDistance;
    private int mCurrent;
    private int mTemperature;
    private int mMode;
    private int mBattery;
    private double mAverageBattery;
    private double mAverageBatteryCount;
    private int mVoltage;
    //    private long mDistance;
    private int mRideTime;
    private int mLastRideTime;
    private int mTopSpeed;
    private int mFanStatus;
    private boolean mConnectionState = false;
    private String mName = "";
    private String mModel = "";
    private int mVersion;
    private String mSerialNumber = "";
    private WHEEL_TYPE mWheelType = WHEEL_TYPE.Unknown;
    private long rideStartTime;
    private long mStartTotalDistance;

    private boolean mAlarmsEnabled = false;
    private boolean mDisablePhoneVibrate = false;
    private int mAlarm1Speed = 0;
    private int mAlarm2Speed = 0;
    private int mAlarm3Speed = 0;
    private int mAlarm1Battery = 0;
    private int mAlarm2Battery = 0;
    private int mAlarm3Battery = 0;
    private int mAlarmCurrent = 0;

    private int mAlarm1 = 0;
    private int mAlarm2 = 0;
    private int mAlarm3 = 0;
    private int mTiltback = 0;
    private boolean mColorLightEnabled = true;
    private boolean mLightEnabled = true;
    private CYCLING_MODE mCyclingMode = CYCLING_MODE.PLAYER;
    private COLOR_LIGHT_MODE mColorLightMode = COLOR_LIGHT_MODE.MODE_1;
    private LIGHT_MODE mLightMode = LIGHT_MODE.AUTO;

    private boolean mSpeedAlarmExecuted = false;
    private boolean mCurrentAlarmExecuted = false;

    static void initiate() {
        if (mInstance == null)
            mInstance = new WheelData();

        mInstance.full_reset();
    }

    public static WheelData getInstance() {
        return mInstance;
    }

    int getSpeed() {
        return mSpeed / 10;
    }

    public int getTemperature() {
        return mTemperature / 100;
    }

    public int getBatteryLevel() {
        return mBattery;
    }

    int getFanStatus() {
        return mFanStatus;
    }

    boolean isConnected() {
        return mConnectionState;
    }

    //    int getTopSpeed() { return mTopSpeed; }
    int getVersion() {
        return mVersion;
    }

    //    int getCurrentTime() { return mCurrentTime+mLastCurrentTime; }
    int getMode() {
        return mMode;
    }

    WHEEL_TYPE getWheelType() {
        return mWheelType;
    }

    String getName() {
        return mName;
    }

    String getModel() {
        return mModel;
    }

    String getSerial() {
        return mSerialNumber;
    }

    int getRideTime() { return mRideTime; }

    String getRideTimeString() {
        int currentTime = mRideTime + mLastRideTime;
        long hours = TimeUnit.SECONDS.toHours(currentTime);
        long minutes = TimeUnit.SECONDS.toMinutes(currentTime) -
                TimeUnit.HOURS.toMinutes(TimeUnit.SECONDS.toHours(currentTime));
        long seconds = TimeUnit.SECONDS.toSeconds(currentTime) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(currentTime));
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    double getSpeedDouble() {
        return mSpeed / 100.0;
    }

    double getVoltageDouble() {
        return mVoltage / 100.0;
    }

    double getPowerDouble() {
        return (mCurrent * mVoltage) / 10000.0;
    }

    double getCurrentDouble() {
        return mCurrent / 100.0;
    }

    int getTopSpeed() { return mTopSpeed; }

    double getTopSpeedDouble() {
        return mTopSpeed / 100.0;
    }

    int getDistance() { return (int) (mTotalDistance - mStartTotalDistance); }

    public double getDistanceDouble() {
        return (mTotalDistance - mStartTotalDistance) / 1000.0;
    }

    double getTotalDistanceDouble() {
        return mTotalDistance / 1000.0;
    }

    ArrayList<String> getXAxis() {
        return xAxis;
    }

    ArrayList<Float> getCurrentAxis() {
        return currentAxis;
    }

    ArrayList<Float> getSpeedAxis() {
        return speedAxis;
    }

    void setConnected(boolean connected) {
        mConnectionState = connected;
    }

    void setAlarmsEnabled(boolean enabled) {
        mAlarmsEnabled = enabled;
    }

    void setPreferences(int alarm1Speed, int alarm1Battery,
                        int alarm2Speed, int alarm2Battery,
                        int alarm3Speed, int alarm3Battery,
                        int alarmCurrent, boolean disablePhoneVibrate,
                        boolean lightEnabled, boolean colorLightEnabled,
                        LIGHT_MODE lightMode, COLOR_LIGHT_MODE colorLightMode,
                        int tiltback, int alarm1, int alarm2, int alarm3) {
        mAlarm1Speed = alarm1Speed * 100;
        mAlarm2Speed = alarm2Speed * 100;
        mAlarm3Speed = alarm3Speed * 100;
        mAlarm1Battery = alarm1Battery;
        mAlarm2Battery = alarm2Battery;
        mAlarm3Battery = alarm3Battery;
        mAlarmCurrent = alarmCurrent*100;
        mDisablePhoneVibrate = disablePhoneVibrate;
        mLightEnabled = lightEnabled;
        mColorLightEnabled = colorLightEnabled;
        mLightMode = lightMode;
        mColorLightMode = colorLightMode;
        mTiltback = tiltback;
        mAlarm1 = alarm1;
        mAlarm2 = alarm2;
        mAlarm3 = alarm3;
    }

    private int byteArrayInt2(byte low, byte high) {
        return (low & 255) + ((high & 255) * 256);
    }

    private long byteArrayInt4(byte value1, byte value2, byte value3, byte value4) {
        return (((((long) ((value1 & 255) << 16))) | ((long) ((value2 & 255) << 24))) | ((long) (value3 & 255))) | ((long) ((value4 & 255) << 8));
    }

    private void setDistance(long distance) {
        if (mStartTotalDistance == 0 && mTotalDistance != 0)
            mStartTotalDistance = mTotalDistance - distance;

//        mDistance = distance;
    }

    private void setCurrentTime(int currentTime) {
        if (mRideTime > (currentTime + TIME_BUFFER))
            mLastRideTime += mRideTime;
        mRideTime = currentTime;
    }

    private void setTopSpeed(int topSpeed) {
        if (topSpeed > mTopSpeed)
            mTopSpeed = topSpeed;
    }

    private void setBatteryPercent(int battery) {
        mBattery = battery;

        mAverageBatteryCount = mAverageBatteryCount < MAX_BATTERY_AVERAGE_COUNT ?
                mAverageBatteryCount + 1 : MAX_BATTERY_AVERAGE_COUNT;

        mAverageBattery += (battery - mAverageBattery) / mAverageBatteryCount;
    }

    private void checkAlarmStatus(Context mContext) {
        // SPEED ALARM
        if (!mSpeedAlarmExecuted) {
            if (mAlarm1Speed > 0 && mAlarm1Battery > 0 &&
                    mAverageBattery <= mAlarm1Battery && mSpeed >= mAlarm1Speed)
                raiseAlarm(ALARM_TYPE.SPEED, mContext);
            else if (mAlarm2Speed > 0 && mAlarm2Battery > 0 &&
                    mAverageBattery <= mAlarm2Battery && mSpeed >= mAlarm2Speed)
                raiseAlarm(ALARM_TYPE.SPEED, mContext);
            else if (mAlarm3Speed > 0 && mAlarm3Battery > 0 &&
                    mAverageBattery <= mAlarm3Battery && mSpeed >= mAlarm3Speed)
                raiseAlarm(ALARM_TYPE.SPEED, mContext);
        } else {
            boolean alarm_finished = false;
            if (mAlarm1Speed > 0 && mAlarm1Battery > 0 &&
                    mAverageBattery > mAlarm1Battery && mSpeed < mAlarm1Speed)
                alarm_finished = true;
            else if (mAlarm2Speed > 0 && mAlarm2Battery > 0 &&
                    mAverageBattery <= mAlarm2Battery && mSpeed >= mAlarm2Speed)
                alarm_finished = true;
            else if (mAlarm3Speed > 0 && mAlarm3Battery > 0 &&
                    mAverageBattery <= mAlarm3Battery && mSpeed >= mAlarm3Speed)
                alarm_finished = true;

            mSpeedAlarmExecuted = alarm_finished;
        }

        // CURRENT
        if (!mCurrentAlarmExecuted) {
            if (mAlarmCurrent > 0 &&
                    mCurrent >= mAlarmCurrent) {
                raiseAlarm(ALARM_TYPE.CURRENT, mContext);
            }
        } else {
            if (mCurrent < mAlarmCurrent)
                mCurrentAlarmExecuted = false;
        }
    }

    private void raiseAlarm(ALARM_TYPE alarmType, Context mContext) {
        Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        long[] pattern = {0};
        Intent intent = new Intent(Constants.ACTION_ALARM_TRIGGERED);
        intent.putExtra(Constants.INTENT_EXTRA_ALARM_TYPE, alarmType);

        switch (alarmType) {
            case SPEED:
                pattern = new long[]{0, 300, 150, 300, 150, 500};
                mSpeedAlarmExecuted = true;
                break;
            case CURRENT:
                pattern = new long[]{0, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100};
                mCurrentAlarmExecuted = true;
                break;
        }
        mContext.sendBroadcast(intent);
        if (v.hasVibrator() && !mDisablePhoneVibrate)
            v.vibrate(pattern, -1);
    }

    void decodeResponse(byte[] data, Context mContext) {

//        StringBuilder stringBuilder = new StringBuilder(data.length);
//        for (byte aData : data)
//            stringBuilder.append(String.format(Locale.US, "%02d ", aData));
//        Timber.i("OUTPUT", stringBuilder.toString());
//        FileUtil.writeLine("bluetoothOutput.txt", stringBuilder.toString());

        boolean new_data = false;
        if (mWheelType == WHEEL_TYPE.KINGSONG)
            new_data = decodeKingSong(data);
        else if (mWheelType == WHEEL_TYPE.GOTWAY)
            new_data = decodeGotway(data);
        else if (mWheelType == WHEEL_TYPE.NINEBOT)
            new_data = decodeNinebot(data);

        if (!new_data)
            return;

        Intent intent = new Intent(Constants.ACTION_WHEEL_DATA_AVAILABLE);

        if (graph_last_update_time + GRAPH_UPDATE_INTERVAL < Calendar.getInstance().getTimeInMillis()) {
            graph_last_update_time = Calendar.getInstance().getTimeInMillis();
            intent.putExtra(Constants.INTENT_EXTRA_GRAPH_UPDATE_AVILABLE, true);
            currentAxis.add((float) getCurrentDouble());
            speedAxis.add((float) getSpeedDouble());
            xAxis.add(new SimpleDateFormat("HH:mm:ss", Locale.US).format(Calendar.getInstance().getTime()));
            if (speedAxis.size() > (3600000 / GRAPH_UPDATE_INTERVAL)) {
                speedAxis.remove(0);
                currentAxis.remove(0);
                xAxis.remove(0);
            }
        }

        if (mAlarmsEnabled)
            checkAlarmStatus(mContext);

        mContext.sendBroadcast(intent);
    }

    private boolean decodeKingSong(byte[] data) {

        if (data.length >= 20) {
            int a1 = data[0] & 255;
            int a2 = data[1] & 255;
            if (a1 != 170 || a2 != 85) {
                return false;
            }
            if ((data[16] & 255) == 169) { // Live data
                mVoltage = byteArrayInt2(data[2], data[3]);
                mSpeed = byteArrayInt2(data[4], data[5]);
                mTotalDistance = byteArrayInt4(data[6], data[7], data[8], data[9]);
                mCurrent = byteArrayInt2(data[10], data[11]);
                if (mCurrent > 7000) {
                    mCurrent = 7000;
                } else if (mCurrent < 0) {
                    mCurrent = 0;
                }
                mTemperature = byteArrayInt2(data[12], data[13]);

                if ((data[15] & 255) == 224) {
                    mMode = data[14];
                }

                int battery;
                if (mVoltage < 5000) {
                    battery = 0;
                } else if (mVoltage >= 6600) {
                    battery = 100;
                } else {
                    battery = (mVoltage - 5000) / 16;
                }
                setBatteryPercent(battery);

                return true;
            } else if ((data[16] & 255) == 185) { // Distance/Time/Fan Data
                long distance = byteArrayInt4(data[2], data[3], data[4], data[5]);
                setDistance(distance);
                int currentTime = byteArrayInt2(data[6], data[7]);
                setCurrentTime(currentTime);
                setTopSpeed(byteArrayInt2(data[8], data[9]));
                mFanStatus = data[12];
            } else if ((data[16] & 255) == 187) { // Name and Type data
                int end = 0;
                int i = 0;
                while (i < 14 && data[i + 2] != 0) {
                    end++;
                    i++;
                }
                mName = new String(data, 2, end).trim();
                mModel = "";
                String[] ss = mName.split("-");
                for (i = 0; i < ss.length - 1; i++) {
                    if (i != 0) {
                        mModel += "-";
                    }
                    mModel += ss[i];
                }
                try {
                    mVersion = Integer.parseInt(ss[ss.length - 1]);
                } catch (Exception ignored) {
                }

            } else if ((data[16] & 255) == 179) { // Serial Number
                byte[] sndata = new byte[18];
                System.arraycopy(data, 2, sndata, 0, 14);
                System.arraycopy(data, 17, sndata, 14, 3);
                sndata[17] = (byte) 0;
                mSerialNumber = new String(sndata);
            }
        }
        return false;
    }

    private boolean decodeGotway(byte[] data) {
        if (rideStartTime == 0)
            rideStartTime = Calendar.getInstance().getTimeInMillis();

        if (data.length >= 20) {
            int a1 = data[0] & 255;
            int a2 = data[1] & 255;
            int a19 = data[18] & 255;
            if (a1 != 85 || a2 != 170 || a19 != 0) {
                return false;
            }

            if (data[5] >= 0)
                mSpeed = (int) Math.abs(((data[4] * 256.0) + data[5]) * 3.6);
            else
                mSpeed = (int) Math.abs((((data[4] * 256.0) + 256.0) + data[5]) * 3.6);

            setTopSpeed(mSpeed);

            mTemperature = (int) Math.round(((((data[12] * 256) + data[13]) / 340.0) + 35) * 100);

            long distance = byteArrayInt2(data[9], data[8]);
            setDistance(distance);

            mVoltage = (data[2] * 256) + (data[3] & 255);

            mCurrent = Math.abs((data[10] * 256) + data[11]);

            int battery;
            if (mVoltage <= 5290) {
                battery = 0;
            } else if (mVoltage >= 6580) {
                battery = 100;
            } else {
                battery = (mVoltage - 5290) / 13;
            }
            setBatteryPercent(battery);

            int currentTime = (int) (Calendar.getInstance().getTimeInMillis() - rideStartTime) / 1000;
            setCurrentTime(currentTime);

            return true;
        } else if (data.length >= 10) {
            int a1 = data[0];
            int a5 = data[4] & 255;
            int a6 = data[5] & 255;
            if (a1 != 90 || a5 != 85 || a6 != 170) {
                return false;
            }

            mTotalDistance = ((((data[6] * 256) + data[7]) * 65536) + (((data[8] & 255) * 256) + (data[9] & 255)));
        }
        return false;
    }

    private boolean decodeNinebot(byte[] data) {
        return false;
    }

    void full_reset() {
        mBluetoothLeService = null;
        mWheelType = WHEEL_TYPE.Unknown;
        xAxis.clear();
        speedAxis.clear();
        currentAxis.clear();
        reset();
    }

    void reset() {
        mSpeed = 0;
        mTotalDistance = 0;
        mCurrent = 0;
        mTemperature = 0;
        mMode = 0;
        mBattery = 0;
        mAverageBatteryCount = 0;
        mAverageBattery = 0;
        mVoltage = 0;
        mRideTime = 0;
        mTopSpeed = 0;
        mFanStatus = 0;
        mName = "";
        mModel = "";
        mVersion = 0;
        mSerialNumber = "";
        rideStartTime = 0;
        mStartTotalDistance = 0;
    }

    boolean detectWheel(BluetoothLeService bluetoothService) {
        mBluetoothLeService = bluetoothService;
        Context mContext = bluetoothService.getApplicationContext();

        Class<R.array> res = R.array.class;
        String wheel_types[] = mContext.getResources().getStringArray(R.array.wheel_types);
        for (String wheel_Type : wheel_types) {
            boolean detected_wheel = true;
            java.lang.reflect.Field services_res = null;
            try {
                services_res = res.getField(wheel_Type + "_services");
            } catch (Exception ignored) {
            }
            int services_res_id = 0;
            if (services_res != null)
                try {
                    services_res_id = services_res.getInt(null);
                } catch (Exception ignored) {
                }

            String services[] = mContext.getResources().getStringArray(services_res_id);

            if (services.length != mBluetoothLeService.getSupportedGattServices().size())
                continue;

            for (String service_uuid : services) {
                UUID s_uuid = UUID.fromString(service_uuid.replace("_", "-"));
                BluetoothGattService service = mBluetoothLeService.getGattService(s_uuid);
                if (service != null) {
                    java.lang.reflect.Field characteristic_res = null;
                    try {
                        characteristic_res = res.getField(wheel_Type + "_" + service_uuid);
                    } catch (Exception ignored) {
                    }
                    int characteristic_res_id = 0;
                    if (characteristic_res != null)
                        try {
                            characteristic_res_id = characteristic_res.getInt(null);
                        } catch (Exception ignored) {
                        }
                    String characteristics[] = mContext.getResources().getStringArray(characteristic_res_id);
                    for (String characteristic_uuid : characteristics) {
                        UUID c_uuid = UUID.fromString(characteristic_uuid.replace("_", "-"));
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(c_uuid);
                        if (characteristic == null) {
                            detected_wheel = false;
                            break;
                        }
                    }
                } else {
                    detected_wheel = false;
                    break;
                }
            }

            if (detected_wheel) {
                if (mContext.getResources().getString(R.string.kingsong).equals(wheel_Type)) {
                    mWheelType = WHEEL_TYPE.KINGSONG;
                    BluetoothGattService targetService = mBluetoothLeService.getGattService(UUID.fromString(Constants.KINGSONG_SERVICE_UUID));
                    BluetoothGattCharacteristic notifyCharacteristic = targetService.getCharacteristic(UUID.fromString(Constants.KINGSONG_READ_CHARACTER_UUID));
                    mBluetoothLeService.setCharacteristicNotification(notifyCharacteristic, true);
                    BluetoothGattDescriptor descriptor = notifyCharacteristic.getDescriptor(UUID.fromString(Constants.KINGSONG_DESCRIPTER_UUID));
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    mBluetoothLeService.writeBluetoothGattDescriptor(descriptor);
                    return true;
                } else if (mContext.getResources().getString(R.string.gotway).equals(wheel_Type)) {
                    mWheelType = WHEEL_TYPE.GOTWAY;
                    BluetoothGattService targetService = mBluetoothLeService.getGattService(UUID.fromString(Constants.GOTWAY_SERVICE_UUID));
                    BluetoothGattCharacteristic notifyCharacteristic = targetService.getCharacteristic(UUID.fromString(Constants.GOTWAY_READ_CHARACTER_UUID));
                    mBluetoothLeService.setCharacteristicNotification(notifyCharacteristic, true);
                    // Let the user know it's working by making the wheel beep
                    mBluetoothLeService.writeBluetoothGattCharacteristic("b".getBytes());
                    return true;
                }
            }
        }
        return false;
    }

    private void setAlarms(int alarm1, int alarm2, int alarm3, int tilt_back) {
        byte[] data = new byte[20];
        data[0x0] = -86;
        data[0x1] = 85;
        data[0x2] = (byte) alarm1;
//        data[3] = 0;
        data[0x4] = (byte) alarm2;
//        data[5] = 0;
        data[0x6] = (byte) alarm3;
//        data[7] = 0;
        data[0x8] = (byte) tilt_back;
//        data[9] = 0;
        data[0x10] = -123;
        data[0x11] = 20;
        data[0x12] = 90;
        data[0x13] = 90;
        if(mBluetoothLeService != null)
            mBluetoothLeService.writeBluetoothGattCharacteristic(data);
    }

    public void readAlarmParas() {
        byte[] data = new byte[20];
        data[0] = -86;
        data[1] = 85;
        data[16] = -104;
        data[17] = 20;
        data[18] = 90;
        data[19] = 90;
        if(mBluetoothLeService != null)
            mBluetoothLeService.writeBluetoothGattCharacteristic(data);
    }

    private void setCyclingMode(CYCLING_MODE mode) {
        byte[] arrayOfByte = new byte[20];
        arrayOfByte[0] = -86;
        arrayOfByte[1] = 85;
        arrayOfByte[2] = (byte) mode.ordinal();
        arrayOfByte[3] = -32;
        arrayOfByte[16] = -121;
        arrayOfByte[17] = 20;
        arrayOfByte[18] = 90;
        arrayOfByte[19] = 90;
        if(mBluetoothLeService != null)
            mBluetoothLeService.writeBluetoothGattCharacteristic(arrayOfByte);
    }

    private void setLight(int value) {
        byte[] data = new byte[0x14];
        data[0x0] = -0x56;
        data[0x1] = 0x55;
        data[0x2] = (byte)value;
        data[0x10] = 0x53;
        data[0x11] = 0x14;
        data[0x12] = 0x5a;
        data[0x13] = 0x5a;
        if(mBluetoothLeService != null)
            mBluetoothLeService.writeBluetoothGattCharacteristic(data);
    }

    private void setLightMode(LIGHT_MODE mode) {
        byte[] data = new byte[0x14];
        data[0x0] = -0x56;
        data[0x1] = 0x55;
        if(mode == LIGHT_MODE.OFF)
            data[0x2] = 0x13;
        else if(mode == LIGHT_MODE.AUTO)
            data[0x2] = 0x14;
        else if(mode == LIGHT_MODE.ON)
            data[0x2] = 0x12;
        data[0x3] = (byte) 1; //getVoiceEnabled()
        data[0x10] = 0x73;
        data[0x11] = 0x14;
        data[0x12] = 0x5a;
        data[0x13] = 0x5a;
        if(mBluetoothLeService != null)
            mBluetoothLeService.writeBluetoothGattCharacteristic(data);
    }
/*
    private void setVoiceActivation(0 = voice activated; 1 = disabled) {
        byte[] data = new byte[0x14];
        data[0x0] = -0x56;
        data[0x1] = 0x55;
        data[0x3] = (byte) mode.ordinal();
        data[0x10] = 0x73;
        data[0x11] = 0x14;
        data[0x12] = 0x5a;
        data[0x13] = 0x5a;
        if(mBluetoothLeService != null)
            mBluetoothLeService.writeBluetoothGattCharacteristic(data);
    }*/

    private void setColorLight(int value) {
        byte[] data = new byte[0x14];
        data[0x0] = -0x56;
        data[0x1] = 0x55;
        data[0x2] = (byte) value;
        data[0x10] = 0x6c;
        data[0x11] = 0x14;
        data[0x12] = 0x5a;
        data[0x13] = 0x5a;
        if(mBluetoothLeService != null)
            mBluetoothLeService.writeBluetoothGattCharacteristic(data);
    }

    private void setColorLightMode(COLOR_LIGHT_MODE mode) {
        byte[] data = new byte[0x14];
        data[0x0] = -0x56;
        data[0x1] = 0x55;
        data[0x2] = (byte) mode.ordinal();
        data[0x10] = 0x50;
        data[0x11] = 0x14;
        data[0x12] = 0x5a;
        data[0x13] = 0x5a;
        if(mBluetoothLeService != null)
            mBluetoothLeService.writeBluetoothGattCharacteristic(data);
    }

    public void writeConfig(String param) {
        switch (param) {
            case "alarm1":
            case "alarm2":
            case "alarm3":
            case "tilt_back":
                setAlarms(mAlarm1, mAlarm2, mAlarm3, mTiltback);
                break;
            case "color_light_enabled":
                setColorLight(mColorLightEnabled ? 1 : 0);
                break;
            case "color_light_mode":
                setColorLightMode(mColorLightMode);
                break;
            case "light_enabled":
                setLight(mLightEnabled ? 1 : 0);
                break;
            case "light_mode":
                setLightMode(mLightMode);
                break;
            case "cycling_mode":
                setCyclingMode(mCyclingMode);
                break;
        }
    }
}
