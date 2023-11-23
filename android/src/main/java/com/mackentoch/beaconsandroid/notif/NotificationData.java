package com.mackentoch.beaconsandroid.notif;

import android.app.Notification;
import android.content.Context;

import androidx.annotation.DrawableRes;

import com.facebook.react.bridge.ReadableMap;
import com.mackentoch.beaconsandroid.NotificationHelper;

public class NotificationData {

  private String channelId;
  private int notificationId;

  private String contentTitle;
  private String contentMessage;
  private @DrawableRes
  int drawableIcon;
  private boolean showWhen;
  private int priority ;
  private int visibility;

  private Context mContext;

  public NotificationData(Context mContext, ReadableMap mMap) throws IllegalArgumentException {
    this.mContext = mContext;

    if (!mMap.hasKey("channelId") ||
        !mMap.hasKey("notificationId") ||
        !mMap.hasKey("contentTitle") ||
        !mMap.hasKey("contentMessage") ||
        !mMap.hasKey("drawableIcon") ||
      !mMap.hasKey("showWhen")
      ){
      throw new IllegalArgumentException("Missing required arguments from readable map..");
    }
    channelId = mMap.getString("channelId");
    notificationId = mMap.getInt("notificationId");
    contentTitle = mMap.getString("contentTitle");
    contentMessage = mMap.getString("contentMessage");
    drawableIcon = NotificationHelper.getResourceIdForResourceName(mContext, mMap.getString("drawableIcon"));
    showWhen = mMap.getBoolean("showWhen");
    if(mMap.hasKey("priority")){
      priority = NotificationHelper.getPriority(mMap.getString("priority"));
    }else{
      priority = NotificationHelper.DEFAULT_NOTIF_PRIORITY;
    }

    if(mMap.hasKey("visibility")){
      visibility = NotificationHelper.getVisibility(mMap.getString("visibility"));
    }else{
      visibility = NotificationHelper.DEFAULT_VISIBILITY;
    }
  }

  public String getChannelId() {
    return channelId;
  }

  public int getNotificationId() {
    return notificationId;
  }

  public String getContentTitle() {
    return contentTitle;
  }

  public String getContentMessage() {
    return contentMessage;
  }

  public int getDrawableIcon() {
    return drawableIcon;
  }

  public boolean isShowWhen() {
    return showWhen;
  }

  public int getPriority() {
    return priority;
  }

  public int getVisibility() {
    return visibility;
  }

  @Override
  public String toString() {
    return "NotificationData{" +
      "channelId='" + channelId + '\'' +
      ", notificationId=" + notificationId +
      ", contentTitle='" + contentTitle + '\'' +
      ", contentMessage='" + contentMessage + '\'' +
      ", drawableIcon=" + drawableIcon +
      ", showWhen=" + showWhen +
      ", priority=" + priority +
      ", visibility=" + visibility +
      '}';
  }
}
