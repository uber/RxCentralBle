# RxCentralBle [![Build Status](https://travis-ci.com/uber/RxCentralBle.svg?branch=master)](https://travis-ci.org/uber/RxCentralBle)

RxCentralBle is a reactive, interface-driven library used to integrate with Bluetooth LE peripherals.   

For those tired of writing eerily similar, yet subtly different code for every Bluetooth LE peripheral integration, RxCentralBle provides a standardized, simple reactive paradigm for connecting to and communicating with peripherals from the central role.

RxCentralBle avoids many known Android pitfalls, including more recently discovered limitations in Android 7 & 8 around long running scan operations.

Check out our detailed [Wiki](https://github.com/uber/RxCentralBle/wiki) for designs and examples for all the capabilities of RxCentralBle.

## Key Features

  - Reactive; subscribe to actions to trigger them, dispose to stop
  - Built-in operation queue; respects the serial nature of Android's BluetoothGatt
  - Built-in GATT write segmentation; all writes are automatically chunked into MTU-sized segments
  - Interface-driven; customize the library with your own implementations
  - Manager-based; two managers for all connectivity and communication

## Applicability

RxCentralBle optimizes for the following use cases:

  - Where the ability to connect to and communicate with a Bluetooth 4.0 LE peripheral is needed
  - Where the peripheral is Bluetooth 4.0 LE compliant and acts per the specification
  - Where the peripheral does not require Bluetooth 4.0 specified authentication
  - Where the peripheral is not an ultra-low power device

## Download

Available on Maven Central:

```gradle
dependencies {
  implementation 'com.uber.rxcentralble:rx-central-ble:1.2.0'
}
```

## Usage

The below demonstrates simple usage of RxCentralBle.  Check out the [Wiki](https://github.com/uber/RxCentralBle/wiki) for details!

### Bluetooth Detection

Use the BluetoothDetector to detect the state of Bluetooth:

```java
BluetoothDetector bluetoothDetector;
Disposable detection;

// Use the detector to detect Bluetooth state.
detection = bluetoothDetector
   .enabled()
   .subscribe(
       enabled -> {
         // Tell the user to turn on Bluetooth if not enabled
       }
   );
   
```

Dispose of your subscription to stop detection.  

```java
// Stop Bluetooth detection.
detection.dispose();
```

### Connection Management

Use the ConnectionManager to manage the lifecycle of connections to a peripheral and supply a fresh Peripheral to the PeripheralManager on every connection.

```java
ScanMatcher scanMatcher;
ConnectionManager connectionManager;
PeripheralManager peripheralManager;
Disposable connection

// Connect to a peripheral.  
connection = connectionManager
    .connect(scanMatcher, DEFAULT_SCAN_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT)
    .subscribe(
        peripheral -> {
          // Inject the latest connected Peripheral into your PeripheralManager.
          peripheralManager.setPeripheral(peripheral);
        },
        error -> {
          // Connection lost.
        };
```

Dispose of your subscription to disconnect.  

```java
// Disconnect.
connection.dispose();
```

Because this is a reactive library, you can leverage Rx retries to attempt to reconnect to a peripheral if a scan fails.

```java
ScanMatcher scanMatcher;
ConnectionManager connectionManager;
PeripheralManager peripheralManager;
Disposable connection

// Support retries for connection. 
connection = connectionManager
    .connect(scanMatcher, DEFAULT_SCAN_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT)
    .retryWhen(
        errorObservable -> 
            // Custom retry logic
    )
    .subscribe(
        peripheral -> {
          // Inject the latest connected peripheral into your PeripheralManager.
          peripheralManager.setPeripheral(peripheral);
        };
```

### Peripheral Management

After injecting the connected Peripheral into your PeripheralManager, you can then queue operations and the PeripheralManager will ensure these are executed in a serial FIFO fashion.  The PeripheralManager is thread safe, so multiple consuming threads can queue operations and they will be reliably executed in the order they are subscribed.

```java 
PeripheralManager peripheralManager;
Write write;
Disposable queued;

// Queue a write operaiton.
queued = peripheralManager
  .queueOperation(write))
  .subscribe(
       irrelevant -> {
         // Write was successful.
       },
       error -> {
         // Write failed.
       });
```

Dispose of your subscription to dequeue (i.e. cancel).  

```java       
// Cancel the write operation if it hasn't begun execution.
queued.dispose();
```

## Sample App

The included sample application allows you to connect to any Bluetooth LE peripheral by name and query the Generic Access, Device Information, and Battery services.  Feel free to improve upon the sample and submit PRs to help the RxCentralBle community.

## License

    Copyright (C) 2018 Uber Technologies

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

