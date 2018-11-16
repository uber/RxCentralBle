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
package com.uber.rx_central_ble.core.scanners;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;

import com.uber.rx_central_ble.AdvertisingData;
import com.uber.rx_central_ble.ConnectionError;
import com.uber.rx_central_ble.ScanMatcher;
import com.uber.rx_central_ble.Scanner;

import java.util.ArrayList;
import java.util.List;

import android.support.annotation.Nullable;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

import static com.uber.rx_central_ble.ConnectionError.Code.SCAN_FAILED;
import static com.uber.rx_central_ble.ConnectionError.Code.SCAN_IN_PROGRESS;

/** Core Scanner implementation for API >= 21 (i.e. Lollipop). */
@TargetApi(21)
public class LollipopScanner implements Scanner {

  private final AdvertisingData.Factory scanDataFactory;
  private final ScanCallback scanCallback;

  @Nullable private PublishSubject<AdvertisingData> scanDataSubject;
  @Nullable private ScanMatcher scanMatcher;

  public LollipopScanner(AdvertisingData.Factory scanDataFactory) {
    this.scanDataFactory = scanDataFactory;
    this.scanCallback = getScanCallback();
  }

  @Override
  public Observable<AdvertisingData> scan(ScanMatcher scanMatcher) {
    if (scanDataSubject != null) {
      return Observable.error(new ConnectionError(SCAN_IN_PROGRESS));
    }

    this.scanMatcher = scanMatcher;
    this.scanDataSubject = PublishSubject.create();

    return scanDataSubject
        .doOnSubscribe(
            d -> {
              if (scanDataSubject != null) {
                startScan(scanDataSubject);
              }
            })
        .doFinally(this::stopScan)
        .share();
  }

  private void startScan(PublishSubject scanDataSubject) {
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter != null && adapter.isEnabled()) {
      // Setting service uuid scan filter failing on Galaxy S6 on Android 7.
      // Manually filter on scan operation.
      List<ScanFilter> filters = new ArrayList<>();

      ScanSettings.Builder settingsBuilder = new ScanSettings.Builder();
      settingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);

      BluetoothLeScanner bleScanner = adapter.getBluetoothLeScanner();
      if (bleScanner != null) {
        bleScanner.startScan(filters, settingsBuilder.build(), scanCallback);
      } else {
        scanDataSubject.onError(new ConnectionError(SCAN_FAILED));
      }
    } else {
      scanDataSubject.onError(new ConnectionError(SCAN_FAILED));
    }
  }

  private void stopScan() {
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter != null) {
      BluetoothLeScanner bleScanner = adapter.getBluetoothLeScanner();
      if (bleScanner != null) {
        bleScanner.stopScan(scanCallback);
      }
    }

    scanDataSubject = null;
  }

  private ScanCallback getScanCallback() {
    return new ScanCallback() {

      @Override
      public void onScanResult(int callbackType, ScanResult scanResult) {
        handleScanData(
            scanDataFactory.produce(
                scanResult.getDevice(),
                scanResult.getScanRecord().getBytes(),
                scanResult.getRssi()));
      }

      @Override
      public void onScanFailed(int errorCode) {
        if (scanDataSubject != null) {
          scanDataSubject.onError(new ConnectionError(SCAN_FAILED));
        }
      }

      @Override
      public void onBatchScanResults(List<ScanResult> results) {
        for (ScanResult scanResult : results) {
          handleScanData(
              scanDataFactory.produce(
                  scanResult.getDevice(),
                  scanResult.getScanRecord().getBytes(),
                  scanResult.getRssi()));
        }
      }

      private void handleScanData(AdvertisingData advertisingData) {
        if (scanDataSubject != null && scanMatcher != null && scanMatcher.match(advertisingData)) {
          scanDataSubject.onNext(advertisingData);
        }
      }
    };
  }
}
