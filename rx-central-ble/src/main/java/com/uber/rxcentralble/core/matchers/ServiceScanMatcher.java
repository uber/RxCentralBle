package com.uber.rxcentralble.core.matchers;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.ParcelUuid;

import com.uber.rxcentralble.ScanData;
import com.uber.rxcentralble.ScanMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import io.reactivex.ObservableTransformer;

/**
 * ScanMatcher that matches the first discovered peripheral advertising a service UUID.
 */
public class ServiceScanMatcher implements ScanMatcher {

  private final UUID serviceUuid;

  public ServiceScanMatcher(UUID serviceUuid) {
    this.serviceUuid = serviceUuid;
  }

  @Override
  public ObservableTransformer<ScanData, ScanData> match() {
    return scanData -> scanData.filter(this::matchByUUID);
  }

  /* This helper function is to match BLE device by service UUID. */
  private boolean matchByUUID(ScanData scanData) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      List<ParcelUuid> uuids = getParcelUuid(scanData);

      if (uuids.size() > 0) {
        ParcelUuid serviceParcelUuid = new ParcelUuid(serviceUuid);
        for (ParcelUuid uuid : uuids) {
          if (uuid.equals(serviceParcelUuid)) {
            return true;
          }
        }
      }
    }

    if (scanData.getParsedAdvertisement() != null
        && scanData.getParsedAdvertisement().hasService(serviceUuid)) {
      return true;
    }

    return false;
  }

  @TargetApi(21)
  private List<ParcelUuid> getParcelUuid(ScanData scanData) {
    List<ParcelUuid> uuids = new ArrayList<>();
    if (scanData.getScanResult() != null
            && scanData.getScanResult().getScanRecord() != null
            && scanData.getScanResult().getScanRecord().getServiceUuids() != null) {
      uuids.addAll(scanData.getScanResult().getScanRecord().getServiceUuids());
    }

    if (scanData.getBluetoothDevice() != null && scanData.getBluetoothDevice().getUuids() != null) {
      uuids.addAll(Arrays.asList(scanData.getBluetoothDevice().getUuids()));
    }

    return uuids;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ServiceScanMatcher) {
      ServiceScanMatcher other = (ServiceScanMatcher) o;
      return other.serviceUuid.equals(this.serviceUuid);
    }

    return false;
  }

  @Override
  public int hashCode() {
    return serviceUuid.hashCode();
  }
}
