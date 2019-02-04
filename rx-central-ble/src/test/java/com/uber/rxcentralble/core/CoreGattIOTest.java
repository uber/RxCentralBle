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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Build;

import com.uber.rxcentralble.ConnectionError;
import com.uber.rxcentralble.GattError;
import com.uber.rxcentralble.GattIO;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.ReflectionHelpers;

import java.util.UUID;

import io.reactivex.observers.TestObserver;

import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
import static com.uber.rxcentralble.ConnectionError.Code.CONNECT_FAILED;
import static com.uber.rxcentralble.ConnectionError.Code.DISCONNECTION;
import static com.uber.rxcentralble.GattError.Code.CHARACTERISTIC_SET_VALUE_FAILED;
import static com.uber.rxcentralble.GattError.Code.DISCONNECTED;
import static com.uber.rxcentralble.GattError.Code.MISSING_CHARACTERISTIC;
import static com.uber.rxcentralble.GattError.Code.OPERATION_IN_PROGRESS;
import static com.uber.rxcentralble.GattError.Code.READ_CHARACTERISTIC_FAILED;
import static com.uber.rxcentralble.GattError.Code.READ_RSSI_FAILED;
import static com.uber.rxcentralble.GattError.Code.REGISTER_NOTIFICATION_FAILED;
import static com.uber.rxcentralble.GattError.Code.SERVICE_DISCOVERY_FAILED;
import static com.uber.rxcentralble.GattError.Code.SET_CHARACTERISTIC_NOTIFICATION_CCCD_MISSING;
import static com.uber.rxcentralble.GattError.Code.SET_CHARACTERISTIC_NOTIFICATION_MISSING_PROPERTY;
import static com.uber.rxcentralble.GattError.Code.REQUEST_MTU_FAILED;
import static com.uber.rxcentralble.GattError.Code.UNREGISTER_NOTIFICATION_FAILED;
import static com.uber.rxcentralble.GattError.Code.WRITE_CHARACTERISTIC_FAILED;
import static com.uber.rxcentralble.GattError.Code.WRITE_DESCRIPTOR_FAILED;
import static com.uber.rxcentralble.GattError.ERROR_STATUS_CALL_FAILED;
import static com.uber.rxcentralble.GattIO.ConnectableState.CONNECTED;
import static com.uber.rxcentralble.GattIO.ConnectableState.CONNECTING;
import static com.uber.rxcentralble.GattIO.MTU_OVERHEAD;
import static com.uber.rxcentralble.core.CoreGattIO.CCCD_UUID;
import static com.uber.rxcentralble.core.CoreGattIO.DEFAULT_MTU;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class CoreGattIOTest {

  @Mock BluetoothDevice bluetoothDevice;
  @Mock Context context;
  @Mock BluetoothGatt bluetoothGatt;
  @Mock BluetoothGattService bluetoothGattService;
  @Mock BluetoothGattCharacteristic bluetoothGattCharacteristic;
  @Mock BluetoothGattDescriptor bluetoothGattDescriptor;

  private final UUID svcUuid = UUID.randomUUID();
  private final UUID chrUuid = UUID.randomUUID();

  private CoreGattIO coreGattIO;
  private BluetoothGattCallback bluetoothGattCallback;
  private TestObserver<GattIO.ConnectableState> connectTestObserver;
  private TestObserver<byte[]> readTestObserver;
  private TestObserver<Void> writeTestObserver;
  private TestObserver<Void> registerNotificationTestObserver;
  private TestObserver<byte[]> notificationTestObserver;
  private TestObserver<Integer> setMtuTestObserver;
  private TestObserver<Integer> readRssiTestObserver;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", 21);

    coreGattIO = new CoreGattIO(bluetoothDevice, context);
  }

  @Test
  public void connect_connectGattFailed() {
    when(bluetoothDevice.connectGatt(any(), anyBoolean(), any())).thenReturn(null);

    connectTestObserver = coreGattIO.connect().test();

    connectTestObserver.assertError(
        throwable -> {
          ConnectionError error = (ConnectionError) throwable;
          if (error != null
              && error.getCode() == CONNECT_FAILED
              && error.getCause() != null
              && error.getCause() instanceof GattError) {
            GattError gattError = (GattError) error.getCause();
            return gattError.getErrorStatus() == ERROR_STATUS_CALL_FAILED;
          }

          return false;
        });
  }

  @Test
  public void connect_gattCallback_nonZeroStatus() {
    prepareConnect(false);

    bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, 99, BluetoothGatt.STATE_CONNECTED);

    connectTestObserver.assertError(
        throwable -> {
          ConnectionError error = (ConnectionError) throwable;
          if (error != null
              && error.getCode() == CONNECT_FAILED
              && error.getCause() != null
              && error.getCause() instanceof GattError) {
            GattError gattError = (GattError) error.getCause();
            return gattError.getErrorStatus() == 99;
          }

          return false;
        });

    verify(bluetoothGatt).disconnect();
  }

  @Test
  public void connect_serviceDiscoveryGattFailed() {
    prepareConnect(false);

    bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, 0, BluetoothGatt.STATE_CONNECTED);

    connectTestObserver.assertError(
        throwable -> {
          ConnectionError error = (ConnectionError) throwable;
          if (error != null
              && error.getCode() == CONNECT_FAILED
              && error.getCause() != null
              && error.getCause() instanceof GattError) {
            GattError gattError = (GattError) error.getCause();
            return gattError.getCode() == SERVICE_DISCOVERY_FAILED
                && gattError.getErrorStatus() == ERROR_STATUS_CALL_FAILED;
          }

          return false;
        });

    verify(bluetoothGatt).disconnect();
  }

  @Test
  public void connect_serviceDiscovery_gattCallback_nonZeroStatus() {
    prepareConnect(true);

    bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, 0, BluetoothGatt.STATE_CONNECTED);

    verify(bluetoothGatt).discoverServices();

    bluetoothGattCallback.onServicesDiscovered(bluetoothGatt, 99);

    connectTestObserver.assertError(
        throwable -> {
          ConnectionError error = (ConnectionError) throwable;
          if (error != null
              && error.getCode() == CONNECT_FAILED
              && error.getCause() != null
              && error.getCause() instanceof GattError) {
            GattError gattError = (GattError) error.getCause();
            return gattError.getCode() == SERVICE_DISCOVERY_FAILED
                && gattError.getErrorStatus() == 99;
          }

          return false;
        });

    verify(bluetoothGatt).disconnect();
  }

  @Test
  public void connect_success() {
    connect();

    connectTestObserver.assertValues(CONNECTING, CONNECTED);
  }

  @Test
  public void connect_verifyMulticasted() {
    connect();

    coreGattIO.connect().test();

    verifyNoMoreInteractions(bluetoothGatt);
  }

  @Test
  public void read_disconnected() {
    readTestObserver = coreGattIO.read(svcUuid, chrUuid).test();

    readTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null && error.getCode() == DISCONNECTED;
        });
  }

  @Test
  public void read_characteristicMissing() {
    connect();

    readTestObserver = coreGattIO.read(svcUuid, chrUuid).test();

    readTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null && error.getCode() == MISSING_CHARACTERISTIC;
        });
  }

  @Test
  public void read_gattReadFailed() {
    connect();

    prepareRead(false);

    readTestObserver = coreGattIO.read(svcUuid, chrUuid).test();

    readTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null
              && error.getCode() == READ_CHARACTERISTIC_FAILED
              && error.getErrorStatus() == ERROR_STATUS_CALL_FAILED;
        });
  }

  @Test
  public void read_gattCallback_nonZeroStatus() {
    connect();

    prepareRead(true);

    readTestObserver = coreGattIO.read(svcUuid, chrUuid).test();

    bluetoothGattCallback.onCharacteristicRead(bluetoothGatt, bluetoothGattCharacteristic, 99);

    readTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null
              && error.getCode() == READ_CHARACTERISTIC_FAILED
              && error.getErrorStatus() == 99;
        });
  }

  @Test
  public void read_success() {
    connect();

    prepareRead(true);

    byte[] readBytes = new byte[] {0x00};
    when(bluetoothGattCharacteristic.getValue()).thenReturn(readBytes);

    readTestObserver = coreGattIO.read(svcUuid, chrUuid).test();

    bluetoothGattCallback.onCharacteristicRead(bluetoothGatt, bluetoothGattCharacteristic, 0);

    readTestObserver.assertValue(readBytes);
  }

  @Test
  public void read_operationInProgress() {
    connect();

    prepareRead(true);

    coreGattIO.read(svcUuid, chrUuid).test();
    readTestObserver = coreGattIO.read(svcUuid, chrUuid).test();

    readTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null && error.getCode() == OPERATION_IN_PROGRESS;
        });
  }

  @Test
  public void write_disconnected() {
    byte[] writeBytes = new byte[] {0x00};
    writeTestObserver = coreGattIO.write(svcUuid, chrUuid, writeBytes).test();

    writeTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null && error.getCode() == DISCONNECTED;
        });
  }

  @Test
  public void write_characteristicMissing() {
    connect();

    byte[] writeBytes = new byte[] {0x00};
    writeTestObserver = coreGattIO.write(svcUuid, chrUuid, writeBytes).test();

    writeTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null && error.getCode() == MISSING_CHARACTERISTIC;
        });
  }

  @Test
  public void write_setValueFailed() {
    connect();

    prepareWrite(false, false);

    byte[] writeBytes = new byte[] {0x00};
    writeTestObserver = coreGattIO.write(svcUuid, chrUuid, writeBytes).test();

    writeTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null && error.getCode() == CHARACTERISTIC_SET_VALUE_FAILED;
        });
  }

  @Test
  public void write_gattWriteFailed() {
    connect();

    prepareWrite(true, false);

    byte[] writeBytes = new byte[] {0x00};
    writeTestObserver = coreGattIO.write(svcUuid, chrUuid, writeBytes).test();

    writeTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null
              && error.getCode() == WRITE_CHARACTERISTIC_FAILED
              && error.getErrorStatus() == ERROR_STATUS_CALL_FAILED;
        });
  }

  @Test
  public void write_gattCallback_nonZeroStatus() {
    connect();

    prepareWrite(true, true);

    byte[] writeBytes = new byte[] {0x00};
    writeTestObserver = coreGattIO.write(svcUuid, chrUuid, writeBytes).test();

    bluetoothGattCallback.onCharacteristicWrite(bluetoothGatt, bluetoothGattCharacteristic, 99);

    writeTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null
              && error.getCode() == WRITE_CHARACTERISTIC_FAILED
              && error.getErrorStatus() == 99;
        });
  }

  @Test
  public void write_success() {
    connect();

    prepareWrite(true, true);

    byte[] writeBytes = new byte[] {0x00};
    writeTestObserver = coreGattIO.write(svcUuid, chrUuid, writeBytes).test();

    bluetoothGattCallback.onCharacteristicWrite(bluetoothGatt, bluetoothGattCharacteristic, 0);

    writeTestObserver.assertComplete();
  }

  @Test
  public void write_operationInProgress() {
    connect();

    prepareWrite(true, true);

    byte[] writeBytes = new byte[] {0x00};
    coreGattIO.write(svcUuid, chrUuid, writeBytes).test();
    writeTestObserver = coreGattIO.write(svcUuid, chrUuid, writeBytes).test();

    writeTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null && error.getCode() == OPERATION_IN_PROGRESS;
        });
  }

  @Test
  public void registerNotification_disconnected() {
    registerNotificationTestObserver = coreGattIO.registerNotification(svcUuid, chrUuid).test();

    registerNotificationTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null && error.getCode() == DISCONNECTED;
        });
  }

  @Test
  public void registerNotification_operationInProgress() {
    connect();
    prepareNotifications(true, true, true, true, true);

    coreGattIO.registerNotification(svcUuid, chrUuid).test();
    registerNotificationTestObserver = coreGattIO.registerNotification(svcUuid, chrUuid).test();

    registerNotificationTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null && error.getCode() == OPERATION_IN_PROGRESS;
        });
  }

  @Test
  public void registerNotification_characteristicMissing() {
    connect();

    registerNotificationTestObserver = coreGattIO.registerNotification(svcUuid, chrUuid).test();

    registerNotificationTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null && error.getCode() == MISSING_CHARACTERISTIC;
        });
  }

  @Test
  public void registerNotification_characteristic_missingNotificationProperty() {
    connect();
    prepareNotifications(false, true, false, true, false);

    registerNotificationTestObserver = coreGattIO.registerNotification(svcUuid, chrUuid).test();

    registerNotificationTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          if (error != null
              && error.getCode() == REGISTER_NOTIFICATION_FAILED
              && error.getCause() != null
              && error.getCause() instanceof GattError) {
            GattError gattError = (GattError) error.getCause();
            return gattError.getCode() == SET_CHARACTERISTIC_NOTIFICATION_MISSING_PROPERTY;
          }

          return false;
        });
  }

  @Test
  public void registerNotification_descriptor_missing() {
    connect();
    prepareNotifications(true, false, false, false, false);

    registerNotificationTestObserver = coreGattIO.registerNotification(svcUuid, chrUuid).test();

    registerNotificationTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          if (error != null
              && error.getCode() == REGISTER_NOTIFICATION_FAILED
              && error.getCause() != null
              && error.getCause() instanceof GattError) {
            GattError gattError = (GattError) error.getCause();
            return gattError.getCode() == SET_CHARACTERISTIC_NOTIFICATION_CCCD_MISSING;
          }

          return false;
        });
  }

  @Test
  public void registerNotification_setValueFailed() {
    connect();
    prepareNotifications(true, true, false, true, false);

    registerNotificationTestObserver = coreGattIO.registerNotification(svcUuid, chrUuid).test();

    registerNotificationTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          if (error != null
              && error.getCode() == REGISTER_NOTIFICATION_FAILED
              && error.getCause() != null
              && error.getCause() instanceof GattError) {
            GattError gattError = (GattError) error.getCause();
            return gattError.getCode() == CHARACTERISTIC_SET_VALUE_FAILED;
          }

          return false;
        });
  }

  @Test
  public void registerNotification_descriptor_writeFailed() {
    connect();
    prepareNotifications(true, true, true, true, false);

    registerNotificationTestObserver = coreGattIO.registerNotification(svcUuid, chrUuid).test();

    registerNotificationTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          if (error != null
              && error.getCode() == REGISTER_NOTIFICATION_FAILED
              && error.getCause() != null
              && error.getCause() instanceof GattError) {
            GattError gattError = (GattError) error.getCause();
            return gattError.getCode() == WRITE_DESCRIPTOR_FAILED;
          }

          return false;
        });
  }

  @Test
  public void registerNotification_gattCallback_nonZeroStatus() {
    connect();
    prepareNotifications(true, true, true, true, true);

    registerNotificationTestObserver = coreGattIO.registerNotification(svcUuid, chrUuid).test();

    bluetoothGattCallback.onDescriptorWrite(bluetoothGatt, bluetoothGattDescriptor, 99);

    registerNotificationTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null
              && error.getCode() == WRITE_DESCRIPTOR_FAILED
              && error.getErrorStatus() == 99;
        });
  }

  @Test
  public void registerNotification_success() {
    connect();
    prepareNotifications(true, true, true, true, true);

    registerNotificationTestObserver = coreGattIO.registerNotification(svcUuid, chrUuid).test();

    bluetoothGattCallback.onDescriptorWrite(bluetoothGatt, bluetoothGattDescriptor, 0);

    registerNotificationTestObserver.assertComplete();
  }

  @Test
  public void unregisterNotification_disconnected() {
    registerNotificationTestObserver = coreGattIO.unregisterNotification(svcUuid, chrUuid).test();

    registerNotificationTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null && error.getCode() == DISCONNECTED;
        });
  }

  @Test
  public void unregisterNotification_operationInProgress() {
    connect();
    prepareNotifications(true, true, true, true, true);

    coreGattIO.registerNotification(svcUuid, chrUuid).test();
    registerNotificationTestObserver = coreGattIO.unregisterNotification(svcUuid, chrUuid).test();

    registerNotificationTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null && error.getCode() == OPERATION_IN_PROGRESS;
        });
  }

  @Test
  public void unregisterNotification_characteristicMissing() {
    connect();

    registerNotificationTestObserver = coreGattIO.unregisterNotification(svcUuid, chrUuid).test();

    registerNotificationTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null && error.getCode() == MISSING_CHARACTERISTIC;
        });
  }

  @Test
  public void unregisterNotification_descriptor_missing() {
    connect();
    prepareNotifications(true, false, false, false, false);

    registerNotificationTestObserver = coreGattIO.unregisterNotification(svcUuid, chrUuid).test();

    registerNotificationTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          if (error != null
              && error.getCode() == UNREGISTER_NOTIFICATION_FAILED
              && error.getCause() != null
              && error.getCause() instanceof GattError) {
            GattError gattError = (GattError) error.getCause();
            return gattError.getCode() == SET_CHARACTERISTIC_NOTIFICATION_CCCD_MISSING;
          }

          return false;
        });
  }

  @Test
  public void unregisterNotification_descriptor_writeFailed() {
    connect();
    prepareNotifications(true, true, true, true, false);

    registerNotificationTestObserver = coreGattIO.unregisterNotification(svcUuid, chrUuid).test();

    registerNotificationTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          if (error != null
              && error.getCode() == UNREGISTER_NOTIFICATION_FAILED
              && error.getCause() != null
              && error.getCause() instanceof GattError) {
            GattError gattError = (GattError) error.getCause();
            return gattError.getCode() == WRITE_DESCRIPTOR_FAILED;
          }

          return false;
        });
  }

  @Test
  public void unregisterNotification_gattCallback_nonZeroStatus() {
    connect();
    prepareNotifications(true, true, true, true, true);

    registerNotificationTestObserver = coreGattIO.unregisterNotification(svcUuid, chrUuid).test();

    bluetoothGattCallback.onDescriptorWrite(bluetoothGatt, bluetoothGattDescriptor, 99);

    registerNotificationTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null
              && error.getCode() == WRITE_DESCRIPTOR_FAILED
              && error.getErrorStatus() == 99;
        });
  }

  @Test
  public void unregisterNotification_success() {
    connect();
    prepareNotifications(true, true, true, true, true);

    registerNotificationTestObserver = coreGattIO.unregisterNotification(svcUuid, chrUuid).test();

    bluetoothGattCallback.onDescriptorWrite(bluetoothGatt, bluetoothGattDescriptor, 0);

    registerNotificationTestObserver.assertComplete();
  }

  @Test
  public void notifications() {
    connect();
    prepareGatt();

    byte[] notification = new byte[] {0x00};
    when(bluetoothGattCharacteristic.getValue()).thenReturn(notification);

    notificationTestObserver = coreGattIO.notification(chrUuid).test();

    bluetoothGattCallback.onCharacteristicChanged(bluetoothGatt, bluetoothGattCharacteristic);

    notificationTestObserver.assertValue(notification);
  }

  @Test
  public void setMtu_disconnected() {
    setMtuTestObserver = coreGattIO.requestMtu(100).test();

    setMtuTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null && error.getCode() == DISCONNECTED;
        });
  }

  @Test
  public void setMtu_operationInProgress() {
    connect();

    when(bluetoothGatt.requestMtu(anyInt())).thenReturn(true);

    coreGattIO.requestMtu(100).test();
    setMtuTestObserver = coreGattIO.requestMtu(100).test();

    setMtuTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null && error.getCode() == OPERATION_IN_PROGRESS;
        });
  }

  @Test
  public void setMtu_gattFailed() {
    connect();

    when(bluetoothGatt.requestMtu(anyInt())).thenReturn(false);

    setMtuTestObserver = coreGattIO.requestMtu(100).test();

    setMtuTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null
              && error.getCode() == REQUEST_MTU_FAILED
              && error.getErrorStatus() == ERROR_STATUS_CALL_FAILED;
        });
  }

  @Test
  public void setMtu_gattCallback_nonZeroStatus() {
    connect();

    when(bluetoothGatt.requestMtu(anyInt())).thenReturn(true);

    setMtuTestObserver = coreGattIO.requestMtu(100).test();

    bluetoothGattCallback.onMtuChanged(bluetoothGatt, -1, 99);

    setMtuTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null
              && error.getCode() == REQUEST_MTU_FAILED
              && error.getErrorStatus() == 99;
        });

    assertEquals(coreGattIO.getMaxWriteLength(), DEFAULT_MTU - MTU_OVERHEAD);
  }

  @Test
  public void setMtu_success() {
    connect();

    when(bluetoothGatt.requestMtu(anyInt())).thenReturn(true);

    setMtuTestObserver = coreGattIO.requestMtu(100).test();

    bluetoothGattCallback.onMtuChanged(bluetoothGatt, 100, 0);

    setMtuTestObserver.assertValue(100);

    assertEquals(coreGattIO.getMaxWriteLength(), 100 - MTU_OVERHEAD);
  }

  @Test
  public void readRssi_disconnected() {
    readRssiTestObserver = coreGattIO.readRssi().test();

    readRssiTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null && error.getCode() == DISCONNECTED;
        });
  }

  @Test
  public void readRssi_operationInProgress() {
    connect();

    when(bluetoothGatt.readRemoteRssi()).thenReturn(true);

    coreGattIO.readRssi().test();
    readRssiTestObserver = coreGattIO.readRssi().test();

    readRssiTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null && error.getCode() == OPERATION_IN_PROGRESS;
        });
  }

  @Test
  public void readRssi_gattFailed() {
    connect();

    when(bluetoothGatt.readRemoteRssi()).thenReturn(false);

    readRssiTestObserver = coreGattIO.readRssi().test();

    readRssiTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null
              && error.getCode() == READ_RSSI_FAILED
              && error.getErrorStatus() == ERROR_STATUS_CALL_FAILED;
        });
  }

  @Test
  public void readRssi_gattCallback_nonZeroStatus() {
    connect();

    when(bluetoothGatt.readRemoteRssi()).thenReturn(true);

    readRssiTestObserver = coreGattIO.readRssi().test();

    bluetoothGattCallback.onReadRemoteRssi(bluetoothGatt, 0, 99);

    readRssiTestObserver.assertError(
        throwable -> {
          GattError error = (GattError) throwable;
          return error != null
              && error.getCode() == READ_RSSI_FAILED
              && error.getErrorStatus() == 99;
        });
  }

  @Test
  public void readRssi_success() {
    connect();

    when(bluetoothGatt.readRemoteRssi()).thenReturn(true);

    readRssiTestObserver = coreGattIO.readRssi().test();

    bluetoothGattCallback.onReadRemoteRssi(bluetoothGatt, 100, 0);

    readRssiTestObserver.assertValue(100);
  }

  @Test
  public void disconnect() {
    connect();

    coreGattIO.disconnect();

    connectTestObserver.assertError(
        throwable -> {
          ConnectionError error = (ConnectionError) throwable;
          return error != null && error.getCode() == DISCONNECTION;
        });
  }

  private void prepareConnect(boolean discoverServiceSuccess) {
    when(bluetoothDevice.connectGatt(any(), anyBoolean(), any())).thenReturn(bluetoothGatt);
    when(bluetoothGatt.discoverServices()).thenReturn(discoverServiceSuccess);

    connectTestObserver = coreGattIO.connect().test();

    ArgumentCaptor<BluetoothGattCallback> gattCaptor =
        ArgumentCaptor.forClass(BluetoothGattCallback.class);
    verify(bluetoothDevice).connectGatt(any(), anyBoolean(), gattCaptor.capture());

    bluetoothGattCallback = gattCaptor.getValue();
  }

  private void connect() {
    prepareConnect(true);

    bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, 0, BluetoothGatt.STATE_CONNECTED);

    verify(bluetoothGatt).discoverServices();

    bluetoothGattCallback.onServicesDiscovered(bluetoothGatt, 0);
  }

  private void prepareGatt() {
    when(bluetoothGattCharacteristic.getUuid()).thenReturn(chrUuid);
    when(bluetoothGatt.getService(any())).thenReturn(bluetoothGattService);
    when(bluetoothGattService.getCharacteristic(any())).thenReturn(bluetoothGattCharacteristic);
  }

  private void prepareRead(boolean gattReadSuccess) {
    prepareGatt();
    when(bluetoothGatt.readCharacteristic(any())).thenReturn(gattReadSuccess);
  }

  private void prepareWrite(boolean setValueSuccess, boolean gattWriteSuccess) {
    prepareGatt();
    when(bluetoothGattCharacteristic.getProperties()).thenReturn(PROPERTY_WRITE_NO_RESPONSE);
    when(bluetoothGattCharacteristic.setValue(any(byte[].class))).thenReturn(setValueSuccess);
    when(bluetoothGatt.writeCharacteristic(any())).thenReturn(gattWriteSuccess);
  }

  private void prepareNotifications(
      boolean descriptorNotificationsEnabled,
      boolean cccdPresent,
      boolean setValueSuccess,
      boolean setCharacteristicSuccess,
      boolean gattWriteDescriptorSuccess) {
    prepareGatt();

    when(bluetoothGattDescriptor.setValue(any())).thenReturn(setValueSuccess);
    when(bluetoothGattDescriptor.getUuid()).thenReturn(CCCD_UUID);
    when(bluetoothGattDescriptor.getCharacteristic()).thenReturn(bluetoothGattCharacteristic);

    if (descriptorNotificationsEnabled) {
      when(bluetoothGattCharacteristic.getProperties()).thenReturn(PROPERTY_NOTIFY);
    } else {
      when(bluetoothGattCharacteristic.getProperties()).thenReturn(0x00);
    }

    if (cccdPresent) {
      when(bluetoothGattCharacteristic.getDescriptor(any())).thenReturn(bluetoothGattDescriptor);
    }

    when(bluetoothGatt.setCharacteristicNotification(any(), anyBoolean()))
        .thenReturn(setCharacteristicSuccess);

    when(bluetoothGatt.writeDescriptor(any())).thenReturn(gattWriteDescriptorSuccess);
  }
}
