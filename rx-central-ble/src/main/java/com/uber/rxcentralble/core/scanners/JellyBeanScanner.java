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

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.support.annotation.Nullable;

import com.uber.rxcentralble.ParsedAdvertisement;
import com.uber.rxcentralble.RxCentralLogger;
import com.uber.rxcentralble.ScanData;
import com.uber.rxcentralble.ConnectionError;
import com.uber.rxcentralble.Scanner;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

import static com.uber.rxcentralble.ConnectionError.Code.SCAN_FAILED;
import static com.uber.rxcentralble.ConnectionError.Code.SCAN_IN_PROGRESS;

/** Core Scanner implementation for API < 21 (i.e. JellyBean) */
@TargetApi(18)
public class JellyBeanScanner implements Scanner {

  private final ParsedAdvertisement.Factory parsedAdDataFactory;
  private final BluetoothAdapter.LeScanCallback leScanCallback;

  @Nullable private PublishSubject<ScanData> scanDataSubject;

  public JellyBeanScanner(ParsedAdvertisement.Factory parsedAdDataFactory) {
    this.parsedAdDataFactory = parsedAdDataFactory;
    this.leScanCallback = getScanCallback();
  }

  @Override
  public Observable<ScanData> scan() {
    if (scanDataSubject != null) {
      return Observable.error(new ConnectionError(SCAN_IN_PROGRESS));
    }

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
      if (!adapter.startLeScan(leScanCallback)) {
        scanDataSubject.onError(new ConnectionError(SCAN_FAILED));
      } else if (RxCentralLogger.isError()) {
        RxCentralLogger.error("startLeScan failed.");
      }
    } else {
      if (RxCentralLogger.isError()) {
        if (adapter == null) {
          RxCentralLogger.error("startScan - Default Bluetooth Adapter is null!");
        } else {
          RxCentralLogger.error("startScan - Bluetooth Adapter is disabled.");
        }
      }

      scanDataSubject.onError(new ConnectionError(SCAN_FAILED));
    }
  }

  private void stopScan() {
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter != null && adapter.isEnabled()) {
      adapter.stopLeScan(leScanCallback);
    } else if (RxCentralLogger.isError()) {
      if (adapter == null) {
        RxCentralLogger.error("stopScan - Default Bluetooth Adapter is null!");
      } else {
        RxCentralLogger.error("stopScan - Bluetooth Adapter is disabled.");
      }
    }

    scanDataSubject = null;
  }

  /** Implementation of Android LeScanCallback. */
  private BluetoothAdapter.LeScanCallback getScanCallback() {
    return (bluetoothDevice, rssi, eirData) -> {
      if (RxCentralLogger.isDebug()) {
        RxCentralLogger.debug("onLeScan - BD_ADDR: "  + bluetoothDevice.getAddress()
                + " | RSSI: " + rssi);
      }

      if (scanDataSubject != null) {
        ScanData scanData = new JellyBeanScanData(bluetoothDevice, rssi, parsedAdDataFactory.produce(eirData));
        scanDataSubject.onNext(scanData);
      }
    };
  }
}
