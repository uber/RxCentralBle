package com.uber.rxcentralble.sample;

import android.annotation.TargetApi;
import android.os.Build;

import com.jakewharton.rxrelay2.PublishRelay;
import com.uber.rxcentralble.ScanData;
import com.uber.rxcentralble.ScanMatcher;

import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;

public class NameScanMatcher implements ScanMatcher {

  private final String name;

  public NameScanMatcher(String name) {
    this.name = name;
  }

  @Override
  public ObservableTransformer<ScanData, ScanData> match() {
    return scanDataStream ->
            scanDataStream
            .filter(scanData -> {
              String scanRecordName = "";
              String deviceName = "";
              String adDataName = "";

              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                scanRecordName = getScanRecordName(scanData);
              }

              if (scanData.getBluetoothDevice().getName() != null) {
                deviceName = scanData.getBluetoothDevice().getName();
              }

              if (scanData.getParsedAdvertisement() != null
                      && scanData.getParsedAdvertisement().getName() != null) {
                adDataName = scanData.getParsedAdvertisement().getName();
              }

              return scanRecordName.contentEquals(name) || deviceName.contentEquals(name) || adDataName.contentEquals(name);
            });
  }

  @Override
  public boolean equals(Object o) {
    if (o != null && o instanceof NameScanMatcher) {
      NameScanMatcher other = (NameScanMatcher) o;
      return other.name.contentEquals(this.name);
    }

    return false;
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @TargetApi(21)
  private String getScanRecordName(ScanData scanData) {
    if (scanData.getScanResult() != null
            && scanData.getScanResult().getScanRecord() != null
            && scanData.getScanResult().getScanRecord().getDeviceName() != null) {
      return scanData.getScanResult().getScanRecord().getDeviceName();
    } else {
      return "";
    }
  }


}
