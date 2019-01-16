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

import android.content.Context;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.uber.rxcentralble.BluetoothDetector;
import com.uber.rxcentralble.ConnectionError;
import com.uber.rxcentralble.ConnectionManager;
import com.uber.rxcentralble.GattIO;
import com.uber.rxcentralble.ParsedAdvertisement;
import com.uber.rxcentralble.ScanData;
import com.uber.rxcentralble.ScanMatcher;
import com.uber.rxcentralble.Scanner;
import com.uber.rxcentralble.core.scanners.JellyBeanScanner;
import com.uber.rxcentralble.core.scanners.LollipopScanner;

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
  private Observable<GattIO> sharedGattIOObservable;

  private int scanTimeoutMs = DEFAULT_SCAN_TIMEOUT;
  private int connectionTimeoutMs = DEFAULT_CONNECTION_TIMEOUT;

  public CoreConnectionManager(
          Context context,
          BluetoothDetector bluetoothDetector,
          GattIO.Factory gattIOFactory) {

    ParsedAdvertisement.Factory factory = new CoreParsedAdvertisement.Factory();
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      this.scanner = new JellyBeanScanner(factory);
    } else {
      this.scanner = new LollipopScanner(factory);
    }

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
    this.sharedGattIOObservable = createdSharedObservable(context, bluetoothDetector, scanner,
            scanMatcher, gattIOFactory);

    return sharedGattIOObservable;
  }

  @Override
  public Observable<State> state() {
    return stateRelay;
  }

  private Observable<GattIO> createdSharedObservable(Context context,
                    BluetoothDetector bluetoothDetector,
                    Scanner scanner,
                    ScanMatcher scanMatcher,
                    GattIO.Factory gattIOFactory) {

    return bluetoothDetector
                .enabled()
                .distinctUntilChanged()
                .filter(enabled -> enabled)
                .compose(scan(scanner, scanMatcher))
                .compose(connectGatt(gattIOFactory, context))
                .doOnNext(connectableGattIO -> stateRelay.accept(State.CONNECTED))
                .doOnDispose(() -> stateRelay.accept(State.DISCONNECTED))
                .doOnError(error -> stateRelay.accept(State.DISCONNECTED_WITH_ERROR))
                .doFinally(() -> this.scanMatcher = null)
                .map(connectableGattIO -> connectableGattIO)
                .replay(1)
                .refCount();
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

  private ObservableTransformer<ScanData, GattIO> connectGatt(
      GattIO.Factory gattIOFactory, Context context) {
    return scanDataObservable ->
        scanDataObservable
            .doOnNext(sd -> stateRelay.accept(State.CONNECTING))
            .flatMap(
                scanData -> {
                  GattIO gattIO =
                      gattIOFactory.produce(scanData.getBluetoothDevice(), context);

                  Observable<Pair<GattIO.ConnectableState, GattIO>>
                      gattIoConnection =
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

                  return Observable.combineLatest(
                      gattIoConnection,
                      gattIoConnectionTimeout,
                      (connection, timeout) -> connection);
                })
            .filter(
                stateGattPair ->
                    stateGattPair.first == GattIO.ConnectableState.CONNECTED)
            .map(statePair -> statePair.second);
  }
}
