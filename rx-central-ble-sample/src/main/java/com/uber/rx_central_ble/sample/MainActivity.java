package com.uber.rx_central_ble.sample;

import android.annotation.SuppressLint;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.uber.rx_central_ble.ConnectionManager;
import com.uber.rx_central_ble.GattManager;
import com.uber.rx_central_ble.core.operations.Read;
import com.uber.rx_central_ble.core.operations.RegisterNotification;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import timber.log.Timber;

import static com.uber.rx_central_ble.ConnectionManager.DEFAULT_CONNECTION_TIMEOUT;
import static com.uber.rx_central_ble.ConnectionManager.DEFAULT_SCAN_TIMEOUT;

public class MainActivity extends AppCompatActivity {

  private ConnectionManager connectionManager;
  private GattManager gattManager;

  @BindView(R.id.nameEditText)
  EditText nameEditText;

  @BindView(R.id.logTextView)
  TextView logTextView;

  @BindView(R.id.buttonConnect)
  Button connectButton;

  Disposable connection;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    ButterKnife.bind(this);

    connectionManager = ((SampleApplication) getApplication()).getConnectionManager();
    gattManager = ((SampleApplication) getApplication()).getGattManager();

    Timber.plant(new TextViewLoggingTree(logTextView));

    logTextView.setMovementMethod(new ScrollingMovementMethod());
  }

  @OnClick(R.id.buttonConnect)
  public void connectClick() {
    if (connection == null) {
      String name = nameEditText.getEditableText().toString();
      if (!TextUtils.isEmpty(name)) {
        Timber.d("Connect to:  " + name);
        connectButton.setText("Cancel");

        connection = connectionManager
                .connect(new NameScanMatcher(name),
                        DEFAULT_SCAN_TIMEOUT,
                        DEFAULT_CONNECTION_TIMEOUT)
                .subscribe(
                        gattIO -> {
                          gattManager.setGattIO(gattIO);

                          AndroidSchedulers.mainThread().scheduleDirect(() -> connectButton.setText("Disconnect"));
                          Timber.d("Connected to: " + name);
                        },
                        error -> {
                          connection.dispose();
                          connection = null;

                          AndroidSchedulers.mainThread().scheduleDirect(() -> connectButton.setText("Connect"));
                          Timber.d("Connection error: " + error.getMessage());
                        }
                );

      }
    } else {
      connection.dispose();
      connection = null;

      connectButton.setText("Connect");
      Timber.d("Disconnected");
    }
  }

  @SuppressLint("CheckResult")
  @OnClick(R.id.gapButton)
  public void getGap() {
    gattManager
        .queueOperation(new Read(SampleApplication.GAP_SVC_UUID, SampleApplication.GAP_DEVICE_NAME_UUID, 5000))
        .map(bytes -> new String(bytes, "UTF-8"))
        .subscribe(
          name -> Timber.d("Get GAP: success: " + name),
          error -> Timber.d("Get GAP: error: " + error.getMessage()));
  }

  @SuppressLint("CheckResult")
  @OnClick(R.id.batteryButton)
  public void getBattery() {
    gattManager
        .queueOperation(new Read(SampleApplication.BATTERY_SVC_UUID, SampleApplication.BATTERY_LEVEL_UUID, 5000))
        .map(bytes -> bytes.length > 0 ? (int) bytes[0] : -1)
        .subscribe(
          batteryLevel -> Timber.d("Get Battery: success: " + batteryLevel),
          error -> Timber.d("Get Battery: error: " + error.getMessage()));

    gattManager
        .queueOperation(new RegisterNotification(
                SampleApplication.BATTERY_SVC_UUID,
                SampleApplication.BATTERY_LEVEL_UUID,
                5000))
        .flatMapObservable(irrelevant -> gattManager.notification(SampleApplication.BATTERY_LEVEL_UUID))
        .map(bytes -> bytes.length > 0 ? (int) bytes[0] : -1)
        .subscribe(
          batteryLevel -> Timber.d("Notif Battery: success: " + batteryLevel),
          error -> Timber.d("Notif Battery: error: " + error.getMessage()));
  }
}
