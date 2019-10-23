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

import com.jakewharton.rxrelay2.PublishRelay;
import com.uber.rxcentralble.ParsedAdvertisement;
import com.uber.rxcentralble.RxCentralLogger;
import com.uber.rxcentralble.ScanData;
import com.uber.rxcentralble.ConnectionError;
import com.uber.rxcentralble.Scanner;
import com.uber.rxcentralble.core.CoreParsedAdvertisement;

import io.reactivex.Observable;
import io.reactivex.subjects.CompletableSubject;

import static com.uber.rxcentralble.ConnectionError.Code.SCAN_FAILED;

/**
 * Core Scanner implementation for API < 21 (i.e. JellyBean).  This implementation is thread safe.
 */
@TargetApi(18)
public class JellyBeanScanner implements Scanner {

  private final ParsedAdvertisement.Factory parsedAdDataFactory;
  private final BluetoothAdapter.LeScanCallback leScanCallback;
  private final PublishRelay<ScanData> scanDataRelay = PublishRelay.create();
  private final Observable<ScanData> sharedScanData;

  private final Object syncRoot = new Object();

  private CompletableSubject errorSubject;

  public JellyBeanScanner() {
    this(new CoreParsedAdvertisement.Factory());
  }

  public JellyBeanScanner(ParsedAdvertisement.Factory parsedAdDataFactory) {
    this.parsedAdDataFactory = parsedAdDataFactory;
    this.leScanCallback = getScanCallback();

    this.errorSubject = CompletableSubject.create();
    this.sharedScanData = scanDataRelay
            .doOnSubscribe(disposable -> startScan())
            .doFinally(this::stopScan)
            .share();
  }

  @Override
  public Observable<ScanData> scan() {
    return Observable.merge(sharedScanData, getErrorSubject().toObservable());
  }

  /**
   * Scan latency is ignored on JellyBean as it is only supported on Android 5+.
   *
   * @param scanLatency latency setting for scanning operation.
   * @return stream of scan data from discovered peripherals.
   */
  @Override
  public Observable<ScanData> scan(int scanLatency) {
    return scan();
  }

  private void startScan() {
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter != null && adapter.isEnabled()) {
      if (!adapter.startLeScan(leScanCallback)) {
        getErrorSubject().onError(new ConnectionError(SCAN_FAILED));

        if (RxCentralLogger.isError()) {
          RxCentralLogger.error("startLeScan failed.");
        }
      }
    } else {
      if (RxCentralLogger.isError()) {
        if (adapter == null) {
          RxCentralLogger.error("startScan - Default Bluetooth Adapter is null!");
        } else {
          RxCentralLogger.error("startScan - Bluetooth Adapter is disabled.");
        }
      }

      getErrorSubject().onError(new ConnectionError(SCAN_FAILED));
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
  }

  private CompletableSubject getErrorSubject() {
    synchronized (syncRoot) {
      if (errorSubject.hasThrowable()) {
        errorSubject = CompletableSubject.create();
      }

      return errorSubject;
    }
  }

  /** Implementation of Android LeScanCallback. */
  private BluetoothAdapter.LeScanCallback getScanCallback() {
    return (bluetoothDevice, rssi, eirData) -> {
      if (RxCentralLogger.isDebug()) {
        RxCentralLogger.debug("onLeScan - BD_ADDR: "  + bluetoothDevice.getAddress()
                + " | RSSI: " + rssi);
      }

      ScanData scanData = new JellyBeanScanData(bluetoothDevice, rssi, parsedAdDataFactory.produce(eirData));
      scanDataRelay.accept(scanData);
    };
  }
}
