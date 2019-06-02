package com.uber.rxcentralble.sample;

import android.annotation.TargetApi;
import android.app.Application;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;
import android.util.AndroidException;

import com.uber.rxcentralble.BluetoothDetector;
import com.uber.rxcentralble.ConnectionManager;
import com.uber.rxcentralble.GattManager;
import com.uber.rxcentralble.ScanData;
import com.uber.rxcentralble.ScanMatcher;
import com.uber.rxcentralble.Scanner;
import com.uber.rxcentralble.Utils;
import com.uber.rxcentralble.core.CoreBluetoothDetector;
import com.uber.rxcentralble.core.CoreConnectionManager;
import com.uber.rxcentralble.core.CoreGattIO;
import com.uber.rxcentralble.core.CoreGattManager;
import com.uber.rxcentralble.core.CoreParsedAdvertisement;
import com.uber.rxcentralble.core.operations.Read;
import com.uber.rxcentralble.core.scanners.LollipopScanData;
import com.uber.rxcentralble.core.scanners.LollipopScanner;

import java.util.UUID;

import io.reactivex.ObservableTransformer;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

import static com.uber.rxcentralble.ConnectionManager.DEFAULT_CONNECTION_TIMEOUT;
import static com.uber.rxcentralble.ConnectionManager.DEFAULT_SCAN_TIMEOUT;

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
    connectionManager = new CoreConnectionManager(this,
            new CoreBluetoothDetector(this),
            new CoreGattIO.Factory());
  }

  public BluetoothDetector getBluetoothDetector() { return bluetoothDetector; }

  public ConnectionManager getConnectionManager() {
    return connectionManager;
  }

  public GattManager getGattManager() {
    return gattManager;
  }
}
