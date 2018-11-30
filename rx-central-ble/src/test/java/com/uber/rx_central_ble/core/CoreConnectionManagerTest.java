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
package com.uber.rx_central_ble.core;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.uber.rx_central_ble.BluetoothDetector;
import com.uber.rx_central_ble.ConnectionError;
import com.uber.rx_central_ble.ConnectionManager;
import com.uber.rx_central_ble.GattIO;
import com.uber.rx_central_ble.ScanData;
import com.uber.rx_central_ble.ScanMatcher;
import com.uber.rx_central_ble.Scanner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeUnit;

import io.reactivex.observers.TestObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.PublishSubject;

import static com.uber.rx_central_ble.ConnectionManager.DEFAULT_CONNECTION_TIMEOUT;
import static com.uber.rx_central_ble.ConnectionManager.DEFAULT_SCAN_TIMEOUT;
import static com.uber.rx_central_ble.ConnectionManager.State.DISCONNECTED;
import static com.uber.rx_central_ble.ConnectionManager.State.CONNECTED;
import static com.uber.rx_central_ble.ConnectionManager.State.CONNECTING;
import static com.uber.rx_central_ble.ConnectionManager.State.DISCONNECTED_WITH_ERROR;
import static com.uber.rx_central_ble.ConnectionManager.State.SCANNING;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class CoreConnectionManagerTest  {

  @Mock Context context;
  @Mock BluetoothDetector bluetoothDetector;
  @Mock Scanner scanner;
  @Mock GattIO.Factory gattIOFactory;
  @Mock GattIO gattIO;
  @Mock ScanData scanData;
  @Mock BluetoothDevice bluetoothDevice;
  @Mock ScanMatcher scanMatcher2;

  private final TestScheduler testScheduler = new TestScheduler();
  private final BehaviorRelay<Boolean> bluetoothEnabledRelay = BehaviorRelay.createDefault(true);
  private final PublishSubject<ScanData> scanDataPublishSubject = PublishSubject.create();
  private final PublishSubject<GattIO.ConnectableState> connectableStatePublishSubject =
      PublishSubject.create();

  private ScanMatcher scanMatcher;
  private CoreConnectionManager coreConnectionManager;
  private TestObserver<GattIO> connectTestObserver;
  private TestObserver<ConnectionManager.State> stateTestObserver;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    RxJavaPlugins.setComputationSchedulerHandler(schedulerCallable -> testScheduler);

    when(scanner.scan(any())).thenReturn(scanDataPublishSubject.hide());
    when(bluetoothDetector.enabled()).thenReturn(bluetoothEnabledRelay.hide());
    when(gattIOFactory.produce(any(), any())).thenReturn(gattIO);
    when(gattIO.connect()).thenReturn(connectableStatePublishSubject.hide());
    when(scanData.getBluetoothDevice()).thenReturn(bluetoothDevice);

    coreConnectionManager =
        new CoreConnectionManager(
            context, bluetoothDetector, scanner, gattIOFactory);
  }

  @After
  public void after() {
        RxJavaPlugins.reset();
    }
    
  @Test
  public void connect_failed_scanFailed() {
    prepareConnect(true);

    scanDataPublishSubject.onError(new ConnectionError(ConnectionError.Code.SCAN_FAILED));

    stateTestObserver.assertValues(DISCONNECTED, SCANNING, DISCONNECTED_WITH_ERROR);
    connectTestObserver.assertError(
        throwable -> {
          ConnectionError error = (ConnectionError) throwable;
          return error != null && error.getCode() == ConnectionError.Code.SCAN_FAILED;
        });
  }

  @Test
  public void connect_failed_scanTimeout() {
    prepareConnect(true);

    testScheduler.advanceTimeBy(DEFAULT_SCAN_TIMEOUT + 1000, TimeUnit.MILLISECONDS);

    stateTestObserver.assertValues(DISCONNECTED, SCANNING, DISCONNECTED_WITH_ERROR);
    connectTestObserver.assertError(
        throwable -> {
          ConnectionError error = (ConnectionError) throwable;
          return error != null && error.getCode() == ConnectionError.Code.SCAN_TIMEOUT;
        });
  }

  @Test
  public void connect_failed_connectFailed() {
    prepareConnect(true);

    scanDataPublishSubject.onNext(scanData);

    testScheduler.advanceTimeBy(1000, TimeUnit.MILLISECONDS);

    connectableStatePublishSubject.onError(
        new ConnectionError(ConnectionError.Code.CONNECT_FAILED));

    stateTestObserver.assertValues(DISCONNECTED, SCANNING, CONNECTING, DISCONNECTED_WITH_ERROR);
    connectTestObserver.assertError(
        throwable -> {
          ConnectionError error = (ConnectionError) throwable;
          return error != null && error.getCode() == ConnectionError.Code.CONNECT_FAILED;
        });
  }

  @Test
  public void connect_failed_connectTimeout() {
    prepareConnect(true);

    scanDataPublishSubject.onNext(scanData);

    testScheduler.advanceTimeBy(DEFAULT_CONNECTION_TIMEOUT + 1000, TimeUnit.MILLISECONDS);

    stateTestObserver.assertValues(DISCONNECTED, SCANNING, CONNECTING, DISCONNECTED_WITH_ERROR);
    connectTestObserver.assertError(
        throwable -> {
          ConnectionError error = (ConnectionError) throwable;
          return error != null && error.getCode() == ConnectionError.Code.CONNECT_TIMEOUT;
        });
  }

  @Test
  public void connect_failed_connectionInProgress() {
    prepareConnect(false);

    TestObserver<GattIO> secondConnect =
        coreConnectionManager
            .connect(
                scanMatcher2, DEFAULT_SCAN_TIMEOUT, ConnectionManager.DEFAULT_CONNECTION_TIMEOUT)
            .test();

    secondConnect.assertError(
        throwable -> {
          ConnectionError error = (ConnectionError) throwable;
          return error != null && error.getCode() == ConnectionError.Code.CONNECTION_IN_PROGRESS;
        });
  }

  @Test
  public void connect_multicasted_for_equalScanMatcher() {
    prepareConnect(true);

    TestObserver<GattIO> secondConnect =
        coreConnectionManager
            .connect(
                scanMatcher2, DEFAULT_SCAN_TIMEOUT, ConnectionManager.DEFAULT_CONNECTION_TIMEOUT)
            .test();

    secondConnect.assertNoErrors();
  }

  @Test
  public void connect_bluetoothDisabled() {
    bluetoothEnabledRelay.accept(false);

    prepareConnect(true);

    connectTestObserver.assertEmpty();
  }

  @Test
  public void connect_connected() {
    prepareConnect(true);

    scanDataPublishSubject.onNext(scanData);

    testScheduler.advanceTimeBy(1000, TimeUnit.MILLISECONDS);

    connectableStatePublishSubject.onNext(GattIO.ConnectableState.CONNECTED);

    stateTestObserver.assertValues(DISCONNECTED, SCANNING, CONNECTING, CONNECTED);
    connectTestObserver.assertValue(gattIO);
  }

  private void prepareConnect(boolean matcherEquals) {
    scanMatcher =
        new ScanMatcher() {
          @Override
          public boolean match(ScanData scanData) {
            return true;
          }

          @Override
          public boolean equals(Object o) {
            return matcherEquals;
          }

          @Override
          public int hashCode() {
            return 1;
          }
        };

    stateTestObserver = coreConnectionManager.state().test();
    connectTestObserver =
        coreConnectionManager
            .connect(
                scanMatcher, DEFAULT_SCAN_TIMEOUT, ConnectionManager.DEFAULT_CONNECTION_TIMEOUT)
            .test();
  }
}
