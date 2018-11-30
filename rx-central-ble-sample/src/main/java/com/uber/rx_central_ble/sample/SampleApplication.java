package com.uber.rx_central_ble.sample;

import android.app.Application;
import com.uber.rx_central_ble.ConnectionManager;
import com.uber.rx_central_ble.GattManager;
import com.uber.rx_central_ble.Utils;
import com.uber.rx_central_ble.core.CoreBluetoothDetector;
import com.uber.rx_central_ble.core.CoreConnectionManager;
import com.uber.rx_central_ble.core.CoreGattIO;
import com.uber.rx_central_ble.core.CoreGattManager;

import java.util.UUID;

public class SampleApplication extends Application {

  public static final UUID GAP_SVC_UUID = Utils.uuidFromInteger(0x1800);
  public static final UUID GAP_DEVICE_NAME_UUID = Utils.uuidFromInteger(0x2A00);
  public static final UUID BATTERY_SVC_UUID = Utils.uuidFromInteger(0x180F);
  public static final UUID BATTERY_LEVEL_UUID = Utils.uuidFromInteger(0x2A19);

  private ConnectionManager connectionManager;
  private GattManager gattManager;

  @Override
  public void onCreate() {
    super.onCreate();

    gattManager = new CoreGattManager();
    connectionManager = new CoreConnectionManager(this,
            new CoreBluetoothDetector(this),
            new CoreGattIO.Factory());
  }

  public ConnectionManager getConnectionManager() {
    return connectionManager;
  }

  public GattManager getGattManager() {
    return gattManager;
  }
}
