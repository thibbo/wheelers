package com.cooper.wheellog.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.preference.PreferenceManager;

import com.cooper.wheellog.R;


public class SettingsUtil {

    private static final String key = "WheelLog";


    public static String getLastAddress(Context context) {
        SharedPreferences pref = context.getSharedPreferences(key, Context.MODE_PRIVATE);
        if (pref.contains("last_mac")) {
            return pref.getString("last_mac", "");
        }
        return "";
    }

    public static void setLastAddress(Context context, String address) {
        SharedPreferences.Editor editor = context.getSharedPreferences(key, Context.MODE_PRIVATE).edit();
        editor.putString("last_mac", address);
        editor.apply();
    }

    public static boolean isFirstRun(Context context) {
        SharedPreferences pref = context.getSharedPreferences(key, Context.MODE_PRIVATE);

        if (pref.contains("first_run"))
            return false;

        pref.edit().putBoolean("first_run", false).apply();
        return true;
    }

    public static boolean getBoolean(Context context, String preference) {
        return getSharedPreferences(context).getBoolean(preference, false);
    }

    public static boolean isAutoLogEnabled(Context context) {
        return getSharedPreferences(context).getBoolean(context.getString(R.string.auto_log), false);
    }

    public static void setAutoLog(Context context, boolean enabled) {
        getSharedPreferences(context).edit().putBoolean(context.getString(R.string.auto_log), enabled).apply();
    }

    public static boolean isLogLocationEnabled(Context context) {
        return getSharedPreferences(context).getBoolean(context.getString(R.string.log_location_data), false);
    }

    public static void setLogLocationEnabled(Context context, boolean enabled) {
        getSharedPreferences(context).edit().putBoolean(context.getString(R.string.log_location_data), enabled).apply();
    }

    public static boolean isUseGPSEnabled(Context context) {
        return getSharedPreferences(context).getBoolean(context.getString(R.string.use_gps), false);
    }

    public static boolean isAutoUploadEnabled(Context context) {
        return getSharedPreferences(context).getBoolean(context.getString(R.string.auto_upload), false);
    }

    public static void setAutoUploadEnabled(Context context, boolean enabled) {
        getSharedPreferences(context).edit().putBoolean(context.getString(R.string.auto_upload), enabled).apply();
    }

    public static boolean isUnlockerEnabled(Context context) {
        return getSharedPreferences(context).getBoolean(context.getString(R.string.unlocker_enabled), false);
    }

    public static void setUnlockerEnabled(Context context, boolean enabled) {
        getSharedPreferences(context).edit().putBoolean(context.getString(R.string.unlocker_enabled), enabled).apply();
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static boolean isUseMPH(Context context) {
        return getSharedPreferences(context).getBoolean(context.getString(R.string.use_mph), false);
    }

    public static int getMaxSpeed(Context context) {
        return getSharedPreferences(context).getInt(context.getString(R.string.max_speed), 30);
    }

    public static int getHornMode(Context context) {
        return Integer.parseInt(getSharedPreferences(context).getString(context.getString(R.string.horn_mode), "0"));
    }

    public static Constants.CYCLING_MODE getCyclingMode(Context context) {
        return Constants.CYCLING_MODE.values()[Integer.parseInt(getSharedPreferences(context).getString(context.getString(R.string.cycling_mode), "0"))];
    }

    public static boolean getVoiceEnabled(Context context) {
        return getSharedPreferences(context).getBoolean(context.getString(R.string.voice_enabled), false);
    }

    public static Constants.COLOR_LIGHT_MODE getColorLightMode(Context context) {
        return Constants.COLOR_LIGHT_MODE.values()[Integer.parseInt(getSharedPreferences(context).getString(context.getString(R.string.color_light_mode), "0"))];
    }

    public static boolean getColorLightEnabled(Context context) {
        return getSharedPreferences(context).getBoolean(context.getString(R.string.color_light_enabled), true);
    }

    public static Constants.LIGHT_MODE getLightMode(Context context) {
        return Constants.LIGHT_MODE.values()[Integer.parseInt(getSharedPreferences(context).getString(context.getString(R.string.light_mode), "0"))];
    }

    public static boolean getLightEnabled(Context context) {
        return getSharedPreferences(context).getBoolean(context.getString(R.string.light_enabled), true);
    }

    public static int getAlarm1(Context context) {
        return getSharedPreferences(context).getInt(context.getString(R.string.alarm1), 10);
    }

    public static int getAlarm2(Context context) {
        return getSharedPreferences(context).getInt(context.getString(R.string.alarm2), 15);
    }

    public static int getAlarm3(Context context) {
        return getSharedPreferences(context).getInt(context.getString(R.string.alarm3), 20);
    }

    public static int getTiltBack(Context context) {
        return getSharedPreferences(context).getInt(context.getString(R.string.tilt_back), 25);
    }
}
