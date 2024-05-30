package com.mackentoch.beaconsandroid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.RemoteException;

import org.altbeacon.beacon.logging.Loggers;
import org.jetbrains.annotations.Nullable;

import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.mackentoch.beaconsandroid.notif.NotificationData;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.BeaconTransmitter;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.service.ArmaRssiFilter;
import org.altbeacon.beacon.service.RunningAverageRssiFilter;
import org.altbeacon.bluetooth.BluetoothMedic;
import org.altbeacon.beacon.logging.LogManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Date;

public class BeaconsAndroidModule extends ReactContextBaseJavaModule implements  MonitorNotifier, RangeNotifier {
  private static final String LOG_TAG = "BeaconsAndroidModule";
  private static final int RUNNING_AVG_RSSI_FILTER = 0;
  private static final int ARMA_RSSI_FILTER = 1;
  private static final String TAG = BeaconsAndroidModule.class.getSimpleName();
  private BeaconManager mBeaconManager;
  private Context mApplicationContext;
  private ReactApplicationContext mReactContext;
  private NotificationData notifData = null;
  private BeaconTransmitter beaconTransmitter = null;

  private RNLogger logger;

  public BeaconsAndroidModule(ReactApplicationContext reactContext) {
    super(reactContext);
    Log.d(LOG_TAG, "BeaconsAndroidModule - started");
    this.mReactContext = reactContext;
  }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void initialize() {
        this.logger = new RNLogger();
        this.mApplicationContext = this.mReactContext.getApplicationContext();
        this.logger.i("BeaconsAndroidModule", "Initializing BeaconAndroidModule");

        // @TODO
        // BluetoothMedic.getInstance().legacyEnablePowerCycleOnFailures(this) // Android 4-12 only
        // BluetoothMedic.getInstance().enablePeriodicTests(this, BluetoothMedic.SCAN_TEST + BluetoothMedic.TRANSMIT_TEST)
        this.mBeaconManager = BeaconManager.getInstanceForApplication(mApplicationContext);

        // need to bind at instantiation so that service loads (to test more)
        // mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:0-3=4c000215,i:4-19,i:20-21,i:22-23,p:24-24"));
        mBeaconManager.getBeaconParsers().clear();
        BeaconParser parser = new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24");
        this.mBeaconManager.getBeaconParsers().add(parser);

        // If we were monitoring *different* regions on the last run of this app, they will be
        // remembered.  In this case we need to disable them here
        for (Region region: this.mBeaconManager.getMonitoredRegions()) {
          this.mBeaconManager.stopMonitoring(region);
          this.mBeaconManager.stopRangingBeacons(region);
        }

        this.mBeaconManager.removeAllMonitorNotifiers();
        this.mBeaconManager.removeAllRangeNotifiers();

        Log.d(TAG, "setting up background monitoring in app onCreate");
        this.mBeaconManager.addMonitorNotifier(this);
        this.mBeaconManager.addRangeNotifier(this);

        if (this.mBeaconManager.isAnyConsumerBound()) {
            this.logger.e("BeaconsAndroidModule", "Consumer is already bound!");
        } else {
            this.mBeaconManager.setEnableScheduledScanJobs(false);
        }
        this.mBeaconManager.setBackgroundBetweenScanPeriod(15 * 1000);
        this.mBeaconManager.setBackgroundScanPeriod(1100);
        // this.mBeaconManager.setIntentScanningStrategyEnabled(true);

        if (this.beaconTransmitter == null) {
            this.beaconTransmitter = new BeaconTransmitter(mApplicationContext, new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
        }
    }

    @Override
  public String getName() {
      return LOG_TAG;
  }

  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("SUPPORTED", BeaconTransmitter.SUPPORTED);
    constants.put("NOT_SUPPORTED_MIN_SDK", BeaconTransmitter.NOT_SUPPORTED_MIN_SDK);
    constants.put("NOT_SUPPORTED_BLE", BeaconTransmitter.NOT_SUPPORTED_BLE);
    constants.put("NOT_SUPPORTED_CANNOT_GET_ADVERTISER_MULTIPLE_ADVERTISEMENTS", BeaconTransmitter.NOT_SUPPORTED_CANNOT_GET_ADVERTISER_MULTIPLE_ADVERTISEMENTS);
    constants.put("NOT_SUPPORTED_CANNOT_GET_ADVERTISER", BeaconTransmitter.NOT_SUPPORTED_CANNOT_GET_ADVERTISER);
    constants.put("RUNNING_AVG_RSSI_FILTER",RUNNING_AVG_RSSI_FILTER);
    constants.put("ARMA_RSSI_FILTER",ARMA_RSSI_FILTER);
    return constants;
  }

  @ReactMethod
  public void transmitBeacon(String uuid, int major, int minor, int measuredPower, Callback resolve, Callback reject) {
    try {
      if (this.beaconTransmitter.isStarted()) return;
      Beacon beacon = new Beacon.Builder()
        .setId1(uuid)
        .setId2(String.valueOf(major))
        .setId3(String.valueOf(minor))
        .setManufacturer(0x004C)
        .setTxPower(measuredPower)
        .build();
      this.beaconTransmitter.startAdvertising(beacon, new AdvertiseCallback() {
        @Override
        public void onStartFailure(int errorCode) {
          super.onStartFailure(errorCode);
          reject.invoke(errorCode);
        }
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
          super.onStartSuccess(settingsInEffect);
          resolve.invoke();
        }
      });
    } catch (Exception e) {
      reject.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void stopTransmittingBeacon(Callback resolve, Callback reject) {
    try {
      if (!this.beaconTransmitter.isStarted()) return;
      this.beaconTransmitter.stopAdvertising();
      resolve.invoke();
    } catch (Exception e) {
      reject.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void setHardwareEqualityEnforced(Boolean e) {
    Beacon.setHardwareEqualityEnforced(e.booleanValue());
  }

  private Notification generateNotification(String extraMessage){
    NotificationManager notificationManager =
      (NotificationManager) this.mApplicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
    NotificationCompat.Builder builder;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(notifData.getChannelId(),
        "出席管理通知", NotificationManager.IMPORTANCE_HIGH);
      channel.enableLights(false);
      channel.enableVibration(false);
      channel.setLockscreenVisibility(notifData.getVisibility());
      notificationManager.createNotificationChannel(channel);
      builder = new NotificationCompat.Builder(this.mApplicationContext, channel.getId());
    }
    else {
      builder = new NotificationCompat.Builder(this.mApplicationContext);
      builder.setPriority(Notification.PRIORITY_HIGH);
    }

    TaskStackBuilder stackBuilder = TaskStackBuilder.create(this.mApplicationContext);
    Class<?> cls = mReactContext.hasCurrentActivity() ? Objects.requireNonNull(mReactContext.getCurrentActivity()).getClass() : Objects.requireNonNull(mReactContext.getApplicationContext()).getClass();
    stackBuilder.addNextIntent(new Intent(this.mApplicationContext, cls));
    PendingIntent resultPendingIntent =
      stackBuilder.getPendingIntent(
        0,
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT
      );
    builder.setSmallIcon(notifData.getDrawableIcon());
    builder.setContentTitle(notifData.getContentTitle());
    if(!extraMessage.isEmpty()){
      String fullText = notifData.getContentMessage() + " " + extraMessage;
      builder.setContentText(fullText);
      builder.setStyle(new NotificationCompat.BigTextStyle()
        .bigText(fullText));
    }
    else{
      builder.setContentText(notifData.getContentMessage());
    }

    builder.setContentIntent(resultPendingIntent);
    builder.setShowWhen(notifData.isShowWhen());

    return builder.build();
  }

  @ReactMethod
  public void startForegroundService(ReadableMap readableMap, Callback resolve, Callback reject){
    NotificationManager mNotificationManager = (NotificationManager) mApplicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
    notifData = new NotificationData(this.mApplicationContext, readableMap);
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
      boolean didResolved = false;
      StatusBarNotification[] notifications = mNotificationManager.getActiveNotifications();
      for (StatusBarNotification notification : notifications) {
        if (notification.getId() == notifData.getNotificationId()) {
          didResolved = true;
        }
      }
      if(didResolved){
        resolve.invoke(notifData.getNotificationId());
      }else{
        try {
          mBeaconManager.enableForegroundServiceScanning(generateNotification(""), notifData.getNotificationId());
          mBeaconManager.setEnableScheduledScanJobs(false);
          mBeaconManager.setBackgroundBetweenScanPeriod(60 * 1000); // Each 60s run background scan
          mBeaconManager.setBackgroundScanPeriod(1100l);            // Background scan for 1.1 seconds

          resolve.invoke(notifData.getNotificationId());
        }catch(Exception e){
          Log.d(TAG, "Notif Exception " + e);
          reject.invoke(e.getMessage());
        }
      }

    }else{
      try {
        mBeaconManager.enableForegroundServiceScanning(generateNotification(""), notifData.getNotificationId());
        mBeaconManager.setEnableScheduledScanJobs(false);
        mBeaconManager.setBackgroundBetweenScanPeriod(60 * 1000); // Each 60s run background scan
        mBeaconManager.setBackgroundScanPeriod(1100l);            // Background scan for 1.1 seconds

        resolve.invoke(notifData.getNotificationId());
      }catch(Exception e){
        Log.d(TAG, "Notif Exception " + e);
        reject.invoke(e.getMessage());
      }
    }
  }

  @ReactMethod
  public void stopForegroundService(Callback resolve, Callback reject){
    try{
      if(mBeaconManager.isAnyConsumerBound()){
        for (Region region: mBeaconManager.getMonitoredRegions()) {
          mBeaconManager.stopMonitoring(region);
          mBeaconManager.stopRangingBeacons(region);

        }
        mBeaconManager.removeAllRangeNotifiers();
        mBeaconManager.removeAllMonitorNotifiers();
      }
      mBeaconManager.disableForegroundServiceScanning();
      mBeaconManager.setEnableScheduledScanJobs(true);
      mBeaconManager.setBackgroundBetweenScanPeriod(mBeaconManager.getForegroundBetweenScanPeriod());
      mBeaconManager.setBackgroundScanPeriod(mBeaconManager.getBackgroundScanPeriod());
      notifData = null;
      resolve.invoke(!mBeaconManager.isAnyConsumerBound());
    }catch(Exception e){
      reject.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void addParser(String parser, Callback resolve, Callback reject) {
    try {
      Log.d(LOG_TAG, "BeaconsAndroidModule - addParser: " + parser);
      mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(parser));
      resolve.invoke();
    } catch (Exception e) {
      reject.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void removeParser(String parser, Callback resolve, Callback reject) {
    try {
      Log.d(LOG_TAG, "BeaconsAndroidModule - removeParser: " + parser);
      mBeaconManager.getBeaconParsers().remove(new BeaconParser().setBeaconLayout(parser));
      resolve.invoke();
    } catch (Exception e) {
      reject.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void addParsersListToDetection(ReadableArray parsers, Callback resolve, Callback reject) {
    try {
      for (int i = 0; i < parsers.size(); i++) {
        String parser = parsers.getString(i);
        Log.d(LOG_TAG, "addParsersListToDetection - add parser: " + parser);
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(parser));
      }
      resolve.invoke(parsers);
    } catch (Exception e) {
      reject.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void removeParsersListToDetection(ReadableArray parsers, Callback resolve, Callback reject) {
    try {
      for (int i = 0; i < parsers.size(); i++) {
        String parser = parsers.getString(i);
        Log.d(LOG_TAG, "removeParsersListToDetection - remove parser: " + parser);
        mBeaconManager.getBeaconParsers().remove(new BeaconParser().setBeaconLayout(parser));
      }
      resolve.invoke(parsers);
    } catch (Exception e) {
      reject.invoke(e.getMessage());
    }
  }


  @ReactMethod
  public void getRNLogs(Callback callback) {
      callback.invoke(this.logger.getLogs());
  }

    @ReactMethod
  public void setDebug(boolean debug) {
      if (debug) {
          LogManager.setLogger(this.logger);
          LogManager.setVerboseLoggingEnabled(true);
      } else {
          LogManager.setLogger(Loggers.empty());
          LogManager.setVerboseLoggingEnabled(false);
      }
  }

  @ReactMethod
  public void setEnableScheduledScanJobs(boolean enableScheduledScanJobs){
      mBeaconManager.setEnableScheduledScanJobs(enableScheduledScanJobs);
  }

  @ReactMethod
  public void setBackgroundMode(boolean newMode) {
      mBeaconManager.setBackgroundMode(newMode);
  }

  @ReactMethod
  public void setBackgroundScanPeriod(int period) {
      mBeaconManager.setBackgroundScanPeriod((long) period);
  }

  @ReactMethod
  public void setBackgroundBetweenScanPeriod(int period) {
      mBeaconManager.setBackgroundBetweenScanPeriod((long) period);
  }

  @ReactMethod
  public void setForegroundScanPeriod(int period) {
      mBeaconManager.setForegroundScanPeriod((long) period);
  }

  @ReactMethod
  public void setForegroundBetweenScanPeriod(int period) {
      mBeaconManager.setForegroundBetweenScanPeriod((long) period);
  }

  @ReactMethod
  public void setRssiFilter(int filterType, double avgModifier) {
      String logMsg = "Could not set the rssi filter.";
      if (filterType==RUNNING_AVG_RSSI_FILTER){
        logMsg="Setting filter RUNNING_AVG";
        BeaconManager.setRssiFilterImplClass(RunningAverageRssiFilter.class);
        if (avgModifier>0){
          RunningAverageRssiFilter.setSampleExpirationMilliseconds((long) avgModifier);
          logMsg+=" with custom avg modifier";
        }
      } else if (filterType==ARMA_RSSI_FILTER){
        logMsg="Setting filter ARMA";
        BeaconManager.setRssiFilterImplClass(ArmaRssiFilter.class);
        if (avgModifier>0){
          ArmaRssiFilter.setDEFAULT_ARMA_SPEED(avgModifier);
          logMsg+=" with custom avg modifier";
        }
      }
      Log.d(LOG_TAG, logMsg);
  }

  @ReactMethod
  public void checkTransmissionSupported(Callback callback) {
      int result = BeaconTransmitter.checkTransmissionSupported(mReactContext);
      callback.invoke(result);
  }

  @ReactMethod
  public void getMonitoredRegions(Callback callback) {
      WritableArray array = new WritableNativeArray();
      for (Region region: mBeaconManager.getMonitoredRegions()) {
          WritableMap map = new WritableNativeMap();
          map.putString("identifier", region.getUniqueId());
          map.putString("uuid", region.getId1().toString());
          map.putInt("major", region.getId2() != null ? region.getId2().toInt() : 0);
          map.putInt("minor", region.getId3() != null ? region.getId3().toInt() : 0);
          array.pushMap(map);
      }
      callback.invoke(array);
  }

  @ReactMethod
  public void getRangedRegions(Callback callback) {
      WritableArray array = new WritableNativeArray();
      for (Region region: mBeaconManager.getRangedRegions()) {
          WritableMap map = new WritableNativeMap();
          map.putString("region", region.getUniqueId());
          map.putString("uuid", region.getId1().toString());
          map.putInt("major", region.getId2() != null ? region.getId2().toInt() : 0);
          map.putInt("minor", region.getId3() != null ? region.getId3().toInt() : 0);
          array.pushMap(map);
      }
      callback.invoke(array);
  }

  /***********************************************************************************************
   * Monitoring
   **********************************************************************************************/
  @ReactMethod
  public void startMonitoring(String regionId, String beaconUuid, int minor, int major, Callback resolve, Callback reject) {
      Log.d(LOG_TAG, "startMonitoring, monitoringRegionId: " + regionId + ", monitoringBeaconUuid: " + beaconUuid + ", minor: " + minor + ", major: " + major);
      try {
          Region region = createRegion(
            regionId,
            beaconUuid,
            String.valueOf(minor).equals("-1") ? "" : String.valueOf(minor),
            String.valueOf(major).equals("-1") ? "" : String.valueOf(major)
          );
          mBeaconManager.startMonitoring(region);
          resolve.invoke();
      } catch (Exception e) {
          Log.e(LOG_TAG, "startMonitoring, error: ", e);
          reject.invoke(e.getMessage());
      }
  }

  private WritableMap createMonitoringResponse(Region region) {
      WritableMap map = new WritableNativeMap();
      map.putString("identifier", region.getUniqueId());
      map.putString("uuid", region.getId1() != null ? region.getId1().toString() : "");
      map.putInt("major", region.getId2() != null ? region.getId2().toInt() : 0);
      map.putInt("minor", region.getId3() != null ? region.getId3().toInt() : 0);
      return map;
  }

  @ReactMethod
  public void stopMonitoring(String regionId, String beaconUuid, int minor, int major, Callback resolve, Callback reject) {
      Region region = createRegion(
        regionId,
        beaconUuid,
        String.valueOf(minor).equals("-1") ? "" : String.valueOf(minor),
        String.valueOf(major).equals("-1") ? "" : String.valueOf(major)
        // minor,
        // major
      );

      try {
          mBeaconManager.stopMonitoring(region);
          resolve.invoke();
      } catch (Exception e) {
          Log.e(LOG_TAG, "stopMonitoring, error: ", e);
          reject.invoke(e.getMessage());
      }
  }

  /***********************************************************************************************
   * Ranging
   **********************************************************************************************/
  @ReactMethod
  public void startRanging(String regionId, String beaconUuid, Callback resolve, Callback reject) {
      Log.d(LOG_TAG, "startRanging, rangingRegionId: " + regionId + ", rangingBeaconUuid: " + beaconUuid);
      try {
          Region region = createRegion(regionId, beaconUuid);
          mBeaconManager.startRangingBeacons(region);
          resolve.invoke();
      } catch (Exception e) {
          Log.e(LOG_TAG, "startRanging, error: ", e);
          reject.invoke(e.getMessage());
      }
  }

  private WritableMap createRangingResponse(Collection<Beacon> beacons, Region region) {
      WritableMap map = new WritableNativeMap();
      map.putString("identifier", region.getUniqueId());
      map.putString("uuid", region.getId1() != null ? region.getId1().toString() : "");
      map.putDouble("receivedAtNative", (new Date()).getTime() / 1000.0);
      WritableArray a = new WritableNativeArray();
      for (Beacon beacon : beacons) {
          WritableMap b = new WritableNativeMap();
          b.putString("uuid", beacon.getId1().toString());
          if (beacon.getIdentifiers().size() > 2) {
              b.putInt("major", beacon.getId2().toInt());
              b.putInt("minor", beacon.getId3().toInt());
          }
          b.putInt("rssi", beacon.getRssi());
          if(beacon.getDistance() == Double.POSITIVE_INFINITY
                    || Double.isNaN(beacon.getDistance())
                    || beacon.getDistance() == Double.NaN
                    || beacon.getDistance() == Double.NEGATIVE_INFINITY){
                b.putDouble("distance", 999.0);
                b.putString("proximity", "far");
            }else {
                b.putDouble("distance", beacon.getDistance());
                b.putString("proximity", getProximity(beacon.getDistance()));
            }
          a.pushMap(b);
      }
      map.putArray("beacons", a);
      return map;
  }

  private String getProximity(double distance) {
      if (distance == -1.0) {
          return "unknown";
      } else if (distance < 1) {
          return "immediate";
      } else if (distance < 3) {
          return "near";
      } else {
          return "far";
      }
  }

  @ReactMethod
  public void stopRanging(String regionId, String beaconUuid, Callback resolve, Callback reject) {
      Region region = createRegion(regionId, beaconUuid);
      try {
          mBeaconManager.stopRangingBeacons(region);
          resolve.invoke();
      } catch (Exception e) {
          Log.e(LOG_TAG, "stopRanging, error: ", e);
          reject.invoke(e.getMessage());
      }
  }

  @ReactMethod
  public void requestStateForRegion(String regionId, String beaconUuid, int minor, int major) {
      Region region = createRegion(
        regionId,
        beaconUuid,
        String.valueOf(minor).equals("-1") ? "" : String.valueOf(minor),
        String.valueOf(major).equals("-1") ? "" : String.valueOf(major)
      );
      mBeaconManager.requestStateForRegion(region);
  }


  /***********************************************************************************************
   * Utils
   **********************************************************************************************/
  private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
      if (reactContext.hasActiveCatalystInstance()) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
      }
  }

  private Region createRegion(String regionId, String beaconUuid) {
      Identifier id1 = (beaconUuid == null) ? null : Identifier.parse(beaconUuid);
      return new Region(regionId, id1, null, null);
  }

  private Region createRegion(String regionId, String beaconUuid, String minor, String major) {
      Identifier id1 = (beaconUuid == null) ? null : Identifier.parse(beaconUuid);
      return new Region(
        regionId,
        id1,
        major.length() > 0 ? Identifier.parse(major) : null,
        minor.length() > 0 ? Identifier.parse(minor) : null
      );
  }

  /***********************************************************************************************
   * Monitor Notifier
   **********************************************************************************************/
  @Override
  public void didEnterRegion(Region region) {
      sendEvent(mReactContext, "regionDidEnter", createMonitoringResponse(region));
  }

  @Override
  public void didExitRegion(Region region) {
      sendEvent(mReactContext, "regionDidExit", createMonitoringResponse(region));
  }

  /***********************************************************************************************
   * Determine State Monitor Notifier
   **********************************************************************************************/
  @Override
  public void didDetermineStateForRegion(int i, Region region) {
      String state = "unknown";
      switch (i) {
        case MonitorNotifier.INSIDE:
          state = "inside";
          break;
        case MonitorNotifier.OUTSIDE:
          state = "outside";
          break;
        default:
          break;
      }
      WritableMap map = createMonitoringResponse(region);
      map.putString("state", state);
      sendEvent(mReactContext, "didDetermineState", map);
  }

  /***********************************************************************************************
   * Range Notifier
   **********************************************************************************************/
  @Override
  public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
    Log.d(LOG_TAG, "didRangeBeaconsInRegion, region: " + region.toString() + " beacons: " + beacons.toString());
    sendEvent(mReactContext, "beaconsDidRange", createRangingResponse(beacons, region));
    StringBuilder stringBuilder = new StringBuilder();
    int count = 0;
  }
}
