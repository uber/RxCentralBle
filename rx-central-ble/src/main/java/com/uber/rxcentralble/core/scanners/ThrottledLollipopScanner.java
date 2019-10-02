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
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.uber.rxcentralble.ParsedAdvertisement;
import com.uber.rxcentralble.RxCentralLogger;
import com.uber.rxcentralble.ScanData;
import com.uber.rxcentralble.ConnectionError;
import com.uber.rxcentralble.Scanner;
import com.uber.rxcentralble.core.CoreParsedAdvertisement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import android.support.annotation.Nullable;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

import static android.bluetooth.le.ScanSettings.SCAN_MODE_OPPORTUNISTIC;
import static com.uber.rxcentralble.ConnectionError.Code.SCAN_FAILED;

/** Core Scanner implementation for API >= 21 (i.e. Lollipop). */
@TargetApi(21)
public class ThrottledLollipopScanner implements Scanner {

  private final ParsedAdvertisement.Factory parsedAdDataFactory;
  private final ScanCallback scanCallback;
  private final Map<Long, Integer> scanModeMap = new HashMap<>();
  private final long maxScanDurationMs;
  private final long pauseIntervalMs;
  private final Queue<Long> scanTimestamps = new ArrayDeque<>(5);
  private final BehaviorRelay<Integer> scanModeRelay = BehaviorRelay.createDefault(SCAN_MODE_OPPORTUNISTIC);

  @Nullable private PublishSubject<ScanData> scanDataSubject;
  @Nullable private Observable<ScanData> sharedScanData;

  public static final long ANDROID_7_MAX_SCAN_DURATION_MS = 29 * 60 * 1000; // 29 minutes
  public static final long PAUSE_INTERVAL_MS = 10 * 1000; // 10 seconds
  public static final long SCAN_WINDOW_MS = 30 * 1000; // 30 seconds

  public ThrottledLollipopScanner() {
    this(new CoreParsedAdvertisement.Factory(), ANDROID_7_MAX_SCAN_DURATION_MS, PAUSE_INTERVAL_MS);
  }

  public ThrottledLollipopScanner(ParsedAdvertisement.Factory parsedAdDataFactory,
                                  long maxScanDurationMs,
                                  long pauseIntervalMs) {
    this.parsedAdDataFactory = parsedAdDataFactory;
    this.scanCallback = getScanCallback();
    this.maxScanDurationMs = maxScanDurationMs;
    this.pauseIntervalMs = pauseIntervalMs;
  }

  @Override
  public Observable<ScanData> scan() {
    return scan(DEFAULT_SCAN_MODE);
  }

  @Override
  public Observable<ScanData> scan(final int scanMode) {
    final long timestamp = System.currentTimeMillis();

    if (sharedScanData == null) {
      this.scanDataSubject = PublishSubject.create();
      // Android 7 disallows cycling scanning more than 5 times in a 30 second window.
      // Work around this by delaying the start of the next scan based on the last time we stopped.
      // Without this workaround, you may see logs in logcat such as
      // "E/BtGatt.GattService: App 'com.your.app' is scanning too frequently"
      this.sharedScanData = scanModeRelay
              .switchMap(nextScanMode -> Observable.fromCallable(this::calculateDelay)
                      .switchMap(delay -> Observable.timer(delay, TimeUnit.MILLISECONDS))
                      .map(proceed -> nextScanMode))
              .distinctUntilChanged()
              .switchMap(nextScanMode -> Observable.concat(
                      // Start a (potentially delayed) throttled scan.
                      throttledScan(scanDataSubject, nextScanMode),
                      // Repeat pause followed by throttle scan.
                      intervalScan(scanDataSubject, nextScanMode)))
              .doFinally(this::cleanup)
              .share();
    }

    return sharedScanData
            .doOnSubscribe(d -> checkForFasterScanMode(timestamp, scanMode))
            .doFinally(() -> checkForSlowerScanMode(timestamp));
  }

  // On Android 7, scans that go longer than 30 minutes are converted to opportunistic scans.
  // Work around this by ensuring a scan operation has a max duration of 29 minutes.
  private Observable<ScanData> throttledScan(Observable<ScanData> scanData, final int scanMode) {
    return scanData
              .takeUntil(Observable.timer(maxScanDurationMs, TimeUnit.MILLISECONDS))
              .doOnSubscribe(d -> startScan(scanMode))
              .doFinally(() -> {
                scanTimestamps.add(System.currentTimeMillis());
                stopScan();
              });
  }

  private Observable<ScanData> intervalScan(Observable<ScanData> scanData, final int scanMode) {
    final long totalInterval = maxScanDurationMs + pauseIntervalMs;
    return Observable.interval(0, totalInterval, TimeUnit.MILLISECONDS)
            // paused
            .switchMap(t1 -> Observable.timer(pauseIntervalMs, TimeUnit.MILLISECONDS))
            // scanning
            .switchMap(t2 -> throttledScan(scanData, scanMode));
  }

  private void startScan(int scanMode) {
    // Don't do anything if nobody can listen.
    if (scanDataSubject == null) {
      return;
    }

    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter != null && adapter.isEnabled()) {
      // Add a dummy filter to avoid Android 8.1+ enforcement of filters during background isScanning.
      List<ScanFilter> filters = new ArrayList<>();
      ScanFilter.Builder scanFilterBuilder = new ScanFilter.Builder();
      filters.add(scanFilterBuilder.build());

      ScanSettings.Builder settingsBuilder = new ScanSettings.Builder();
      settingsBuilder.setScanMode(scanMode);

      BluetoothLeScanner bleScanner = adapter.getBluetoothLeScanner();
      if (bleScanner != null) {
        bleScanner.startScan(filters, settingsBuilder.build(), scanCallback);
      } else {
        if (RxCentralLogger.isError()) {
          RxCentralLogger.error("startScan - BluetoothLeScanner is null!");
        }

        scanDataSubject.onError(new ConnectionError(SCAN_FAILED));
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
      BluetoothLeScanner bleScanner = adapter.getBluetoothLeScanner();
      if (bleScanner != null) {
        bleScanner.stopScan(scanCallback);
      } else if (RxCentralLogger.isError()) {
        RxCentralLogger.error("stopScan - BluetoothLeScanner is null!");
      }
    } else if (RxCentralLogger.isError()) {
      if (adapter == null) {
        RxCentralLogger.error("stopScan - Default Bluetooth Adapter is null!");
      } else {
        RxCentralLogger.error("stopScan - Bluetooth Adapter is disabled.");
      }
    }
  }

  private long calculateDelay() {
    long currentTime = System.currentTimeMillis();

    // Remove all timestamps from the queue that are outside the 30 second scan window.
    while ((scanTimestamps.peek() != null)
            && (currentTime - scanTimestamps.peek() > SCAN_WINDOW_MS)) {
      scanTimestamps.remove();
    }

    // If we have 4 timestamps inside the 30 second scan window, return a delay such that
    // the the next scan would occur outisde that 30 second window.
    if (scanTimestamps.size() == 4) {
      return SCAN_WINDOW_MS - (currentTime -  scanTimestamps.peek());
    } else {
      return 0;
    }
  }

  private void cleanup() {
    sharedScanData = null;
    scanDataSubject = null;
    scanModeRelay.accept(SCAN_MODE_OPPORTUNISTIC);
    scanModeMap.clear();
  }

  private void checkForFasterScanMode(long timestamp, int scanMode) {
    if (scanModeRelay.getValue() < scanMode) {
      scanModeMap.put(timestamp, scanMode);
      scanModeRelay.accept(scanMode);
    }
  }

  private void checkForSlowerScanMode(long timestamp) {
    if (scanModeMap.containsKey(timestamp)) {
      scanModeMap.remove(timestamp);
      int fastestLatency = -1;
      for (int latency : scanModeMap.values()) {
        if (latency > fastestLatency) {
          fastestLatency = latency;
        }
      }

      if (scanModeRelay.getValue() > fastestLatency) {
        scanModeRelay.accept(fastestLatency);
      }
    }
  }

  private ScanCallback getScanCallback() {
    return new ScanCallback() {

      @Override
      public void onScanResult(int callbackType, ScanResult scanResult) {
        if (RxCentralLogger.isDebug()) {
          RxCentralLogger.debug("onScanResult - BD_ADDR: " + scanResult.getDevice().getAddress()
                  + " | RSSI: " + scanResult.getRssi());
        }

        handleScanData(scanResult);
      }

      @Override
      public void onScanFailed(int errorCode) {
        if (RxCentralLogger.isError()) {
          RxCentralLogger.error("onScanFailed - Error Code: "  + errorCode);
        }

        if (scanDataSubject != null) {
          scanDataSubject.onError(new ConnectionError(SCAN_FAILED));
        }
      }

      @Override
      public void onBatchScanResults(List<ScanResult> results) {
        for (ScanResult scanResult : results) {
          if (RxCentralLogger.isDebug()) {
            RxCentralLogger.debug("onBatchScanResults - BD_ADDR: "
                    + scanResult.getDevice().getAddress() + " | RSSI: " + scanResult.getRssi());
          }

          handleScanData(scanResult);
        }
      }

      private void handleScanData(ScanResult scanResult) {
        if (scanDataSubject != null) {
          ParsedAdvertisement parsedAdvertisement = null;
          if (scanResult.getScanRecord() != null) {
            parsedAdvertisement = parsedAdDataFactory.produce(scanResult.getScanRecord().getBytes());
          }

          ScanData scanData = new LollipopScanData(scanResult, parsedAdvertisement);
          scanDataSubject.onNext(scanData);
        }
      }
    };
  }
}
