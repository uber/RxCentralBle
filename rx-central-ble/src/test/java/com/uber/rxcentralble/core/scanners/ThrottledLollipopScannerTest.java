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
package com.uber.rxcentralble.core.scanners;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;

import com.uber.rxcentralble.ConnectionError;
import com.uber.rxcentralble.ParsedAdvertisement;
import com.uber.rxcentralble.ScanData;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.TimeUnit;

import io.reactivex.observers.TestObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.TestScheduler;

import static com.uber.rxcentralble.core.scanners.ThrottledLollipopScanner.ANDROID_7_MAX_SCAN_DURATION_MS;
import static com.uber.rxcentralble.core.scanners.ThrottledLollipopScanner.SCAN_WINDOW_MS;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(RobolectricTestRunner.class)
@PowerMockIgnore({"org.powermock.*", "org.mockito.*", "org.robolectric.*", "android.*"})
@PrepareForTest({BluetoothAdapter.class})
public class ThrottledLollipopScannerTest {

  @Rule
  public PowerMockRule rule = new PowerMockRule();

  @Mock ParsedAdvertisement.Factory adDataFactory;
  @Mock ParsedAdvertisement parsedAdvertisement;

  @Mock BluetoothAdapter bluetoothAdapter;
  @Mock BluetoothLeScanner bluetoothLeScanner;
  @Mock BluetoothDevice bluetoothDevice;
  @Mock ScanResult scanResult;
  @Mock ScanRecord scanRecord;

  private final TestScheduler testScheduler = new TestScheduler();

  private ThrottledLollipopScanner scanner;
  private TestObserver<ScanData> scanDataTestObserver;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    RxJavaPlugins.setComputationSchedulerHandler(schedulerCallable -> testScheduler);

    mockStatic(BluetoothAdapter.class);

    when(bluetoothAdapter.getBluetoothLeScanner()).thenReturn(bluetoothLeScanner);
    when(adDataFactory.produce(any())).thenReturn(parsedAdvertisement);
    when(scanResult.getDevice()).thenReturn(bluetoothDevice);
    when(scanResult.getScanRecord()).thenReturn(scanRecord);
    when(scanRecord.getBytes()).thenReturn(new byte[] {0x00});
    when(scanResult.getRssi()).thenReturn(0);

    scanner = new ThrottledLollipopScanner();
  }

  @Test
  public void scan_failed_bluetoothUnsupported() {
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(null);

    scanDataTestObserver = scanner.scan().test();

    testScheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS);

    scanDataTestObserver.assertError(
        throwable -> {
          ConnectionError error = (ConnectionError) throwable;
          return error != null && error.getCode() == ConnectionError.Code.SCAN_FAILED;
        });
  }

  @Test
  public void scan_failed_bluetoothOff() {
    when(bluetoothAdapter.isEnabled()).thenReturn(false);
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(bluetoothAdapter);

    scanDataTestObserver = scanner.scan().test();

    testScheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS);

    scanDataTestObserver.assertError(
        throwable -> {
          ConnectionError error = (ConnectionError) throwable;
          return error != null && error.getCode() == ConnectionError.Code.SCAN_FAILED;
        });
  }

  @Test
  public void scan_shared_inProgress() {
    when(bluetoothAdapter.isEnabled()).thenReturn(true);
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(bluetoothAdapter);

    scanner.scan().test();

    testScheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS);

    scanDataTestObserver = scanner.scan().test();

    testScheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS);

    scanDataTestObserver.assertNoErrors();
    scanDataTestObserver.hasSubscription();
  }

  @Test
  public void scan_failed_onCallback() {
    when(bluetoothAdapter.isEnabled()).thenReturn(true);
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(bluetoothAdapter);

    scanDataTestObserver = scanner.scan().test();

    testScheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS);

    ArgumentCaptor<ScanCallback> argument = ArgumentCaptor.forClass(ScanCallback.class);
    verify(bluetoothLeScanner).startScan(any(), any(), argument.capture());

    argument.getValue().onScanFailed(0);

    scanDataTestObserver.assertError(
        throwable -> {
          ConnectionError error = (ConnectionError) throwable;
          return error != null && error.getCode() == ConnectionError.Code.SCAN_FAILED;
        });
  }

  @Test
  public void scan_withMatches() {
    when(bluetoothAdapter.isEnabled()).thenReturn(true);
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(bluetoothAdapter);

    scanDataTestObserver = scanner.scan().test();

    testScheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS);

    ArgumentCaptor<ScanCallback> argument = ArgumentCaptor.forClass(ScanCallback.class);
    verify(bluetoothLeScanner).startScan(any(), any(), argument.capture());

    argument.getValue().onScanResult(0, scanResult);

    scanDataTestObserver.assertValueCount(1);
  }

  @Test
  public void scan_duration_throttling() {
    when(bluetoothAdapter.isEnabled()).thenReturn(true);
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(bluetoothAdapter);

    scanDataTestObserver = scanner.scan().test();

    testScheduler.advanceTimeBy(ANDROID_7_MAX_SCAN_DURATION_MS * 3, TimeUnit.MILLISECONDS);

    verify(bluetoothLeScanner, times(3))
            .startScan(any(), any(), any(ScanCallback.class));

    verify(bluetoothLeScanner, times(2)).stopScan(any(ScanCallback.class));
  }

  @Test
  public void scan_start_throttling() {
    when(bluetoothAdapter.isEnabled()).thenReturn(true);
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(bluetoothAdapter);

    scanDataTestObserver = scanner.scan().test();
    testScheduler.advanceTimeBy(SCAN_WINDOW_MS / 5, TimeUnit.MILLISECONDS);
    scanDataTestObserver.dispose();

    scanDataTestObserver = scanner.scan().test();
    testScheduler.advanceTimeBy(SCAN_WINDOW_MS / 5, TimeUnit.MILLISECONDS);
    scanDataTestObserver.dispose();

    scanDataTestObserver = scanner.scan().test();
    testScheduler.advanceTimeBy(SCAN_WINDOW_MS / 5, TimeUnit.MILLISECONDS);
    scanDataTestObserver.dispose();

    scanDataTestObserver = scanner.scan().test();
    testScheduler.advanceTimeBy(SCAN_WINDOW_MS / 5, TimeUnit.MILLISECONDS);
    scanDataTestObserver.dispose();

    scanDataTestObserver = scanner.scan().test();
    testScheduler.advanceTimeBy(SCAN_WINDOW_MS / 5, TimeUnit.MILLISECONDS);

    verify(bluetoothLeScanner, times(4))
            .startScan(any(), any(), any(ScanCallback.class));

    testScheduler.advanceTimeBy(SCAN_WINDOW_MS, TimeUnit.MILLISECONDS);

    verify(bluetoothLeScanner, times(5))
            .startScan(any(), any(), any(ScanCallback.class));
  }

  @Test
  public void scan_latency_throttling() {
    when(bluetoothAdapter.isEnabled()).thenReturn(true);
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(bluetoothAdapter);

    scanDataTestObserver = scanner.scan(ScanSettings.SCAN_MODE_BALANCED).test();

    testScheduler.advanceTimeBy(SCAN_WINDOW_MS / 5, TimeUnit.MILLISECONDS);

    // Each time latency changes, we stop / start scanning.
    TestObserver<ScanData> fastScanMode = scanner.scan(ScanSettings.SCAN_MODE_LOW_LATENCY).test();
    testScheduler.advanceTimeBy(SCAN_WINDOW_MS / 5, TimeUnit.MILLISECONDS);
    // Disposing this subscription will result in stop / start scanning back to BALANCED scan mode.
    fastScanMode.dispose();

    testScheduler.advanceTimeBy(SCAN_WINDOW_MS / 5, TimeUnit.MILLISECONDS);

    fastScanMode = scanner.scan(ScanSettings.SCAN_MODE_LOW_LATENCY).test();
    testScheduler.advanceTimeBy(SCAN_WINDOW_MS / 5, TimeUnit.MILLISECONDS);
    fastScanMode.dispose();

    verify(bluetoothLeScanner, times(2))
            .startScan(any(), argThat(argument -> {
              ScanSettings scanSettings = (ScanSettings) argument;
              if (scanSettings == null) {
                return false;
              }

              return scanSettings.getScanMode() == ScanSettings.SCAN_MODE_BALANCED;
            }), any(ScanCallback.class));

    verify(bluetoothLeScanner, times(2))
            .startScan(any(), argThat(argument -> {
              ScanSettings scanSettings = (ScanSettings) argument;
              if (scanSettings == null) {
                return false;
              }

              return scanSettings.getScanMode() == ScanSettings.SCAN_MODE_LOW_LATENCY;
            }), any(ScanCallback.class));

    testScheduler.advanceTimeBy(SCAN_WINDOW_MS, TimeUnit.MILLISECONDS);

    // After a further delay, we should be returned to scanning balanced mode.
    verify(bluetoothLeScanner, times(3))
            .startScan(any(), argThat(argument -> {
              ScanSettings scanSettings = (ScanSettings) argument;
              if (scanSettings == null) {
                return false;
              }

              return scanSettings.getScanMode() == ScanSettings.SCAN_MODE_BALANCED;
            }), any(ScanCallback.class));

    verify(bluetoothLeScanner, times(2))
            .startScan(any(), argThat(argument -> {
              ScanSettings scanSettings = (ScanSettings) argument;
              if (scanSettings == null) {
                return false;
              }

              return scanSettings.getScanMode() == ScanSettings.SCAN_MODE_LOW_LATENCY;
            }), any(ScanCallback.class));
  }
}
