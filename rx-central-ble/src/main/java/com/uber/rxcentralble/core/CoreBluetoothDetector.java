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

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.uber.rxcentralble.BluetoothDetector;
import com.uber.rxcentralble.RxCentralLogger;

import io.reactivex.Observable;

/** Core implementation of BluetoothDetector. */
@TargetApi(18)
public class CoreBluetoothDetector implements BluetoothDetector {

  private final Context context;
  private final BroadcastReceiver bluetoothStateReceiver;
  private final Observable<Capability> sharedBluetoothEnabled;
  private final BehaviorRelay<Capability> bluetoothEnabledRelay =
      BehaviorRelay.createDefault(Capability.UNSUPPORTED);

  public CoreBluetoothDetector(Context context) {
    this.context = context;

    this.bluetoothStateReceiver =
        new BroadcastReceiver() {

          @Override
          public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
              int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);

              if (RxCentralLogger.isDebug()) {
                RxCentralLogger.debug("Bluetooth State Changed: " + state);
              }

              if (state == BluetoothAdapter.STATE_ON) {
                bluetoothEnabledRelay.accept(Capability.ENABLED);
              } else {
                bluetoothEnabledRelay.accept(Capability.DISABLED);
              }
            }
          }
        };

    sharedBluetoothEnabled =
        bluetoothEnabledRelay
            .hide()
            .distinctUntilChanged()
            .doOnSubscribe(disposable -> startDetection())
            .doOnDispose(() -> stopDetection())
            .replay(1)
            .refCount();
  }

  @Override
  public Observable<Capability> capability() {
    return sharedBluetoothEnabled;
  }

  @Override
  public Observable<Boolean> enabled() {
    return sharedBluetoothEnabled.map(c -> c == Capability.ENABLED);
  }

  private void startDetection() {
    if (BluetoothAdapter.getDefaultAdapter() != null) {
      if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
        bluetoothEnabledRelay.accept(Capability.ENABLED);
      } else {
        bluetoothEnabledRelay.accept(Capability.DISABLED);
      }

      // Listen for Bluetooth state changes.
      context.registerReceiver(
          bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    } else if (RxCentralLogger.isError()) {
      RxCentralLogger.error("startDetection - Default Bluetooth Adapter is null!");
    }
  }

  private void stopDetection() {
    if (BluetoothAdapter.getDefaultAdapter() != null) {
      try {
        context.unregisterReceiver(bluetoothStateReceiver);
      } catch (IllegalArgumentException e) {
        if (RxCentralLogger.isError()) {
          RxCentralLogger.error("stopDetection - Unregister receiver failed!");
        }
      }
    }

    bluetoothEnabledRelay.accept(Capability.UNSUPPORTED);
  }
}
