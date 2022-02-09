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
package com.uber.rxcentralble.core;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.uber.rxcentralble.BluetoothDetector;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RobolectricTestRunner;

import io.reactivex.observers.TestObserver;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(RobolectricTestRunner.class)
@PrepareForTest({BluetoothAdapter.class})
public class CoreBluetoothDetectorTest {

  @Rule
  public PowerMockRule rule = new PowerMockRule();

  @Mock Context context;
  @Mock BluetoothAdapter bluetoothAdapter;
  @Mock IntentFilter intentFilter;
  @Mock Intent bleIntent;

  CoreBluetoothDetector coreBluetoothDetector;
  TestObserver<Boolean> enabledTestObserver;
  TestObserver<BluetoothDetector.Capability> capabilityTestObserver;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    whenNew(IntentFilter.class).withAnyArguments().thenReturn(intentFilter);

    mockStatic(BluetoothAdapter.class);

    coreBluetoothDetector = new CoreBluetoothDetector(context);
  }

  @Test
  public void unsupported() {
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(null);

    enabledTestObserver = coreBluetoothDetector.enabled().test();
    capabilityTestObserver = coreBluetoothDetector.capability().test();

    enabledTestObserver.assertValue(false);
    capabilityTestObserver.assertValue(BluetoothDetector.Capability.UNSUPPORTED);
  }

  @Test
  public void disabled() {
    when(bluetoothAdapter.isEnabled()).thenReturn(false);
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(bluetoothAdapter);

    enabledTestObserver = coreBluetoothDetector.enabled().test();
    capabilityTestObserver = coreBluetoothDetector.capability().test();

    enabledTestObserver.assertValue(false);
    capabilityTestObserver.assertValue(BluetoothDetector.Capability.DISABLED);
  }

  @Test
  public void enabled() {
    when(bluetoothAdapter.isEnabled()).thenReturn(true);
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(bluetoothAdapter);

    enabledTestObserver = coreBluetoothDetector.enabled().test();
    capabilityTestObserver = coreBluetoothDetector.capability().test();

    enabledTestObserver.assertValue(true);
    capabilityTestObserver.assertValue(BluetoothDetector.Capability.ENABLED);
  }

  @Test
  public void disabled_thenEnabled() {
    when(bluetoothAdapter.isEnabled()).thenReturn(false);
    when(BluetoothAdapter.getDefaultAdapter()).thenReturn(bluetoothAdapter);

    enabledTestObserver = coreBluetoothDetector.enabled().test();
    capabilityTestObserver = coreBluetoothDetector.capability().test();

    ArgumentCaptor<BroadcastReceiver> argument = ArgumentCaptor.forClass(BroadcastReceiver.class);
    verify(context).registerReceiver(argument.capture(), any());

    when(bleIntent.getAction()).thenReturn(BluetoothAdapter.ACTION_STATE_CHANGED);
    when(bleIntent.getIntExtra(anyString(), anyInt())).thenReturn(BluetoothAdapter.STATE_ON);
    argument.getValue().onReceive(context, bleIntent);

    enabledTestObserver.assertValues(false, true);
    capabilityTestObserver.assertValues(
        BluetoothDetector.Capability.DISABLED, BluetoothDetector.Capability.ENABLED);
  }
}
