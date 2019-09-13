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
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.uber.rxcentralble.BluetoothDetector;
import com.uber.rxcentralble.ConnectionError;
import com.uber.rxcentralble.ConnectionManager;
import com.uber.rxcentralble.GattIO;
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
  private final GattIO.Factory gattIOFactory;

  @Nullable
  private ScanMatcher scanMatcher;
  @Nullable
  protected Observable<GattIO> sharedGattIOObservable;

  private int scanTimeoutMs = DEFAULT_SCAN_TIMEOUT;
  private int connectionTimeoutMs = DEFAULT_CONNECTION_TIMEOUT;

  public CoreConnectionManager(Context context) {
    this(context, new CoreBluetoothDetector(context));
  }

  public CoreConnectionManager(Context context, BluetoothDetector bluetoothDetector) {
    this(context, bluetoothDetector, new CoreGattIO.Factory());
  }

  public CoreConnectionManager(Context context, BluetoothDetector bluetoothDetector, GattIO.Factory gattIOFactory) {
    this(context, bluetoothDetector, gattIOFactory, new CoreScannerFactory());
  }

  public CoreConnectionManager(
          Context context,
          BluetoothDetector bluetoothDetector,
          GattIO.Factory gattIOFactory,
          Scanner.Factory scannerFactory) {
    this.scanner = scannerFactory.produce();
    this.context = context;
    this.bluetoothDetector = bluetoothDetector;
    this.gattIOFactory = gattIOFactory;
  }

  public CoreConnectionManager(
          Context context,
          BluetoothDetector bluetoothDetector,
          Scanner scanner,
          GattIO.Factory gattIOFactory) {

    this.context = context;
    this.bluetoothDetector = bluetoothDetector;
    this.gattIOFactory = gattIOFactory;
    this.scanner = scanner;
  }

  @Override
  public Observable<GattIO> connect(
          ScanMatcher scanMatcher, int scanTimeoutMs, int connectionTimeoutMs) {
    if (this.scanMatcher != null && !this.scanMatcher.equals(scanMatcher)) {
      return Observable.error(new ConnectionError(CONNECTION_IN_PROGRESS));
    } else if (sharedGattIOObservable != null) {
      return sharedGattIOObservable;
    }

    this.scanTimeoutMs = scanTimeoutMs;
    this.connectionTimeoutMs = connectionTimeoutMs;
    this.scanMatcher = scanMatcher;
    this.sharedGattIOObservable = bluetoothDetector
            .enabled()
            .distinctUntilChanged()
            .filter(enabled -> enabled)
            .compose(scan(scanner, scanMatcher))
            .switchMap(scanData -> connect(scanData.getBluetoothDevice()))
            .compose(shareConnection());

    return sharedGattIOObservable;
  }

  @Override
  public Observable<GattIO> connect(BluetoothDevice bluetoothDevice, int connectionTimeoutMs) {
    if (sharedGattIOObservable != null) {
      return sharedGattIOObservable;
    }

    this.connectionTimeoutMs = connectionTimeoutMs;
    this.sharedGattIOObservable = bluetoothDetector
            .enabled()
            .distinctUntilChanged()
            .filter(enabled -> enabled)
            .switchMap(enabled -> connect(bluetoothDevice))
            .compose(shareConnection());

    return sharedGattIOObservable;
  }

  @Override
  public Observable<State> state() {
    return stateRelay;
  }

  private ObservableTransformer<Boolean, ScanData> scan(Scanner scanner, ScanMatcher scanMatcher) {
    return bluetoothEnabled ->
            bluetoothEnabled
                    .doOnNext(connectableGattIO -> stateRelay.accept(State.SCANNING))
                    .switchMap(enabled -> scanner.scan())
                    .compose(scanMatcher.match())
                    .firstOrError()
                    .timeout(
                            scanTimeoutMs,
                            TimeUnit.MILLISECONDS,
                            Single.error(new ConnectionError(SCAN_TIMEOUT)))
                    .toObservable();
  }

  private Observable<GattIO> connect(BluetoothDevice bluetoothDevice) {
    stateRelay.accept(State.CONNECTING);

    GattIO gattIO = gattIOFactory.produce(bluetoothDevice, context);

    Observable<Pair<GattIO.ConnectableState, GattIO>> gattIoConnection =
            gattIO
                    .connect()
                    .withLatestFrom(Observable.just(gattIO), Pair::new);

    Observable<GattIO.ConnectableState> gattIoConnectionTimeout =
            gattIO
                    .connect()
                    .filter(s -> s == GattIO.ConnectableState.CONNECTED)
                    .firstOrError()
                    .timeout(
                            connectionTimeoutMs,
                            TimeUnit.MILLISECONDS,
                            Single.error(new ConnectionError(CONNECT_TIMEOUT)))
                    .toObservable();

    return Observable.combineLatest(gattIoConnection, gattIoConnectionTimeout,
        (connection, timeout) -> connection)
            .filter(stateGattPair -> stateGattPair.first == GattIO.ConnectableState.CONNECTED)
            .map(statePair -> statePair.second);
  }

  private ObservableTransformer<GattIO, GattIO> shareConnection() {
    return gattIO -> gattIO
            .doOnNext(connectableGattIO -> stateRelay.accept(State.CONNECTED))
            .doOnDispose(() -> stateRelay.accept(State.DISCONNECTED))
            .doOnError(error -> stateRelay.accept(State.DISCONNECTED_WITH_ERROR))
            .doFinally(() -> {
              this.sharedGattIOObservable = null;
              this.scanMatcher = null;
            })
            .map(connectableGattIO -> connectableGattIO)
            .replay(1)
            .refCount();
  }
}
