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
package com.uber.rx_central_ble.core;

import android.bluetooth.BluetoothDevice;
import android.support.annotation.Nullable;

import com.uber.rx_central_ble.AdvertisingData;
import com.uber.rx_central_ble.Utils;

import java.io.UnsupportedEncodingException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core implementation of AdvertisingData interface.
 */
public class CoreAdvertisingData implements AdvertisingData {

  /**
   * Advertising data types.
   */
  private static final int AD_INCOMPLETE_16BIT_SVC_LIST = 0x02;
  private static final int AD_COMPLETE_16BIT_SVC_LIST = 0x03;
  private static final int AD_INCOMPLETE_32BIT_SVC_LIST = 0x04;
  private static final int AD_COMPLETE_32BIT_SVC_LIST = 0x05;
  private static final int AD_INCOMPLETE_128BIT_SVC_LIST = 0x06;
  private static final int AD_COMPLETE_128BIT_SVC_LIST = 0x07;
  private static final int AD_SHORTENED_LOCAL_NAME = 0x08;
  private static final int AD_COMPLETE_LOCAL_NAME = 0x09;
  private static final int AD_MANUFACTURER_DATA = 0xFF;

  private final List<UUID> servicesList = new ArrayList<>();
  private final Map<Integer, byte[]> mfgDataMap = new HashMap<>();

  private final int rssi;
  private final BluetoothDevice bluetoothDevice;
  private final byte[] rawAdvertisement;

  @Nullable String name;

  public CoreAdvertisingData(BluetoothDevice bluetoothDevice, byte[] rawAdvertisement, int rssi) {
    this.bluetoothDevice = bluetoothDevice;
    this.rawAdvertisement = rawAdvertisement;
    this.rssi = rssi;

    ByteBuffer byteBuffer = ByteBuffer.wrap(rawAdvertisement).order(ByteOrder.LITTLE_ENDIAN);
    try {
      while (byteBuffer.hasRemaining()) {
        int length = byteBuffer.get() & 0xFF;
        if (length <= byteBuffer.remaining()) {
          int dataType = byteBuffer.get() & 0xFF;
          parseAdData(dataType, length - 1, byteBuffer);
        }
      }
    } catch (BufferUnderflowException e) {
      // Ignore the exception; data will remain empty.
    }
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
  @Nullable
  public String getName() {
    return name;
  }

  @Override
  public boolean hasService(UUID svc) {
    return servicesList.contains(svc);
  }

  @Override
  @Nullable
  public byte[] getManufacturerData(int manufacturerId) {
    return mfgDataMap.get(manufacturerId);
  }

  @Override
  public byte[] getRawAdvertisement() {
    return new byte[0];
  }

  private void parseAdData(int dataType, int length, ByteBuffer byteBuffer) {
    int bytesRead = 0;
    try {
      switch (dataType) {
        case AD_INCOMPLETE_16BIT_SVC_LIST:
        case AD_COMPLETE_16BIT_SVC_LIST:
          while (bytesRead < length) {
            int uuid = byteBuffer.getShort();
            servicesList.add(Utils.uuidFromInteger(uuid));
            bytesRead += 2;
          }
          break;
        case AD_INCOMPLETE_32BIT_SVC_LIST:
        case AD_COMPLETE_32BIT_SVC_LIST:
          while (bytesRead < length) {
            int uuid = byteBuffer.getInt();
            servicesList.add(Utils.uuidFromInteger(uuid));
            bytesRead += 4;
          }
          break;
        case AD_INCOMPLETE_128BIT_SVC_LIST:
        case AD_COMPLETE_128BIT_SVC_LIST:
          while (bytesRead < length) {
            long lsb = byteBuffer.getLong();
            long msb = byteBuffer.getLong();
            servicesList.add(new UUID(msb, lsb));
            bytesRead += 8;
          }
          break;
        case AD_SHORTENED_LOCAL_NAME:
        case AD_COMPLETE_LOCAL_NAME:
          try {
            byte[] nameBytes = new byte[length];
            byteBuffer.get(nameBytes);
            name = new String(nameBytes, "UTF-8");
          } catch (UnsupportedEncodingException e) {
            // Ignore the exception; data will remain empty.
          }
          break;
        case AD_MANUFACTURER_DATA:
          int mfgId = byteBuffer.getShort() & 0xFFFF;
          byte[] data = new byte[length - 2];
          byteBuffer.get(data);
          mfgDataMap.put(mfgId, data);
          break;
        default:
          byteBuffer.position(byteBuffer.position() + length);
          break;
      }
    } catch (BufferUnderflowException e) {
      // Ignore the exception; data will remain empty.
    }
  }

  /** Factory to produce Core AdvertisingData implementations */
  public static class Factory implements AdvertisingData.Factory {

    @Override
    public AdvertisingData produce(BluetoothDevice bluetoothDevice, byte[] rawData, int rssi) {
      return new CoreAdvertisingData(bluetoothDevice, rawData, rssi);
    }
  }
}
