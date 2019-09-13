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

import io.reactivex.observers.TestObserver;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(RobolectricTestRunner.class)
@PowerMockIgnore({"org.powermock.*", "org.mockito.*", "org.robolectric.*", "android.*"})
@PrepareForTest({BluetoothAdapter.class})
public class LollipopScannerTest {

  @Rule
  public PowerMockRule rule = new PowerMockRule();

  @Mock ParsedAdvertisement.Factory adDataFactory;
  @Mock ParsedAdvertisement parsedAdvertisement;

  @Mock BluetoothAdapter bluetoothAdapter;
  @Mock BluetoothLeScanner bluetoothLeScanner;
  @Mock BluetoothDevice bluetoothDevice;
  @Mock ScanResult scanResult;
  @Mock ScanRecord scanRecord;

  LollipopScanner scanner;
  TestObserver<ScanData> scanDataTestObserver;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    mockStatic(BluetoothAdapter.class);

    when(bluetoothAdapter.getBluetoothLeScanner()).thenReturn(bluetoothLeScanner);
    when(adDataFactory.produce(any())).thenReturn(parsedAdvertisement);
    when(scanResult.getDevice()).thenReturn(bluetoothDevice);
    when(scanResult.getScanRecord()).thenReturn(scanRecord);
    when(scanRecord.getBytes()).thenReturn(new byte[] {0x00});
    when(scanResult.getRssi()).thenReturn(0);

    scanner = new LollipopScanner(adDataFactory);
  }

  @Test
  public void scan_failed_bluetoothUnsupported() {
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(null);

    scanDataTestObserver = scanner.scan().test();

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

    scanDataTestObserver.assertError(
        throwable -> {
          ConnectionError error = (ConnectionError) throwable;
          return error != null && error.getCode() == ConnectionError.Code.SCAN_FAILED;
        });
  }

  @Test
  public void scan_failed_inProgress() {
    when(bluetoothAdapter.isEnabled()).thenReturn(true);
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(bluetoothAdapter);

    scanner.scan().test();
    scanDataTestObserver = scanner.scan().test();

    scanDataTestObserver.assertError(
        throwable -> {
          ConnectionError error = (ConnectionError) throwable;
          return error != null && error.getCode() == ConnectionError.Code.SCAN_IN_PROGRESS;
        });
  }

  @Test
  public void scan_failed_onCallback() {
    when(bluetoothAdapter.isEnabled()).thenReturn(true);
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(bluetoothAdapter);

    scanDataTestObserver = scanner.scan().test();

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

    ArgumentCaptor<ScanCallback> argument = ArgumentCaptor.forClass(ScanCallback.class);
    verify(bluetoothLeScanner).startScan(any(), any(), argument.capture());

    argument.getValue().onScanResult(0, scanResult);

    scanDataTestObserver.assertValueCount(1);
  }
}
