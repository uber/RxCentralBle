package com.uber.rxcentralble.sample;

import android.annotation.TargetApi;
import android.app.Application;
import android.os.Build;

import com.uber.rxcentralble.BluetoothDetector;
import com.uber.rxcentralble.ConnectionManager;
import com.uber.rxcentralble.PeripheralManager;
import com.uber.rxcentralble.Scanner;
import com.uber.rxcentralble.Utils;
import com.uber.rxcentralble.core.CoreBluetoothDetector;
import com.uber.rxcentralble.core.CoreConnectionManager;
import com.uber.rxcentralble.core.CorePeripheral;
import com.uber.rxcentralble.core.CorePeripheralManager;
import com.uber.rxcentralble.core.CoreScannerFactory;

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
  private PeripheralManager peripheralManager;
  private BluetoothDetector bluetoothDetector;
  private Scanner scanner;

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  @Override
  public void onCreate() {
    super.onCreate();

    bluetoothDetector = new CoreBluetoothDetector(this.getApplicationContext());
    peripheralManager = new CorePeripheralManager();
    scanner = new CoreScannerFactory().produce();
    connectionManager = new CoreConnectionManager(this, bluetoothDetector,
            scanner, new CorePeripheral.Factory());
  }

  public BluetoothDetector getBluetoothDetector() {
    return bluetoothDetector;
  }

  public ConnectionManager getConnectionManager() {
    return connectionManager;
  }

  public PeripheralManager getPeripheralManager() {
    return peripheralManager;
  }

  public Scanner getScanner() {
    return scanner;
  }
}
