package com.uber.rxcentralble.sample;

import android.annotation.TargetApi;
import android.app.Application;
import android.os.Build;

import com.uber.rxcentralble.BluetoothDetector;
import com.uber.rxcentralble.ConnectionManager;
import com.uber.rxcentralble.GattManager;
import com.uber.rxcentralble.Utils;
import com.uber.rxcentralble.core.CoreBluetoothDetector;
import com.uber.rxcentralble.core.CoreConnectionManager;
import com.uber.rxcentralble.core.CoreGattIO;
import com.uber.rxcentralble.core.CoreGattManager;
import com.uber.rxcentralble.core.scanners.ThrottledLollipopScanner;

import java.util.UUID;

public class SampleApplication extends Application {

  public static final UUID GAP_SVC_UUID = Utils.uuidFromInteger(0x1800);
  public static final UUID GAP_DEVICE_NAME_UUID = Utils.uuidFromInteger(0x2A00);
  public static final UUID BATTERY_SVC_UUID = Utils.uuidFromInteger(0x180F);
  public static final UUID BATTERY_LEVEL_UUID = Utils.uuidFromInteger(0x2A19);
  public static final UUID DIS_SVC_UUID = Utils.uuidFromInteger(0x180A);
  public static final UUID DIS_MFG_NAME_UUID = Utils.uuidFromInteger(0x2A29);
  public static final UUID DIS_MODEL_UUID = Utils.uuidFromInteger(0x2A24);
  public static final UUID DIS_SERIAL_UUID = Utils.uuidFromInteger(0x2A25);
  public static final UUID DIS_HARDWARE_UUID = Utils.uuidFromInteger(0x2A27);
  public static final UUID DIS_FIRMWARE_UUID = Utils.uuidFromInteger(0x2A26);

  private ConnectionManager connectionManager;
  private GattManager gattManager;
  private BluetoothDetector bluetoothDetector;

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  @Override
  public void onCreate() {
    super.onCreate();

    bluetoothDetector = new CoreBluetoothDetector(this.getApplicationContext());
    gattManager = new CoreGattManager();
    connectionManager = new CoreConnectionManager(this, bluetoothDetector,
            new ThrottledLollipopScanner(), new CoreGattIO.Factory());
  }

  public BluetoothDetector getBluetoothDetector() {
    return bluetoothDetector;
  }

  public ConnectionManager getConnectionManager() {
    return connectionManager;
  }

  public GattManager getGattManager() {
    return gattManager;
  }
}
