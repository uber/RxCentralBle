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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;
import android.support.annotation.Nullable;

import com.uber.rxcentralble.ParsedAdvertisement;
import com.uber.rxcentralble.ScanData;

/**
 * JellyBean implementation of ScanData interface.
 */
public class JellyBeanScanData implements ScanData {

  private final int rssi;
  private final BluetoothDevice bluetoothDevice;
  private final ParsedAdvertisement parsedAdvertisement;

  public JellyBeanScanData(BluetoothDevice bluetoothDevice,
                           int rssi,
                           @Nullable ParsedAdvertisement parsedAdvertisement) {
    this.bluetoothDevice = bluetoothDevice;
    this.rssi = rssi;
    this.parsedAdvertisement = parsedAdvertisement;
  }

  @Override
  public int getRssi() {
    return rssi;
  }

  @Override
  public BluetoothDevice getBluetoothDevice() {
    return bluetoothDevice;
  }

  @Override
  public ParsedAdvertisement getParsedAdvertisement() {
    return parsedAdvertisement;
  }

  @Override
  public ScanResult getScanResult() {
    return null;
  }
}
