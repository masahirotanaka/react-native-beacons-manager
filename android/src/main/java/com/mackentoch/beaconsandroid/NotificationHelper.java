package com.mackentoch.beaconsandroid;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.core.app.NotificationCompat;

public final class NotificationHelper {

  public static final int DEFAULT_NOTIFICATION_ID = 99999;
  public static final String DEFAULT_CHANNEL_ID = "Beacon-Notification";
  public static final int DEFAULT_NOTIF_PRIORITY = NotificationCompat.PRIORITY_MAX;
  public static final int DEFAULT_VISIBILITY = NotificationCompat.VISIBILITY_PUBLIC;


  public static int getResourceIdForResourceName(Context context, String resourceName) {
    int resourceId = context.getResources().getIdentifier(resourceName, "drawable", context.getPackageName());
    if (resourceId == 0) {
      resourceId = context.getResources().getIdentifier(resourceName, "mipmap", context.getPackageName());
    }
    return resourceId;
  }

  public static int getPriority(String priorityString) {
    if (priorityString != null) {
      switch(priorityString.toLowerCase()) {
        case "max":
          return NotificationCompat.PRIORITY_MAX;
        case "low":
          return NotificationCompat.PRIORITY_LOW;
        case "min":
          return NotificationCompat.PRIORITY_MIN;
        case "default":
          return NotificationCompat.PRIORITY_DEFAULT;
        default:
          return NotificationCompat.PRIORITY_HIGH;
      }
    }
    return NotificationCompat.PRIORITY_HIGH;
  }

  public static int getVisibility(String visibilityString){
    if (visibilityString != null) {
      switch(visibilityString.toLowerCase()) {
        case "private":
          return NotificationCompat.VISIBILITY_PRIVATE;
        case "secret":
          return NotificationCompat.VISIBILITY_SECRET;
        default:
          return NotificationCompat.VISIBILITY_PUBLIC;
      }
    }
    return NotificationCompat.VISIBILITY_PUBLIC;
  }
}
