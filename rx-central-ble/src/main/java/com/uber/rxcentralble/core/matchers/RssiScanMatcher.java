package com.uber.rxcentralble.core.matchers;

import com.uber.rxcentralble.ScanData;
import com.uber.rxcentralble.ScanMatcher;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.reactivex.ObservableTransformer;

/**
 * ScanMatcher that matches the closest (gauged by RSSI) peripheral advertising a service UUID.
 */
public class RssiScanMatcher implements ScanMatcher {

  public static final int DEFAULT_MATCH_DELAY_MS = 3000;

  private final ServiceScanMatcher serviceScanMatcher;
  private final int matchDelayMs;
  private final Map<String, ScanData> matches = new HashMap<>();
  private final Object syncRoot = new Object();

  public RssiScanMatcher(ServiceScanMatcher serviceScanMatcher) {
    this(serviceScanMatcher, DEFAULT_MATCH_DELAY_MS);
  }

  public RssiScanMatcher(ServiceScanMatcher serviceScanMatcher, int matchDelayMs) {
    this.serviceScanMatcher = serviceScanMatcher;
    this.matchDelayMs = matchDelayMs;
  }

  /**
   * Take a stream of scan data, match by service uuid, wait 1500ms, then check to see if this is
   * the closest (gauged by RSSI) discovered peripheral with that service uuid.
   *
   * @return transformed stream of matches.
   */
  @Override
  public ObservableTransformer<ScanData, ScanData> match() {
    return scanDataStream ->
            scanDataStream
                    .doOnSubscribe(disposable -> matches.clear())
                    .compose(serviceScanMatcher.match())
                    .doOnNext(scanData -> {
                      synchronized (syncRoot) {
                        matches.put(scanData.getBluetoothDevice().getAddress(), scanData);
                      }
                    })
                    .delay(matchDelayMs, TimeUnit.MILLISECONDS)
                    .filter(match -> {
                      synchronized (syncRoot) {
                        for (ScanData other : matches.values()) {
                          // Skip our self.
                          if (other.getBluetoothDevice().getAddress().contentEquals(
                                  match.getBluetoothDevice().getAddress())) {
                            continue;
                          }

                          // If the other match is closer then do not match.
                          // RSSI is measured in dB; negative values, closer to zero indicates
                          // stronger signal.
                          if (other.getRssi() > match.getRssi()) {
                            return false;
                          }
                        }

                        // This is our match.
                        return true;
                      }
                    });
  }

  @Override
  public boolean equals(Object o) {
    if (o != null && o instanceof RssiScanMatcher) {
      RssiScanMatcher other = (RssiScanMatcher) o;
      return other.serviceScanMatcher.equals(this.serviceScanMatcher);
    }

    return false;
  }

  @Override
  public int hashCode() {
    return serviceScanMatcher.hashCode();
  }
}
