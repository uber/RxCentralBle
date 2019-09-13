package com.uber.rxcentralble.core.scanners;

import android.annotation.TargetApi;
import android.support.annotation.Nullable;

import com.uber.rxcentralble.ScanData;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;

@TargetApi(21)
public class ThrottledLollipopScanner extends LollipopScanner {

  public static final int ANDROID_7_MAX_SCAN_DURATION_MS = 29 * 60 * 1000; // 29 minutes
  public static final int PAUSE_INTERVAL_MS = 10 * 1000; // 10 seconds

  private final int maxScanDurationMs;
  private final int pauseIntervalMs;
  @Nullable private Observable<ScanData> sharedScanData;

  public ThrottledLollipopScanner() {
    this(ANDROID_7_MAX_SCAN_DURATION_MS, PAUSE_INTERVAL_MS);
  }

  public ThrottledLollipopScanner(int maxScanDurationMs, int pauseIntervalMs) {
    super();

    this.maxScanDurationMs = maxScanDurationMs;
    this.pauseIntervalMs = pauseIntervalMs;
  }

  @Override
  public Observable<ScanData> scan() {
    if (sharedScanData != null) {
      return sharedScanData;
    }

    final Observable<ScanData> scopedSharedScanData = Observable.concat(
            // Start throttled scan.
            throttledScan(),
            // Repeat pause followed by throttle scan.
            intervalScan());

    return scopedSharedScanData
            .doOnSubscribe(d -> sharedScanData = scopedSharedScanData)
            .doFinally(() -> sharedScanData = null)
            .share();
  }

  private Observable<ScanData> throttledScan() {
    return super.scan().takeUntil(Observable.timer(maxScanDurationMs, TimeUnit.MILLISECONDS));
  }

  private Observable<ScanData> intervalScan() {
    final int totalInterval = maxScanDurationMs + pauseIntervalMs;
    return Observable.interval(0, totalInterval, TimeUnit.MILLISECONDS)
            // paused
            .switchMap(t1 -> Observable.timer(pauseIntervalMs, TimeUnit.MILLISECONDS))
            // scanning
            .switchMap(t2 -> throttledScan());
  }
}
