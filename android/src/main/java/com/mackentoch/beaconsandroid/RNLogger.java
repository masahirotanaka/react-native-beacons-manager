package com.mackentoch.beaconsandroid;

import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.Date;
import org.altbeacon.beacon.logging.Logger;

import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Arguments;

import java.util.TimeZone;

class RNLogger implements Logger {

    final private Set<String> logs = new HashSet<>();

    public WritableArray getLogs() {
        WritableArray result = Arguments.makeNativeArray(logs.toArray());
        logs.clear();
        return result;
    }

    private void add(String str) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.JAPAN);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        logs.add(dateFormat.format(new Date()) + " [NATIVE] " + str);
    }

    protected String formatString(String message, Object... args) {
        // If no varargs are supplied, treat it as a request to log the string without formatting.
        return args.length == 0 ? message : String.format(message, args);
    }

    @Override
    public void v(String tag, String message, Object... args) {
        // Log.v(tag, formatString(message, args));
        // add("[V]" + tag + " " + formatString(message, args));
    }

    @Override
    public void v(Throwable t, String tag, String message, Object... args) {
        // Log.v(tag, formatString(message, args), t);
        // add("[V]" + tag + " " + formatString(message, args) + " " + t.getMessage());
    }

    @Override
    public void d(String tag, String message, Object... args) {
        // Log.d(tag, formatString(message, args));
        // add("[D]" + tag + " " + formatString(message, args));
    }

    @Override
    public void d(Throwable t, String tag, String message, Object... args) {
        // Log.d(tag, formatString(message, args), t);
        // add("[D]" + tag + " " + formatString(message, args) + " " + t.getMessage());
    }

    @Override
    public void i(String tag, String message, Object... args) {
        Log.i(tag, formatString(message, args));
        add("[I]" + tag + " " + formatString(message, args));
    }

    @Override
    public void i(Throwable t, String tag, String message, Object... args) {
        Log.i(tag, formatString(message, args), t);
        add("[I]" + tag + " " + formatString(message, args) + " " + t.getMessage());
    }

    @Override
    public void w(String tag, String message, Object... args) {
        Log.w(tag, formatString(message, args));
        add("[W]" + tag + " " + formatString(message, args));
    }

    @Override
    public void w(Throwable t, String tag, String message, Object... args) {
        Log.w(tag, formatString(message, args), t);
        add("[W]" + tag + " " + formatString(message, args) + " " + t.getMessage());
    }

    @Override
    public void e(String tag, String message, Object... args) {
        Log.e(tag, formatString(message, args));
        add("[E]" + tag + " " + formatString(message, args));
    }

    @Override
    public void e(Throwable t, String tag, String message, Object... args) {
        Log.e(tag, formatString(message, args), t);
        add("[E]" + tag + " " + formatString(message, args) + " " + t.getMessage());
    }
}
