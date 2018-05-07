package com.google.ar.core.examples.java.common.helpers;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

public final class StoragePermissionHelper {
  public static final int STORAGE_PERMISSION_CODE = 1;
  private static final String STORAGE_PERMISSION = Manifest.permission.READ_EXTERNAL_STORAGE;

  /** Check to see we have the necessary permissions for this app. */
  public static boolean hasPermission(Activity activity) {
    return ContextCompat.checkSelfPermission(activity, STORAGE_PERMISSION)
            == PackageManager.PERMISSION_GRANTED;
  }

  /** Check to see we have the necessary permissions for this app, and ask for them if we don't. */
  public static void requestPermission(Activity activity) {
    ActivityCompat.requestPermissions(
            activity, new String[] {STORAGE_PERMISSION}, STORAGE_PERMISSION_CODE);
  }

  /** Check to see if we need to show the rationale for this permission. */
  public static boolean shouldShowRequestPermissionRationale(Activity activity) {
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, STORAGE_PERMISSION);
  }

  /** Launch Application Setting to grant permission. */
  // TODO: should pull up common functions with CameraPermissionHelper
  public static void launchPermissionSettings(Activity activity) {
    Intent intent = new Intent();
    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
    activity.startActivity(intent);
  }
}
