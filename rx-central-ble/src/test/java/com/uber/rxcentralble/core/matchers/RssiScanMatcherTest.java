package com.uber.rxcentralble.core.matchers;

import android.bluetooth.BluetoothDevice;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.jakewharton.rxrelay2.Relay;
import com.uber.rxcentralble.ScanData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.TimeUnit;

import io.reactivex.ObservableTransformer;
import io.reactivex.observers.TestObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.TestScheduler;

import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class RssiScanMatcherTest {

  private final TestScheduler testScheduler = new TestScheduler();

  @Mock ScanData scanData1;
  @Mock ScanData scanData2;
  @Mock BluetoothDevice bluetoothDevice1;
  @Mock BluetoothDevice bluetoothDevice2;
  @Mock ServiceScanMatcher serviceScanMatcher;

  private Relay<ScanData> scanDataRelay = BehaviorRelay.create();
  private TestObserver<ScanData> scanDataTestObserver;

  private RssiScanMatcher rssiScanMatcher;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    RxJavaPlugins.setComputationSchedulerHandler(schedulerCallable -> testScheduler);

    when(scanData1.getBluetoothDevice()).thenReturn(bluetoothDevice1);
    when(scanData2.getBluetoothDevice()).thenReturn(bluetoothDevice2);

    when(bluetoothDevice1.getAddress()).thenReturn("bluetoothDevice1");
    when(bluetoothDevice2.getAddress()).thenReturn("bluetoothDevice2");

    rssiScanMatcher = new RssiScanMatcher(serviceScanMatcher);
  }

  @After
  public void after() {
    RxJavaPlugins.reset();
  }

  @Test
  public void match_none() {
    ObservableTransformer<ScanData, ScanData> serviceMatch = scanData -> scanData.filter(sd -> false);
    when(serviceScanMatcher.match()).thenReturn(serviceMatch);

    scanDataTestObserver = scanDataRelay
            .compose(rssiScanMatcher.match())
            .test();

    scanDataRelay.accept(scanData1);
    scanDataRelay.accept(scanData2);
    scanDataTestObserver.assertEmpty();
  }

  @Test
  public void match_closest() {
    ObservableTransformer<ScanData, ScanData> serviceMatch = scanData -> scanData.filter(sd -> true);
    when(serviceScanMatcher.match()).thenReturn(serviceMatch);

    scanDataTestObserver = scanDataRelay
            .compose(rssiScanMatcher.match())
            .test();

    when(scanData1.getRssi()).thenReturn(-60);
    when(scanData2.getRssi()).thenReturn(-50);

    scanDataRelay.accept(scanData1);

    testScheduler.advanceTimeBy(500, TimeUnit.MILLISECONDS);

    scanDataRelay.accept(scanData2);

    testScheduler.advanceTimeBy(
            RssiScanMatcher.DEFAULT_MATCH_DELAY_MS + 500,
            TimeUnit.MILLISECONDS);

    scanDataTestObserver.assertValue(scanData2);
  }
}
