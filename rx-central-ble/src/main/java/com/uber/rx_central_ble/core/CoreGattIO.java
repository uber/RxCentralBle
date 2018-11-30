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

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.uber.rx_central_ble.ConnectionError;
import com.uber.rx_central_ble.GattError;
import com.uber.rx_central_ble.GattIO;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.SingleSubject;

import static com.uber.rx_central_ble.GattIO.ConnectableState.CONNECTED;
import static com.uber.rx_central_ble.ConnectionError.Code.DISCONNECTION;
import static com.uber.rx_central_ble.GattError.Code.CHARACTERISTIC_SET_VALUE_FAILED;
import static com.uber.rx_central_ble.GattError.Code.CONNECTION_FAILED;
import static com.uber.rx_central_ble.GattError.Code.MISSING_CHARACTERISTIC;
import static com.uber.rx_central_ble.GattError.Code.READ_CHARACTERISTIC_FAILED;
import static com.uber.rx_central_ble.GattError.Code.READ_RSSI_FAILED;
import static com.uber.rx_central_ble.GattError.Code.REGISTER_NOTIFICATION_FAILED;
import static com.uber.rx_central_ble.GattError.Code.REQUEST_MTU_FAILED;
import static com.uber.rx_central_ble.GattError.Code.UNREGISTER_NOTIFICATION_FAILED;
import static com.uber.rx_central_ble.GattError.Code.WRITE_CHARACTERISTIC_FAILED;
import static com.uber.rx_central_ble.GattError.ERROR_STATUS_CALL_FAILED;

/** Core implementation of GattIO. */
public class CoreGattIO implements GattIO {

  private final BehaviorRelay<Boolean> connectedRelay = BehaviorRelay.createDefault(false);
  private final BehaviorRelay<Pair<UUID, byte[]>> notificationRelay = BehaviorRelay.create();
  private final Map<UUID, GattIO.Preprocessor> preprocessorMap = new HashMap<>();

  private final Context context;
  private final BluetoothDevice device;
  private final Object syncRoot = new Object();

  @Nullable private BluetoothGatt bluetoothGatt;
  @Nullable private BehaviorSubject<GattIO.ConnectableState> connectionStateSubject;
  @Nullable private Observable<ConnectableState> sharedConnectionState;
  @Nullable private SingleSubject currentOperation;
  @Nullable private SingleSubject<Pair<UUID, byte[]>> readSubject;
  @Nullable private SingleSubject<UUID> writeSubject;
  @Nullable private SingleSubject<UUID> registerNotificationSubject;
  @Nullable private SingleSubject<Integer> requestMtuSubject;
  @Nullable private SingleSubject<Integer> readRssiSubject;

  private int maxWriteLength = DEFAULT_MTU;

  public CoreGattIO(BluetoothDevice device, Context context) {
    this.context = context;
    this.device = device;
  }

  @Override
  public Observable<ConnectableState> connect() {
    if (sharedConnectionState != null) {
      return sharedConnectionState;
    }

    connectionStateSubject = BehaviorSubject.create();

    sharedConnectionState =
        connectionStateSubject
            .doOnNext(state -> connectedRelay.accept(state == CONNECTED))
            .doOnSubscribe(disposable -> processConnect())
            .doFinally(() -> disconnect())
            .replay(1)
            .refCount(); // Don't do this on normal disconnect status 0 / 8

    return sharedConnectionState;
  }

  @Override
  public Observable<Boolean> connected() {
    return connectedRelay;
  }

  @Override
  public Single<byte[]> read(UUID svc, UUID chr) {
    final SingleSubject<Pair<UUID, byte[]>> scopedSubject = SingleSubject.create();

    return scopedSubject
        .filter(chrPair -> chrPair.first.equals(chr))
        .flatMapSingle(chrPair -> Single.just(chrPair.second))
        .doOnSubscribe(disposable -> processRead(scopedSubject, svc, chr))
        .doFinally(() -> clearReadSubject(scopedSubject));
  }

  @Override
  public Completable write(UUID svc, UUID chr, byte[] data) {
    final SingleSubject<UUID> scopedSubject = SingleSubject.create();

    return scopedSubject
        .filter(writeChr -> writeChr.equals(chr))
        .ignoreElement()
        .doOnSubscribe(disposable -> processWrite(scopedSubject, svc, chr, data))
        .doFinally(() -> clearWriteSubject(scopedSubject));
  }

  @Override
  public Completable registerNotification(UUID svc, UUID chr) {
    return registerNotification(svc, chr, null);
  }

  @Override
  public Completable registerNotification(UUID svc, UUID chr, @Nullable Preprocessor preprocessor) {
    final SingleSubject<UUID> scopedSubject = SingleSubject.create();

    return scopedSubject
        .filter(regChr -> regChr.equals(chr))
        .ignoreElement()
        .doOnSubscribe(
            disposable -> processRegisterNotification(scopedSubject, svc, chr, preprocessor))
        .doFinally(() -> clearRegisterNotificationSubject(scopedSubject));
  }

  @Override
  public Completable unregisterNotification(UUID svc, UUID chr) {
    final SingleSubject<UUID> scopedSubject = SingleSubject.create();

    return scopedSubject
        .filter(regChr -> regChr.equals(chr))
        .ignoreElement()
        .doOnSubscribe(disposable -> processUnregisterNotification(scopedSubject, svc, chr))
        .doOnComplete(() -> preprocessorMap.remove(chr))
        .doFinally(() -> clearRegisterNotificationSubject(scopedSubject));
  }

  @Override
  public Observable<byte[]> notification(UUID chr) {
    return notificationRelay
        .filter(chrPair -> chrPair.first.equals(chr))
        .map(chrPair -> chrPair.second);
  }

  @TargetApi(21)
  @Override
  public Single<Integer> requestMtu(int mtu) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      return Single.error(new GattError(GattError.Code.MINIMUM_SDK_UNSUPPORTED));
    }

    final SingleSubject<Integer> scopedSubject = SingleSubject.create();

    return scopedSubject
        .doOnSubscribe(disposable -> processRequestMtu(scopedSubject, mtu))
        .doFinally(() -> clearRequestMtuSubject(scopedSubject));
  }

  @Override
  public Single<Integer> readRssi() {
    SingleSubject<Integer> scopedSubject = SingleSubject.create();

    return scopedSubject
        .doOnSubscribe(disposable -> processReadRssi(scopedSubject))
        .doFinally(() -> clearReadRssiSubject(scopedSubject));
  }

  @Override
  public int getMaxWriteLength() {
    return maxWriteLength;
  }

  @Override
  public void disconnect() {
    synchronized (syncRoot) {
      if (bluetoothGatt != null) {
        bluetoothGatt.disconnect();
        bluetoothGatt = null;
      }

      connectedRelay.accept(false);

      if (connectionStateSubject != null && connectionStateSubject.hasObservers()) {
        connectionStateSubject.onError(new ConnectionError(DISCONNECTION));
        connectionStateSubject = null;
      }

      if (currentOperation != null && currentOperation.hasObservers()) {
        currentOperation.onError(new ConnectionError(DISCONNECTION));
        currentOperation = null;
      }
    }
  }

  private void processConnect() {
    if (connectionStateSubject == null) {
      return;
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      bluetoothGatt = device.connectGatt(context, false, getGattCallback());
    } else {
      bluetoothGatt =
          device.connectGatt(context, false, getGattCallback(), BluetoothDevice.TRANSPORT_LE);
    }

    if (bluetoothGatt != null) {
      connectionStateSubject.onNext(ConnectableState.CONNECTING);
    } else {
      connectionStateSubject.onError(
          new ConnectionError(
              ConnectionError.Code.CONNECT_FAILED,
              new GattError(CONNECTION_FAILED, ERROR_STATUS_CALL_FAILED)));
    }
  }

  private void processRead(SingleSubject<Pair<UUID, byte[]>> scopedSubject, UUID svc, UUID chr) {
    synchronized (syncRoot) {
      GattError error = subscribeChecks();
      if (error != null) {
        scopedSubject.onError(error);
        return;
      }

      BluetoothGattCharacteristic characteristic = getCharacteristic(svc, chr);
      if (characteristic == null) {
        scopedSubject.onError(new GattError(MISSING_CHARACTERISTIC));
        return;
      }

      readSubject = scopedSubject;
      currentOperation = readSubject;

      if (bluetoothGatt != null && !bluetoothGatt.readCharacteristic(characteristic)) {
        readSubject.onError(new GattError(READ_CHARACTERISTIC_FAILED, ERROR_STATUS_CALL_FAILED));
      }
    }
  }

  private void clearReadSubject(final SingleSubject<Pair<UUID, byte[]>> scopedSubject) {
    synchronized (syncRoot) {
      if (readSubject == scopedSubject) {
        readSubject = null;
      }
    }
  }

  private void processWrite(SingleSubject<UUID> scopedSubject, UUID svc, UUID chr, byte[] data) {
    synchronized (syncRoot) {
      GattError error = subscribeChecks();
      if (error != null) {
        scopedSubject.onError(error);
        return;
      }

      BluetoothGattCharacteristic characteristic = getCharacteristic(svc, chr);
      if (characteristic == null) {
        scopedSubject.onError(new GattError(MISSING_CHARACTERISTIC));
        return;
      }

      writeSubject = scopedSubject;
      currentOperation = writeSubject;

      if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
          == BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) {
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
      } else {
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
      }

      if (!characteristic.setValue(data)) {
        writeSubject.onError(new GattError(CHARACTERISTIC_SET_VALUE_FAILED));
      }

      if (bluetoothGatt != null && !bluetoothGatt.writeCharacteristic(characteristic)) {
        writeSubject.onError(new GattError(WRITE_CHARACTERISTIC_FAILED, ERROR_STATUS_CALL_FAILED));
      }
    }
  }

  private void clearWriteSubject(final SingleSubject<UUID> scopedSubject) {
    synchronized (syncRoot) {
      if (writeSubject == scopedSubject) {
        writeSubject = null;
      }
    }
  }

  private void processRegisterNotification(
      SingleSubject<UUID> scopedSubject, UUID svc, UUID chr, @Nullable Preprocessor preprocessor) {
    synchronized (syncRoot) {
      GattError error = subscribeChecks();
      if (error != null) {
        scopedSubject.onError(error);
        return;
      }

      BluetoothGattCharacteristic characteristic = getCharacteristic(svc, chr);
      if (characteristic == null) {
        scopedSubject.onError(new GattError(MISSING_CHARACTERISTIC));
        return;
      }

      registerNotificationSubject = scopedSubject;
      currentOperation = registerNotificationSubject;

      if (bluetoothGatt != null) {
        error = setCharacteristicNotification(bluetoothGatt, characteristic, true);
        if (error != null) {
          registerNotificationSubject.onError(new GattError(REGISTER_NOTIFICATION_FAILED, error));
        } else {
          preprocessorMap.put(chr, preprocessor);
        }
      }
    }
  }

  private void processUnregisterNotification(
      SingleSubject<UUID> scopedSubject, UUID svc, UUID chr) {
    synchronized (syncRoot) {
      GattError error = subscribeChecks();
      if (error != null) {
        scopedSubject.onError(error);
        return;
      }

      BluetoothGattCharacteristic characteristic = getCharacteristic(svc, chr);
      if (characteristic == null) {
        scopedSubject.onError(new GattError(MISSING_CHARACTERISTIC));
        return;
      }

      registerNotificationSubject = scopedSubject;
      currentOperation = registerNotificationSubject;

      if (bluetoothGatt != null) {
        error = setCharacteristicNotification(bluetoothGatt, characteristic, false);
        if (error != null) {
          registerNotificationSubject.onError(new GattError(UNREGISTER_NOTIFICATION_FAILED, error));
        }
      }
    }
  }

  private void clearRegisterNotificationSubject(SingleSubject<UUID> scopedSubject) {
    synchronized (syncRoot) {
      if (registerNotificationSubject == scopedSubject) {
        registerNotificationSubject = null;
      }
    }
  }

  @TargetApi(21)
  private void processRequestMtu(SingleSubject<Integer> scopedSubject, int mtu) {
    synchronized (syncRoot) {
      GattError error = subscribeChecks();
      if (error != null) {
        scopedSubject.onError(error);
        return;
      }

      requestMtuSubject = scopedSubject;
      currentOperation = requestMtuSubject;

      if (bluetoothGatt != null && !bluetoothGatt.requestMtu(mtu)) {
        requestMtuSubject.onError(new GattError(REQUEST_MTU_FAILED, ERROR_STATUS_CALL_FAILED));
      }
    }
  }

  private void clearRequestMtuSubject(SingleSubject<Integer> scopedSubject) {
    synchronized (syncRoot) {
      if (requestMtuSubject == scopedSubject) {
        requestMtuSubject = null;
      }
    }
  }

  private void processReadRssi(SingleSubject<Integer> scopedSubject) {
    synchronized (syncRoot) {
      GattError error = subscribeChecks();
      if (error != null) {
        scopedSubject.onError(error);
        return;
      }

      readRssiSubject = scopedSubject;
      currentOperation = readRssiSubject;

      if (bluetoothGatt != null && !bluetoothGatt.readRemoteRssi()) {
        readRssiSubject.onError(new GattError(READ_RSSI_FAILED, ERROR_STATUS_CALL_FAILED));
      }
    }
  }

  private void clearReadRssiSubject(SingleSubject<Integer> scopedSubject) {
    synchronized (syncRoot) {
      if (readRssiSubject == scopedSubject) {
        readRssiSubject = null;
      }
    }
  }

  @Nullable
  private GattError subscribeChecks() {
    if (!isConnected()) {
      return new GattError(GattError.Code.DISCONNECTED);
    }

    if (currentOperation != null && currentOperation.hasObservers()) {
      return new GattError(GattError.Code.OPERATION_IN_PROGRESS);
    }

    return null;
  }

  private boolean isConnected() {
    return bluetoothGatt != null && connectedRelay.getValue();
  }

  @Nullable
  private GattError setCharacteristicNotification(
      BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic, boolean enable) {

    BluetoothGattDescriptor cccd = characteristic.getDescriptor(CCCD_UUID);
    if (cccd == null) {
      return new GattError(GattError.Code.SET_CHARACTERISTIC_NOTIFICATION_CCCD_MISSING);
    }

    if (!bluetoothGatt.setCharacteristicNotification(characteristic, enable)) {
      return new GattError(GattError.Code.SET_CHARACTERISTIC_NOTIFICATION_FAILED);
    }

    int properties = characteristic.getProperties();
    byte[] value;

    if (enable) {
      if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY)
          == BluetoothGattCharacteristic.PROPERTY_NOTIFY) {
        value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
      } else if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE)
          == BluetoothGattCharacteristic.PROPERTY_INDICATE) {
        value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
      } else {
        return new GattError(GattError.Code.SET_CHARACTERISTIC_NOTIFICATION_MISSING_PROPERTY);
      }
    } else {
      value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
    }

    characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);

    if (!cccd.setValue(value)) {
      return new GattError(CHARACTERISTIC_SET_VALUE_FAILED);
    }

    if (!bluetoothGatt.writeDescriptor(cccd)) {
      return new GattError(GattError.Code.WRITE_DESCRIPTOR_FAILED, ERROR_STATUS_CALL_FAILED);
    }

    return null;
  }

  @Nullable
  private BluetoothGattService getService(UUID uuid) {
    return bluetoothGatt != null ? bluetoothGatt.getService(uuid) : null;
  }

  @Nullable
  private BluetoothGattCharacteristic getCharacteristic(UUID svc, UUID chr) {
    BluetoothGattService s = getService(svc);
    if (s != null) {
      return s.getCharacteristic(chr);
    }

    return null;
  }

  private BluetoothGattCallback getGattCallback() {
    return new BluetoothGattCallback() {
      @Override
      public void onConnectionStateChange(
          final BluetoothGatt gatt, final int status, final int newState) {
        synchronized (syncRoot) {
          if (connectionStateSubject != null) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
              if (status == 0) {
                if (!gatt.discoverServices()) {
                  connectionStateSubject.onError(
                      new ConnectionError(
                          ConnectionError.Code.CONNECT_FAILED,
                          new GattError(
                              GattError.Code.SERVICE_DISCOVERY_FAILED, ERROR_STATUS_CALL_FAILED)));
                }
              } else {
                connectionStateSubject.onError(
                    new ConnectionError(
                        ConnectionError.Code.CONNECT_FAILED,
                        new GattError(CONNECTION_FAILED, status)));
              }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
              if (status == 0 || status == 8) {
                connectionStateSubject.onError(
                    new ConnectionError(
                        DISCONNECTION, new GattError(GattError.Code.CONNECTION_LOST, status)));
              } else {
                connectionStateSubject.onError(
                    new ConnectionError(DISCONNECTION, new GattError(CONNECTION_FAILED, status)));
              }

              gatt.close();
            }
          }
        }
      }

      @Override
      public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
        synchronized (syncRoot) {
          if (connectionStateSubject != null) {
            if (status == 0) {
              connectionStateSubject.onNext(CONNECTED);
            } else {
              connectionStateSubject.onError(
                  new ConnectionError(
                      ConnectionError.Code.CONNECT_FAILED,
                      new GattError(GattError.Code.SERVICE_DISCOVERY_FAILED, status)));
            }
          }
        }
      }

      @Override
      public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic chr) {
        synchronized (syncRoot) {
          Preprocessor preprocessor = preprocessorMap.get(chr.getUuid());
          if (preprocessor != null) {
            byte[] processedBytes = preprocessor.process(chr.getValue());
            if (processedBytes != null) {
              notificationRelay.accept(new Pair<>(chr.getUuid(), processedBytes));
            }
          }

          notificationRelay.accept(new Pair<>(chr.getUuid(), chr.getValue()));
        }
      }

      @Override
      public void onCharacteristicRead(
          BluetoothGatt gatt, BluetoothGattCharacteristic chr, int status) {
        operationResult(
            readSubject,
            new Pair<>(chr.getUuid(), chr.getValue()),
            status,
            READ_CHARACTERISTIC_FAILED);
      }

      @Override
      public void onCharacteristicWrite(
          BluetoothGatt gatt, BluetoothGattCharacteristic chr, int status) {
        operationResult(writeSubject, chr.getUuid(), status, WRITE_CHARACTERISTIC_FAILED);
      }

      @Override
      public void onDescriptorWrite(
          BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        if (descriptor.getUuid().equals(CCCD_UUID)) {
          operationResult(
              registerNotificationSubject,
              descriptor.getCharacteristic().getUuid(),
              status,
              GattError.Code.WRITE_DESCRIPTOR_FAILED);
        }
      }

      @Override
      public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        if (status == 0) {
          maxWriteLength = mtu;
        }

        operationResult(requestMtuSubject, mtu, status, REQUEST_MTU_FAILED);
      }

      @Override
      public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        operationResult(readRssiSubject, rssi, status, READ_RSSI_FAILED);
      }

      private <T> void operationResult(
          @Nullable SingleSubject<T> operationSubject,
          T result,
          int status,
          GattError.Code errorType) {
        synchronized (syncRoot) {
          if (currentOperation == operationSubject && operationSubject != null) {
            if (status == 0) {
              operationSubject.onSuccess(result);
            } else {
              operationSubject.onError(new GattError(errorType, status));
            }
          } else if (currentOperation != null) {
            GattError cause = new GattError(GattError.Code.OPERATION_RESULT_MISMATCH);
            currentOperation.onError(new GattError(errorType, status, cause));
          }
        }
      }
    };
  }

  /** Implementation of Factory to produce GattIO instances. */
  public static class Factory implements GattIO.Factory {

    @Override
    public GattIO produce(BluetoothDevice device, Context context) {
      return new CoreGattIO(device, context);
    }
  }
}
