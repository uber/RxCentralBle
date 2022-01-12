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
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.jakewharton.rxrelay2.PublishRelay;
import com.uber.rxcentralble.ConnectionError;
import com.uber.rxcentralble.PeripheralError;
import com.uber.rxcentralble.Peripheral;
import com.uber.rxcentralble.RxCentralLogger;
import com.uber.rxcentralble.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.SingleSubject;

import static com.uber.rxcentralble.Peripheral.ConnectableState.CONNECTED;
import static com.uber.rxcentralble.ConnectionError.Code.DISCONNECTION;
import static com.uber.rxcentralble.PeripheralError.Code.CHARACTERISTIC_SET_VALUE_FAILED;
import static com.uber.rxcentralble.PeripheralError.Code.CONNECTION_FAILED;
import static com.uber.rxcentralble.PeripheralError.Code.MISSING_CHARACTERISTIC;
import static com.uber.rxcentralble.PeripheralError.Code.READ_CHARACTERISTIC_FAILED;
import static com.uber.rxcentralble.PeripheralError.Code.READ_RSSI_FAILED;
import static com.uber.rxcentralble.PeripheralError.Code.REGISTER_NOTIFICATION_FAILED;
import static com.uber.rxcentralble.PeripheralError.Code.REQUEST_MTU_FAILED;
import static com.uber.rxcentralble.PeripheralError.Code.UNREGISTER_NOTIFICATION_FAILED;
import static com.uber.rxcentralble.PeripheralError.Code.WRITE_CHARACTERISTIC_FAILED;
import static com.uber.rxcentralble.PeripheralError.ERROR_STATUS_CALL_FAILED;

/** Core implementation of Peripheral. */
public class CorePeripheral implements Peripheral {

  private final BehaviorRelay<Boolean> connectedRelay = BehaviorRelay.createDefault(false);
  private final PublishRelay<Pair<UUID, byte[]>> notificationRelay = PublishRelay.create();
  private final Map<UUID, Peripheral.Preprocessor> preprocessorMap = new HashMap<>();

  private final Context context;
  private final BluetoothDevice device;
  private final Object syncRoot = new Object();

  @Nullable private BluetoothGatt bluetoothGatt;
  @Nullable private BehaviorSubject<Peripheral.ConnectableState> connectionStateSubject;
  @Nullable private Observable<ConnectableState> sharedConnectionState;
  @Nullable private SingleSubject currentOperation;
  @Nullable private SingleSubject<Pair<UUID, byte[]>> readSubject;
  @Nullable private SingleSubject<UUID> writeSubject;
  @Nullable private SingleSubject<UUID> registerNotificationSubject;
  @Nullable private SingleSubject<Integer> requestMtuSubject;
  @Nullable private SingleSubject<Integer> readRssiSubject;

  private int mtu = DEFAULT_MTU;

  public CorePeripheral(BluetoothDevice device, Context context) {
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
      return Single.error(new PeripheralError(PeripheralError.Code.MINIMUM_SDK_UNSUPPORTED));
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
    return mtu - MTU_OVERHEAD;
  }

  @Override
  public void disconnect() {
    synchronized (syncRoot) {
      if (bluetoothGatt != null) {
        bluetoothGatt.disconnect();
        bluetoothGatt.close();
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
              new PeripheralError(CONNECTION_FAILED, ERROR_STATUS_CALL_FAILED)));
    }
  }

  private void processRead(SingleSubject<Pair<UUID, byte[]>> scopedSubject, UUID svc, UUID chr) {
    synchronized (syncRoot) {
      PeripheralError error = subscribeChecks();
      if (error != null) {
        scopedSubject.onError(error);
        return;
      }

      BluetoothGattCharacteristic characteristic = getCharacteristic(svc, chr);
      if (characteristic == null) {
        scopedSubject.onError(new PeripheralError(MISSING_CHARACTERISTIC));
        return;
      }

      readSubject = scopedSubject;
      currentOperation = readSubject;

      if (bluetoothGatt != null && !bluetoothGatt.readCharacteristic(characteristic)) {
        readSubject.onError(new PeripheralError(READ_CHARACTERISTIC_FAILED, ERROR_STATUS_CALL_FAILED));
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
      PeripheralError error = subscribeChecks();
      if (error != null) {
        scopedSubject.onError(error);
        return;
      }

      BluetoothGattCharacteristic characteristic = getCharacteristic(svc, chr);
      if (characteristic == null) {
        scopedSubject.onError(new PeripheralError(MISSING_CHARACTERISTIC));
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
        writeSubject.onError(new PeripheralError(CHARACTERISTIC_SET_VALUE_FAILED));
      }

      if (bluetoothGatt != null && !bluetoothGatt.writeCharacteristic(characteristic)) {
        writeSubject.onError(new PeripheralError(WRITE_CHARACTERISTIC_FAILED, ERROR_STATUS_CALL_FAILED));
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
      PeripheralError error = subscribeChecks();
      if (error != null) {
        scopedSubject.onError(error);
        return;
      }

      BluetoothGattCharacteristic characteristic = getCharacteristic(svc, chr);
      if (characteristic == null) {
        scopedSubject.onError(new PeripheralError(MISSING_CHARACTERISTIC));
        return;
      }

      registerNotificationSubject = scopedSubject;
      currentOperation = registerNotificationSubject;

      if (bluetoothGatt != null) {
        error = setCharacteristicNotification(bluetoothGatt, characteristic, true);
        if (error != null) {
          registerNotificationSubject.onError(new PeripheralError(REGISTER_NOTIFICATION_FAILED, error));
        } else {
          preprocessorMap.put(chr, preprocessor);
        }
      }
    }
  }

  private void processUnregisterNotification(
      SingleSubject<UUID> scopedSubject, UUID svc, UUID chr) {
    synchronized (syncRoot) {
      PeripheralError error = subscribeChecks();
      if (error != null) {
        scopedSubject.onError(error);
        return;
      }

      BluetoothGattCharacteristic characteristic = getCharacteristic(svc, chr);
      if (characteristic == null) {
        scopedSubject.onError(new PeripheralError(MISSING_CHARACTERISTIC));
        return;
      }

      registerNotificationSubject = scopedSubject;
      currentOperation = registerNotificationSubject;

      if (bluetoothGatt != null) {
        error = setCharacteristicNotification(bluetoothGatt, characteristic, false);
        if (error != null) {
          registerNotificationSubject.onError(new PeripheralError(UNREGISTER_NOTIFICATION_FAILED, error));
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
      PeripheralError error = subscribeChecks();
      if (error != null) {
        scopedSubject.onError(error);
        return;
      }

      requestMtuSubject = scopedSubject;
      currentOperation = requestMtuSubject;

      if (bluetoothGatt != null && !bluetoothGatt.requestMtu(mtu)) {
        requestMtuSubject.onError(new PeripheralError(REQUEST_MTU_FAILED, ERROR_STATUS_CALL_FAILED));
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
      PeripheralError error = subscribeChecks();
      if (error != null) {
        scopedSubject.onError(error);
        return;
      }

      readRssiSubject = scopedSubject;
      currentOperation = readRssiSubject;

      if (bluetoothGatt != null && !bluetoothGatt.readRemoteRssi()) {
        readRssiSubject.onError(new PeripheralError(READ_RSSI_FAILED, ERROR_STATUS_CALL_FAILED));
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
  private PeripheralError subscribeChecks() {
    if (!isConnected()) {
      return new PeripheralError(PeripheralError.Code.DISCONNECTED);
    }

    if (currentOperation != null && currentOperation.hasObservers()) {
      return new PeripheralError(PeripheralError.Code.OPERATION_IN_PROGRESS);
    }

    return null;
  }

  private boolean isConnected() {
    return bluetoothGatt != null && connectedRelay.getValue();
  }

  @Nullable
  private PeripheralError setCharacteristicNotification(
      BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic, boolean enable) {

    BluetoothGattDescriptor cccd = characteristic.getDescriptor(CCCD_UUID);
    if (cccd == null) {
      return new PeripheralError(PeripheralError.Code.SET_CHARACTERISTIC_NOTIFICATION_CCCD_MISSING);
    }

    if (!bluetoothGatt.setCharacteristicNotification(characteristic, enable)) {
      return new PeripheralError(PeripheralError.Code.SET_CHARACTERISTIC_NOTIFICATION_FAILED);
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
        return new PeripheralError(PeripheralError.Code.SET_CHARACTERISTIC_NOTIFICATION_MISSING_PROPERTY);
      }
    } else {
      value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
    }

    characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);

    if (!cccd.setValue(value)) {
      return new PeripheralError(CHARACTERISTIC_SET_VALUE_FAILED);
    }

    if (!bluetoothGatt.writeDescriptor(cccd)) {
      return new PeripheralError(PeripheralError.Code.WRITE_DESCRIPTOR_FAILED, ERROR_STATUS_CALL_FAILED);
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
        if (RxCentralLogger.isDebug()) {
          RxCentralLogger.debug("onConnectionStateChange - Status: " + status
                  + " | State: " + newState);
        }

        synchronized (syncRoot) {
          if (connectionStateSubject != null) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
              if (status == 0) {
                if (!gatt.discoverServices()) {
                  connectionStateSubject.onError(
                      new ConnectionError(
                          ConnectionError.Code.CONNECT_FAILED,
                          new PeripheralError(
                              PeripheralError.Code.SERVICE_DISCOVERY_FAILED, ERROR_STATUS_CALL_FAILED)));
                }
              } else {
                connectionStateSubject.onError(
                    new ConnectionError(
                        ConnectionError.Code.CONNECT_FAILED,
                        new PeripheralError(CONNECTION_FAILED, status)));
              }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
              if (status == 0 || status == 8) {
                connectionStateSubject.onError(
                    new ConnectionError(
                        DISCONNECTION, new PeripheralError(PeripheralError.Code.CONNECTION_LOST, status)));
              } else {
                connectionStateSubject.onError(
                    new ConnectionError(DISCONNECTION, new PeripheralError(CONNECTION_FAILED, status)));
              }

              gatt.close();
            }
          }
        }
      }

      @Override
      public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
        if (RxCentralLogger.isDebug()) {
          RxCentralLogger.debug("onServicesDiscovered - Status: " + status);
        }

        synchronized (syncRoot) {
          if (connectionStateSubject != null) {
            if (status == 0) {
              connectionStateSubject.onNext(CONNECTED);
            } else {
              connectionStateSubject.onError(
                  new ConnectionError(
                      ConnectionError.Code.CONNECT_FAILED,
                      new PeripheralError(PeripheralError.Code.SERVICE_DISCOVERY_FAILED, status)));
            }
          }
        }
      }

      @Override
      public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic chr) {
        if (RxCentralLogger.isDebug()) {
          RxCentralLogger.debug("onCharacteristicChanged - UUID: " + chr.getUuid() + " | Data: "
                  + Utils.bytesToHex(chr.getValue()));
        }

        synchronized (syncRoot) {
          Preprocessor preprocessor = preprocessorMap.get(chr.getUuid());
          if (preprocessor != null) {
            byte[] processedBytes = preprocessor.process(chr.getValue());
            if (processedBytes != null) {
              notificationRelay.accept(new Pair<>(chr.getUuid(), processedBytes));
            }
          } else {
            notificationRelay.accept(new Pair<>(chr.getUuid(), chr.getValue()));
          }
        }
      }

      @Override
      public void onCharacteristicRead(
          BluetoothGatt gatt, BluetoothGattCharacteristic chr, int status) {
        if (RxCentralLogger.isDebug()) {
          RxCentralLogger.debug("onCharacteristicRead - UUID: " + chr.getUuid() + " | Status: "
                  + status + " | Data: " + Utils.bytesToHex(chr.getValue()));
        }

        operationResult(
            readSubject,
            new Pair<>(chr.getUuid(), chr.getValue()),
            status,
            READ_CHARACTERISTIC_FAILED);
      }

      @Override
      public void onCharacteristicWrite(
          BluetoothGatt gatt, BluetoothGattCharacteristic chr, int status) {
        if (RxCentralLogger.isDebug()) {
          RxCentralLogger.debug("onCharacteristicWrite - UUID: " + chr.getUuid() + " | Status: "
                  + status + " | Data: " + Utils.bytesToHex(chr.getValue()));
        }

        operationResult(writeSubject, chr.getUuid(), status, WRITE_CHARACTERISTIC_FAILED);
      }

      @Override
      public void onDescriptorWrite(
          BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        if (RxCentralLogger.isDebug()) {
          RxCentralLogger.debug("onDescriptorWrite - UUID: "
                  + descriptor.getCharacteristic().getUuid() + " | Status: " + status);
        }

        if (descriptor.getUuid().equals(CCCD_UUID)) {
          operationResult(
              registerNotificationSubject,
              descriptor.getCharacteristic().getUuid(),
              status,
              PeripheralError.Code.WRITE_DESCRIPTOR_FAILED);
        }
      }

      @Override
      public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        if (RxCentralLogger.isDebug()) {
          RxCentralLogger.debug("onMtuChanged - Status: " + status + " | MTU: " + mtu);
        }

        if (status == 0) {
          CorePeripheral.this.mtu = mtu;
        }

        operationResult(requestMtuSubject, mtu, status, REQUEST_MTU_FAILED);
      }

      @Override
      public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        if (RxCentralLogger.isDebug()) {
          RxCentralLogger.debug("onReadRemoteRssi - Status: " + status + " | RSSI: " + rssi);
        }

        operationResult(readRssiSubject, rssi, status, READ_RSSI_FAILED);
      }

      @Override
      public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        if (RxCentralLogger.isDebug()) {
          RxCentralLogger.debug("onPhyUpdate - Status: " + status + " | txPHY: "
                  + txPhy + " | rxPHY: " + rxPhy);
        }
      }

      @Override
      public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        if (RxCentralLogger.isDebug()) {
          RxCentralLogger.debug("onPhyRead - Status: " + status + " | txPHY: "
                  + txPhy + " | rxPHY: " + rxPhy);
        }
      }

      private <T> void operationResult(
          @Nullable SingleSubject<T> operationSubject,
          T result,
          int status,
          PeripheralError.Code errorType) {
        synchronized (syncRoot) {
          if (currentOperation == operationSubject && operationSubject != null) {
            if (status == 0) {
              operationSubject.onSuccess(result);
            } else {
              operationSubject.onError(new PeripheralError(errorType, status));
            }
          } else if (currentOperation != null) {
            PeripheralError cause = new PeripheralError(PeripheralError.Code.OPERATION_RESULT_MISMATCH);
            currentOperation.onError(new PeripheralError(errorType, status, cause));
          }
        }
      }
    };
  }

  /** Implementation of Factory to produce Peripheral instances. */
  public static class Factory implements Peripheral.Factory {

    @Override
    public Peripheral produce(BluetoothDevice device, Context context) {
      return new CorePeripheral(device, context);
    }
  }
}
