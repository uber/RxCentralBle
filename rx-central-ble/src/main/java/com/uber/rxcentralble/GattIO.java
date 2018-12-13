package com.uber.rxcentralble;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.support.annotation.Nullable;

import java.util.UUID;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.annotations.SchedulerSupport;

/** Reactive interface into the underlying platform-level peripheral Bluetooth GATT operators. */
public interface GattIO {

  /** State of a connection procedure. */
  enum ConnectableState {
    CONNECTING,
    CONNECTED
  }

  /** CCCD UUID for notifications. */
  UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

  /** Default MTU for Bluetooth 4.0. */
  int DEFAULT_MTU = 20;

  /**
   * Connect to the underlying peripheral.
   *
   * <p>Connection is initiated when the returned Observable is subscribed to; disconnection
   * automatically occurs when there are no active subscribers, or on an error.
   *
   * @return a multi-casted Observable of ConnectableState, or else an error if connection fails.
   */
  Observable<ConnectableState> connect();

  /** Disconnect from the underlying peripheral. */
  void disconnect();

  /**
   * Observe the state of our connection to the GATT interface provided by the underlying
   * peripheral.
   *
   * @return Observable stream of connected state.
   */
  Observable<Boolean> connected();

  /**
   * Perform a GATT read operation upon subscription. Supports reactive Retry operators. Immediately
   * returns an error if disconnected.
   *
   * <dl>
   *   <dt><b>Scheduler:</b>
   *   <dd>{@code read} does not operate by default on a particular {@link Scheduler}.
   * </dl>
   *
   * @param svc the UUID of the GATT Service containing the desired Characteristic.
   * @param chr the UUID of the GATT Characteristic to read
   * @return Single of the raw byte array read from the Characteristic, or else an error. Expect
   *     {@link GattError} for errors that may be retried.
   */
  @SchedulerSupport(SchedulerSupport.NONE)
  Single<byte[]> read(UUID svc, UUID chr);

  /**
   * Perform a GATT write operation upon subscription. Supports reactive Retry operators.
   * Immediately returns an error if disconnected.
   *
   * <dl>
   *   <dt><b>Scheduler:</b>
   *   <dd>{@code write} does not operate by default on a particular {@link Scheduler}.
   * </dl>
   *
   * @param svc the UUID of the GATT Service containing the desired Characteristic.
   * @param chr the UUID of the GATT Characteristic to read
   * @param data raw data to write to the Characteristic.
   * @return Completable of the operation success, or else an error. Expect {@link GattError} for
   *     errors that may be retried.
   */
  @SchedulerSupport(SchedulerSupport.NONE)
  Completable write(UUID svc, UUID chr, byte[] data);

  /**
   * Register for characteristic notifications upon subscription. Supports reactive Retry operators.
   * Immediately returns an error if disconnected.
   *
   * <dl>
   *   <dt><b>Scheduler:</b>
   *   <dd>{@code registerNotification} does not operate by default on a particular {@link
   *       Scheduler}.
   * </dl>
   *
   * <p>Notification registration / de-registration is a serial operation separate of the
   * observation of those notifications. Notifications are received by observing notification().
   *
   * @param svc the UUID of the GATT Service containing the desired Characteristic.
   * @param chr the UUID of the GATT Characteristic to read
   * @return Completable of the operation success, or else an error. Expect {@link GattError} for
   *     errors that may be retried.
   */
  @SchedulerSupport(SchedulerSupport.NONE)
  Completable registerNotification(UUID svc, UUID chr);

  /**
   * Register for characteristic notifications upon subscription. Supports reactive Retry operators.
   * Immediately returns an error if disconnected.
   *
   * <p>Notification registration / de-registration is a serial operation separate of the
   * observation of those notifications. Notifications are received by observing notification();
   * these notifications will be pre-processed by the supplied Preprocessor.
   *
   * <dl>
   *   <dt><b>Scheduler:</b>
   *   <dd>{@code registerNotification} does not operate by default on a particular {@link
   *       Scheduler}.
   * </dl>
   *
   * @param svc the UUID of the GATT Service containing the desired Characteristic.
   * @param chr the UUID of the GATT Characteristic to read.
   * @param preprocessor the Preprocessor used to process the raw data returned by peripheral
   *     notifications.
   * @return Completable of the operation success, or else an error. Expect {@link GattError} for
   *     errors that may be retried.
   */
  @SchedulerSupport(SchedulerSupport.NONE)
  Completable registerNotification(UUID svc, UUID chr, @Nullable Preprocessor preprocessor);

  /**
   * Unregister from characteristic notifications upon subscription. Supports reactive Retry
   * operators. Immediately returns an error if disconnected.
   *
   * <p>Notification registration / de-registration is a serial operation separate of the
   * observation of those notifications. Notifications are received by observing notification();
   * these notifications will be pre-processed by the supplied Preprocessor.
   *
   * <dl>
   *   <dt><b>Scheduler:</b>
   *   <dd>{@code unregisterNotification} does not operate by default on a particular {@link
   *       Scheduler}.
   * </dl>
   *
   * @param svc the UUID of the GATT Service containing the desired Characteristic.
   * @param chr the UUID of the GATT Characteristic to read. notifications.
   * @return Completable of the operation success, or else an error. Expect {@link GattError} for
   *     errors that may be retried.
   */
  @SchedulerSupport(SchedulerSupport.NONE)
  Completable unregisterNotification(UUID svc, UUID chr);

  /**
   * Observe notifications for the specified characteristic.
   *
   * <dl>
   *   <dt><b>Scheduler:</b>
   *   <dd>Notifications are not emitted by default on a particular {@link Scheduler}.
   * </dl>
   *
   * @param chr the UUID of the GATT Characteristic to observe notifications from.
   * @return Observable of byte arrays from notifications. If a Preprocessor was specified at the
   *     time of registration, this will emit processed data. If none was specified, this will emit
   *     the raw bytes from each individual notification.
   */
  @SchedulerSupport(SchedulerSupport.NONE)
  Observable<byte[]> notification(UUID chr);

  /**
   * Request an MTU size from the peripheral. Supports reactive Retry operators. Immediately returns
   * an error if disconnected.
   *
   * <p>The MTU is the maximimum transmission unit. This represents the maximum size of a byte array
   * that may be written to a characteristic for the connected peripheral.
   *
   * <dl>
   *   <dt><b>Scheduler:</b>
   *   <dd>{@code requestMtu} does not operate by default on a particular {@link Scheduler}.
   * </dl>
   *
   * @param mtu desired MTU size.
   * @return Single of the actual MTU negotiated with the peripheral, or else an error. Expect
   *     {@link GattError} for errors that may be retried.
   */
  @TargetApi(21)
  @SchedulerSupport(SchedulerSupport.NONE)
  Single<Integer> requestMtu(int mtu);

  /**
   * Read the RSSI for the peripheral. RSSI is relative signal strength. Supports reactive Retry
   * operators. Immediately returns an error if disconnected.
   *
   * <dl>
   *   <dt><b>Scheduler:</b>
   *   <dd>{@code readRssi} does not operate by default on a particular {@link Scheduler}.
   * </dl>
   *
   * @return Single of the RSSI to the peripheral, or else an error. Expect {@link GattError} for
   *     errors that may be retried.
   */
  @SchedulerSupport(SchedulerSupport.NONE)
  Single<Integer> readRssi();

  /**
   * Synchronously return the current MTU.
   *
   * @return the maximum length of a write operation i.e. the MTU.
   */
  int getMaxWriteLength();

  /** A Preprocessor aggregates arrays of bytes for the purpose of demarcation. */
  interface Preprocessor {

    /**
     * Process the raw bytes and aggregate into a demarcated packet.
     *
     * @param bytes the raw data.
     * @return The aggregated, processed data, or else null if the
     *     aggregated data does not yet represent a complete demarcated packet.
     */
    @Nullable
    byte[] process(byte[] bytes);
  }

  /** Factory pattern to produce ConnectableGattIO instances. */
  interface Factory {

    /**
     * Produce a ConnectableGattIO instance given a BluetoothDevice and Context.
     *
     * @param device the BluetoothDevice representing a peripheral to connect to.
     * @param context Android context.
     * @return a ConnectableGattIO instance.
     */
    GattIO produce(BluetoothDevice device, Context context);
  }
}
