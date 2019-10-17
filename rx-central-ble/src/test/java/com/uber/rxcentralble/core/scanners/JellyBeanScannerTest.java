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
import io.reactivex.schedulers.TestScheduler;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(RobolectricTestRunner.class)
@PowerMockIgnore({"org.powermock.*", "org.mockito.*", "org.robolectric.*", "android.*"})
@PrepareForTest({BluetoothAdapter.class})
public class JellyBeanScannerTest {

  @Rule
  public PowerMockRule rule = new PowerMockRule();

  @Mock ParsedAdvertisement.Factory adDataFactory;
  @Mock ParsedAdvertisement parsedAdvertisement;
  @Mock BluetoothAdapter bluetoothAdapter;
  @Mock BluetoothDevice bluetoothDevice;

  JellyBeanScanner scanner;
  TestObserver<ScanData> scanDataTestObserver;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    mockStatic(BluetoothAdapter.class);

    when(bluetoothAdapter.startLeScan(any())).thenReturn(true);
    when(adDataFactory.produce(any())).thenReturn(parsedAdvertisement);

    scanner = new JellyBeanScanner(adDataFactory);
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
  public void scan_share_inProgress() {
    when(bluetoothAdapter.isEnabled()).thenReturn(true);
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(bluetoothAdapter);

    scanner.scan().test();
    scanDataTestObserver = scanner.scan().test();

    scanDataTestObserver.assertNoErrors();
    scanDataTestObserver.hasSubscription();
  }

  @Test
  public void scan_withMatches() {
    when(bluetoothAdapter.isEnabled()).thenReturn(true);
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(bluetoothAdapter);

    scanDataTestObserver = scanner.scan().test();

    ArgumentCaptor<BluetoothAdapter.LeScanCallback> argument =
        ArgumentCaptor.forClass(BluetoothAdapter.LeScanCallback.class);
    verify(bluetoothAdapter).startLeScan(argument.capture());

    argument.getValue().onLeScan(bluetoothDevice, 0, new byte[] {0x00});

    scanDataTestObserver.assertValueCount(1);
  }
}
