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
package com.uber.rx_central_ble;

import android.bluetooth.BluetoothDevice;
import android.support.annotation.Nullable;

import java.util.UUID;

/** Provides an abstraction layer for accessing details of a peripheral's advertisement data. */
public interface AdvertisingData {

  /**
   * Get the RSSI of the advertisement.
   *
   * @return RSSI
   */
  int getRssi();

  /**
   * Get the BluetoothDevice associated with this advertisement.
   *
   * @return the BluetoothDevice
   */
  BluetoothDevice getBluetoothDevice();

  /**
   * Get the advertised device name, either shortened or complete, with preference for complete.
   *
   * @return the name of the device
   */
  @Nullable
  String getName();

  /**
   * Check if a service was advertised.
   *
   * @param svc UUID of service
   * @return true if service was advertised
   */
  boolean hasService(UUID svc);

  /**
   * Get manufacturer data for a given manufacturer id.
   *
   * @param manufacturerId the manufacturer id
   * @return manufacturer data
   */
  @Nullable
  byte[] getManufacturerData(int manufacturerId);

  /**
   * Get the raw advertisement data.
   * @return the raw advertisement data.
   */
  byte[] getRawAdvertisement();

  /** Factory definition to produce AdvertisingData implementations. */
  interface Factory {

    /**
     * Produce a AdvertisingData given a discovered peripheral and accompanying advertising data.
     *
     * @param bluetoothDevice the BluetoothDevice for the peripheral
     * @param rawData raw advertising data bytes
     * @param rssi relative signal strength
     * @return a AdvertisingData implementation
     */
    AdvertisingData produce(BluetoothDevice bluetoothDevice, byte[] rawData, int rssi);
  }
}
