package com.uber.rxcentralble.core.matchers;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.os.ParcelUuid;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.jakewharton.rxrelay2.Relay;
import com.uber.rxcentralble.ParsedAdvertisement;
import com.uber.rxcentralble.ScanData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.reactivex.observers.TestObserver;

import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class ServiceScanMatcherTest {

  @Mock ScanData scanData;
  @Mock BluetoothDevice bluetoothDevice;
  @Mock ScanResult scanResult;
  @Mock ScanRecord scanRecord;
  @Mock ParsedAdvertisement parsedAdvertisement;

  private UUID uuid = UUID.randomUUID();
  private ParcelUuid parcelUuid = new ParcelUuid(uuid);
  private List<ParcelUuid> uuidList = new ArrayList<>();
  private ParcelUuid[] uuidArray = new ParcelUuid[1];
  private Relay<ScanData> scanDataRelay = BehaviorRelay.create();
  private TestObserver<ScanData> scanDataTestObserver;

  private ServiceScanMatcher serviceScanMatcher;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    when(scanData.getScanResult()).thenReturn(scanResult);
    when(scanResult.getScanRecord()).thenReturn(scanRecord);

    when(scanData.getBluetoothDevice()).thenReturn(bluetoothDevice);
    when(scanData.getParsedAdvertisement()).thenReturn(parsedAdvertisement);

    serviceScanMatcher = new ServiceScanMatcher(uuid);
    scanDataTestObserver = scanDataRelay
            .compose(serviceScanMatcher.match())
            .test();
  }

  @Test
  public void match_none() {
    scanDataRelay.accept(scanData);
    scanDataTestObserver.assertEmpty();
  }

  @Test
  public void match_scanResult() {
    uuidList.add(parcelUuid);
    when(scanRecord.getServiceUuids()).thenReturn(uuidList);

    scanDataRelay.accept(scanData);
    scanDataTestObserver.assertValue(scanData);
  }

  @Test
  public void match_bluetoothDevice() {
    uuidArray[0] = parcelUuid;
    when(bluetoothDevice.getUuids()).thenReturn(uuidArray);

    scanDataRelay.accept(scanData);
    scanDataTestObserver.assertValue(scanData);
  }

  @Test
  public void match_parsedAdvertisement() {
    when(parsedAdvertisement.hasService(Matchers.eq(uuid))).thenReturn(true);

    scanDataRelay.accept(scanData);
    scanDataTestObserver.assertValue(scanData);
  }
}
