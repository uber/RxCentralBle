package com.uber.rxcentralble.sample;

import android.Manifest;
import android.annotation.SuppressLint;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.uber.rxcentralble.BluetoothDetector;
import com.uber.rxcentralble.ConnectionError;
import com.uber.rxcentralble.ConnectionManager;
import com.uber.rxcentralble.PeripheralManager;
import com.uber.rxcentralble.Irrelevant;
import com.uber.rxcentralble.RxCentralLogger;
import com.uber.rxcentralble.ScanMatcher;
import com.uber.rxcentralble.Scanner;
import com.uber.rxcentralble.core.operations.Read;
import com.uber.rxcentralble.core.operations.RegisterNotification;
import com.uber.rxcentralble.core.operations.RequestMtu;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import pub.devrel.easypermissions.EasyPermissions;
import timber.log.Timber;

import static com.uber.rxcentralble.ConnectionManager.DEFAULT_CONNECTION_TIMEOUT;
import static com.uber.rxcentralble.ConnectionManager.DEFAULT_SCAN_TIMEOUT;

public class MainActivity extends AppCompatActivity {

  private BluetoothDetector bluetoothDetector;
  private ConnectionManager connectionManager;
  private PeripheralManager peripheralManager;
  private Scanner scanner;

  @BindView(R.id.nameEditText)
  EditText nameEditText;

  @BindView(R.id.logTextView)
  TextView logTextView;

  @BindView(R.id.buttonConnect)
  Button connectButton;

  @BindView(R.id.toggleButtonDirectConnect)
  ToggleButton directConnectToggle;

  Disposable bluetoothDetection;
  Disposable connection;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    ButterKnife.bind(this);

    bluetoothDetector = ((SampleApplication) getApplication()).getBluetoothDetector();
    connectionManager = ((SampleApplication) getApplication()).getConnectionManager();
    peripheralManager = ((SampleApplication) getApplication()).getPeripheralManager();
    scanner = ((SampleApplication) getApplication()).getScanner();

    Timber.plant(new TextViewLoggingTree(logTextView));

    if (BuildConfig.DEBUG) {
      Timber.plant(new Timber.DebugTree());

      Disposable d = RxCentralLogger
              .logs(RxCentralLogger.LogLevel.DEBUG)
              .subscribe(message -> Timber.d(message));
    }

    String[] perms = {
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    };

    if (!EasyPermissions.hasPermissions(this, perms)) {
      EasyPermissions.requestPermissions(this, "Permissions", 99, perms);
    }

    logTextView.setMovementMethod(new ScrollingMovementMethod());
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    // Forward results to EasyPermissions
    EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
  }

  @OnClick(R.id.buttonConnect)
  public void connectClick() {
    if (directConnectToggle.isChecked()) {
      directConnect();
    } else {
      connect();
    }
  }

  @SuppressLint("CheckResult")
  @OnClick(R.id.gapButton)
  public void getGap() {
    peripheralManager
        .queueOperation(new Read(SampleApplication.GAP_SVC_UUID, SampleApplication.GAP_DEVICE_NAME_UUID, 5000))
        .map(bytes -> new String(bytes, "UTF-8"))
        .subscribe(
          name -> Timber.i("Get GAP: success: " + name),
          error -> Timber.i("Get GAP: error: " + error.getMessage()));
  }

  @SuppressLint("CheckResult")
  @OnClick(R.id.batteryButton)
  public void getBattery() {
    peripheralManager
        .queueOperation(new Read(SampleApplication.BATTERY_SVC_UUID, SampleApplication.BATTERY_LEVEL_UUID, 5000))
        .map(bytes -> bytes.length > 0 ? (int) bytes[0] : -1)
        .subscribe(
          batteryLevel -> Timber.i("Get Battery: success: " + batteryLevel),
          error -> Timber.i("Get Battery: error: " + error.getMessage()));

    peripheralManager
        .queueOperation(new RegisterNotification(
                SampleApplication.BATTERY_SVC_UUID,
                SampleApplication.BATTERY_LEVEL_UUID,
                5000))
        .flatMapObservable(irrelevant -> peripheralManager.notification(SampleApplication.BATTERY_LEVEL_UUID))
        .map(bytes -> bytes.length > 0 ? (int) bytes[0] : -1)
        .subscribe(
          batteryLevel -> Timber.i("Notif Battery: success: " + batteryLevel),
          error -> Timber.i("Notif Battery: error: " + error.getMessage()));
  }

  @SuppressLint("CheckResult")
  @OnClick(R.id.disButton)
  public void getDIS() {
    peripheralManager
            .queueOperation(new Read(SampleApplication.DIS_SVC_UUID, SampleApplication.DIS_MFG_NAME_UUID, 5000))
            .map(bytes -> new String(bytes, "UTF-8"))
            .subscribe(
              name -> Timber.i("Get DIS Mfg: success: " + name),
              error -> Timber.i("Get DIS Mfg: error: " + error.getMessage()));

    peripheralManager
            .queueOperation(new Read(SampleApplication.DIS_SVC_UUID, SampleApplication.DIS_MODEL_UUID, 5000))
            .map(bytes -> new String(bytes, "UTF-8"))
            .subscribe(
              name -> Timber.i("Get DIS Model: success: " + name),
              error -> Timber.i("Get DIS Model: error: " + error.getMessage()));

    peripheralManager
            .queueOperation(new Read(SampleApplication.DIS_SVC_UUID, SampleApplication.DIS_SERIAL_UUID, 5000))
            .map(bytes -> new String(bytes, "UTF-8"))
            .subscribe(
              name -> Timber.i("Get DIS Serial: success: " + name),
              error -> Timber.i("Get DIS Serial: error: " + error.getMessage()));

    peripheralManager
            .queueOperation(new Read(SampleApplication.DIS_SVC_UUID, SampleApplication.DIS_HARDWARE_UUID, 5000))
            .map(bytes -> new String(bytes, "UTF-8"))
            .subscribe(
              name -> Timber.i("Get DIS Hardware: success: " + name),
              error -> Timber.i("Get DIS Hardware: error: " + error.getMessage()));

    peripheralManager
            .queueOperation(new Read(SampleApplication.DIS_SVC_UUID, SampleApplication.DIS_FIRMWARE_UUID, 5000))
            .map(bytes -> new String(bytes, "UTF-8"))
            .subscribe(
              name -> Timber.i("Get DIS Firmware: success: " + name),
              error -> Timber.i("Get DIS Firmware: error: " + error.getMessage()));
  }

  @SuppressLint("CheckResult")
  @OnClick(R.id.mtuButton)
  public void getMtu() {
    peripheralManager
            .queueOperation(new RequestMtu(512, 5000))
            .subscribe(
              mtu -> Timber.i("MTU: success: " + mtu),
              error -> Timber.i("MTU: error: " + error.getMessage()));
  }

  @OnCheckedChanged(R.id.toggleButtonBleDetect)
  public void bleDetect(CompoundButton button, boolean checked) {
    if (checked && bluetoothDetection == null) {
      Timber.i("Bluetooth Detection Active");
      bluetoothDetection = new CompositeDisposable();
      bluetoothDetection = bluetoothDetector
              .enabled()
              .subscribe(enabled -> {
                if (enabled) {
                  Timber.i("Bluetooth Enabled");
                } else {
                  Timber.i("Bluetooth Disabled");
                }
              });
    } else if (bluetoothDetection != null) {
      bluetoothDetection.dispose();
      bluetoothDetection = null;
      Timber.i("Bluetooth Detection Deactivated");
    }
  }

  private void directConnect() {
    if (connection == null) {
      String name = nameEditText.getEditableText().toString();
      if (!TextUtils.isEmpty(name)) {
        Timber.i("Connect to:  " + name);
        connectButton.setText("Cancel");

        connection = connectionManager
                .connect(new NameScanMatcher(name),
                        DEFAULT_SCAN_TIMEOUT * 20,
                        DEFAULT_CONNECTION_TIMEOUT)
                .retryWhen(errors ->
                        errors.flatMap(
                            error -> {
                              if (error instanceof ConnectionError) {
                                return Observable.just(Irrelevant.INSTANCE);
                              }

                              return Observable.error(error);
                            }))
                .subscribe(
                        peripheral -> {
                          peripheralManager.setPeripheral(peripheral);

                          AndroidSchedulers.mainThread().scheduleDirect(() -> connectButton.setText("Disconnect"));
                          Timber.i("Connected to: " + name);
                          Timber.i("Max Write Length (MTU): " + peripheral.getMaxWriteLength());
                        },
                        error -> {
                          connection.dispose();
                          connection = null;

                          AndroidSchedulers.mainThread().scheduleDirect(() -> connectButton.setText("Connect"));
                          Timber.i("Connection error: " + error.getMessage());
                        }
                );

      }
    } else {
      connection.dispose();
      connection = null;

      connectButton.setText("Connect");
      Timber.i("Disconnected");
    }
  }

  private void connect() {
    if (connection == null) {
      String name = nameEditText.getEditableText().toString();
      if (!TextUtils.isEmpty(name)) {
        Timber.i("Connect to:  " + name);
        connectButton.setText("Cancel");

        ScanMatcher scanMatcher = new NameScanMatcher(name);
        connection = scanner
                .scan()
                .compose(scanMatcher.match())
                .firstOrError()
                .flatMapObservable(scanData ->
                        connectionManager.connect(
                                scanData.getBluetoothDevice(),
                                DEFAULT_CONNECTION_TIMEOUT))
                .retryWhen(errors ->
                        errors.flatMap(
                            error -> {
                              if (error instanceof ConnectionError) {
                                return Observable.just(Irrelevant.INSTANCE);
                              }

                              return Observable.error(error);
                            }))
                .subscribe(
                        peripheral -> {
                          peripheralManager.setPeripheral(peripheral);

                          AndroidSchedulers.mainThread().scheduleDirect(() -> connectButton.setText("Disconnect"));
                          Timber.i("Connected to: " + name);
                          Timber.i("Max Write Length (MTU): " + peripheral.getMaxWriteLength());
                        },
                        error -> {
                          connection.dispose();
                          connection = null;

                          AndroidSchedulers.mainThread().scheduleDirect(() -> connectButton.setText("Connect"));
                          Timber.i("Connection error: " + error.getMessage());
                        }
                );

      }
    } else {
      connection.dispose();
      connection = null;

      connectButton.setText("Connect");
      Timber.i("Disconnected");
    }
  }
}
