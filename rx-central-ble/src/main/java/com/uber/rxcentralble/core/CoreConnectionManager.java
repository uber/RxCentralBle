/**
 *  Copyright (c) 2018 Uber Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.uber.rxcentralble.core;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.uber.rxcentralble.BluetoothDetector;
import com.uber.rxcentralble.ConnectionError;
import com.uber.rxcentralble.ConnectionManager;
import com.uber.rxcentralble.Peripheral;
import com.uber.rxcentralble.ScanData;
import com.uber.rxcentralble.ScanMatcher;
import com.uber.rxcentralble.Scanner;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import io.reactivex.Single;

import static com.uber.rxcentralble.ConnectionError.Code.CONNECTION_IN_PROGRESS;
import static com.uber.rxcentralble.ConnectionError.Code.CONNECT_TIMEOUT;
import static com.uber.rxcentralble.ConnectionError.Code.SCAN_TIMEOUT;

/** Core implementation of ConnectionManager. */
public class CoreConnectionManager implements ConnectionManager {

  private final BehaviorRelay<State> stateRelay = BehaviorRelay.createDefault(State.DISCONNECTED);
  private final Context context;
  private final BluetoothDetector bluetoothDetector;
  private final Scanner scanner;
  private final Peripheral.Factory peripheralFactory;

  @Nullable
  private ScanMatcher scanMatcher;
  @Nullable
  protected Observable<Peripheral> sharedPeripheralObservable;

  private int scanTimeoutMs = DEFAULT_SCAN_TIMEOUT;
  private int connectionTimeoutMs = DEFAULT_CONNECTION_TIMEOUT;

  public CoreConnectionManager(Context context) {
    this(context, new CoreBluetoothDetector(context));
  }

  public CoreConnectionManager(Context context, BluetoothDetector bluetoothDetector) {
    this(context, bluetoothDetector, new CorePeripheral.Factory());
  }

  public CoreConnectionManager(Context context,
                               BluetoothDetector bluetoothDetector,
                               Peripheral.Factory peripheralFactory) {
    this(context, bluetoothDetector, peripheralFactory, new CoreScannerFactory());
  }

  public CoreConnectionManager(
          Context context,
          BluetoothDetector bluetoothDetector,
          Peripheral.Factory peripheralFactory,
          Scanner.Factory scannerFactory) {
    this.scanner = scannerFactory.produce();
    this.context = context;
    this.bluetoothDetector = bluetoothDetector;
    this.peripheralFactory = peripheralFactory;
  }

  public CoreConnectionManager(
          Context context,
          BluetoothDetector bluetoothDetector,
          Scanner scanner,
          Peripheral.Factory peripheralFactory) {

    this.context = context;
    this.bluetoothDetector = bluetoothDetector;
    this.peripheralFactory = peripheralFactory;
    this.scanner = scanner;
  }

  @Override
  public Observable<Peripheral> connect(
          ScanMatcher scanMatcher, int scanTimeoutMs, int connectionTimeoutMs) {
    if (this.scanMatcher != null && !this.scanMatcher.equals(scanMatcher)) {
      return Observable.error(new ConnectionError(CONNECTION_IN_PROGRESS));
    } else if (sharedPeripheralObservable != null) {
      return sharedPeripheralObservable;
    }

    this.scanTimeoutMs = scanTimeoutMs;
    this.connectionTimeoutMs = connectionTimeoutMs;
    this.scanMatcher = scanMatcher;
    this.sharedPeripheralObservable = bluetoothDetector
            .enabled()
            .distinctUntilChanged()
            .filter(enabled -> enabled)
            .compose(scan(scanner, scanMatcher))
            .switchMap(scanData -> connect(scanData.getBluetoothDevice()))
            .compose(shareConnection());

    return sharedPeripheralObservable;
  }

  @Override
  public Observable<Peripheral> connect(BluetoothDevice bluetoothDevice, int connectionTimeoutMs) {
    if (sharedPeripheralObservable != null) {
      return sharedPeripheralObservable;
    }

    this.connectionTimeoutMs = connectionTimeoutMs;
    this.sharedPeripheralObservable = bluetoothDetector
            .enabled()
            .distinctUntilChanged()
            .filter(enabled -> enabled)
            .switchMap(enabled -> connect(bluetoothDevice))
            .compose(shareConnection());

    return sharedPeripheralObservable;
  }

  @Override
  public Observable<State> state() {
    return stateRelay;
  }

  private ObservableTransformer<Boolean, ScanData> scan(Scanner scanner, ScanMatcher scanMatcher) {
    return bluetoothEnabled ->
            bluetoothEnabled
                    .doOnNext(enabled -> stateRelay.accept(State.SCANNING))
                    .switchMap(enabled -> scanner.scan())
                    .compose(scanMatcher.match())
                    .firstOrError()
                    .timeout(
                            scanTimeoutMs,
                            TimeUnit.MILLISECONDS,
                            Single.error(new ConnectionError(SCAN_TIMEOUT)))
                    .toObservable();
  }

  private Observable<Peripheral> connect(BluetoothDevice bluetoothDevice) {
    stateRelay.accept(State.CONNECTING);

    Peripheral peripheral = peripheralFactory.produce(bluetoothDevice, context);

    Observable<Pair<Peripheral.ConnectableState, Peripheral>> peripheralConnection =
            peripheral
                    .connect()
                    .withLatestFrom(Observable.just(peripheral), Pair::new);

    Observable<Peripheral.ConnectableState> peripheralConnectionTimeout =
            peripheral
                    .connect()
                    .filter(s -> s == Peripheral.ConnectableState.CONNECTED)
                    .firstOrError()
                    .timeout(
                            connectionTimeoutMs,
                            TimeUnit.MILLISECONDS,
                            Single.error(new ConnectionError(CONNECT_TIMEOUT)))
                    .toObservable();

    return Observable.combineLatest(peripheralConnection, peripheralConnectionTimeout,
        (connection, timeout) -> connection)
            .filter(statePeripheraltPair -> statePeripheraltPair.first == Peripheral.ConnectableState.CONNECTED)
            .map(statePeripheraltPair -> statePeripheraltPair.second);
  }

  private ObservableTransformer<Peripheral, Peripheral> shareConnection() {
    return peripheral -> peripheral
            .doOnNext(connectablePeripheral -> stateRelay.accept(State.CONNECTED))
            .doOnDispose(() -> stateRelay.accept(State.DISCONNECTED))
            .doOnError(error -> stateRelay.accept(State.DISCONNECTED_WITH_ERROR))
            .doFinally(() -> {
              this.sharedPeripheralObservable = null;
              this.scanMatcher = null;
            })
            .replay(1)
            .refCount();
  }
}
