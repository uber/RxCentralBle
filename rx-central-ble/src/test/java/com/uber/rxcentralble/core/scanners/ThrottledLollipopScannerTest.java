package com.uber.rxcentralble.core.scanners;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;

import com.uber.rxcentralble.ScanData;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.TimeUnit;

import io.reactivex.observers.TestObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.TestScheduler;

import static com.uber.rxcentralble.core.scanners.ThrottledLollipopScanner.ANDROID_7_MAX_SCAN_DURATION_MS;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(RobolectricTestRunner.class)
@PowerMockIgnore({"org.powermock.*", "org.mockito.*", "org.robolectric.*", "android.*"})
@PrepareForTest({BluetoothAdapter.class})
public class ThrottledLollipopScannerTest {

  @Rule
  public PowerMockRule rule = new PowerMockRule();

  @Mock BluetoothAdapter bluetoothAdapter;
  @Mock BluetoothLeScanner bluetoothLeScanner;

  private final TestScheduler testScheduler = new TestScheduler();

  LollipopScanner scanner;
  TestObserver<ScanData> scanDataTestObserver;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    mockStatic(BluetoothAdapter.class);

    RxJavaPlugins.setComputationSchedulerHandler(schedulerCallable -> testScheduler);

    when(bluetoothAdapter.isEnabled()).thenReturn(true);
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(bluetoothAdapter);
    when(bluetoothAdapter.getBluetoothLeScanner()).thenReturn(bluetoothLeScanner);

    scanner = new ThrottledLollipopScanner();
  }

  @Test
  public void scan_throttling() {
    scanDataTestObserver = scanner.scan().test();

    testScheduler.advanceTimeBy(ANDROID_7_MAX_SCAN_DURATION_MS * 3, TimeUnit.MILLISECONDS);

    verify(bluetoothLeScanner, times(3))
            .startScan(any(), any(), any(ScanCallback.class));

    verify(bluetoothLeScanner, times(2)).stopScan(any(ScanCallback.class));
  }
}
