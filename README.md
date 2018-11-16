# RxCentralBle [![Build Status](https://travis-ci.org/uber/RxCentralBle.svg?branch=master)](https://travis-ci.org/uber/RxCentralBle)

RxCentralBle is a reactive, interface-driven library used to integrate with Bluetooth LE peripherals.   

For those tired of writing eerily similar, yet subtly different code for every Bluetooth LE peripheral integration, RxCentralBle provides a standardized, simple reactive paradigm for connecting to and communicating with peripherals from the central role.

Check out our detailed [Wiki](https://github.com/uber/RxCentralBle/wiki) for designs and examples for all the capabilities of RxCentralBle.

## Key Features

  - Reactive; observe actions to trigger them, dispose to stop. 
  - Built-in operation queue; respects the serial nature of Android's BluetoothGatt.
  - Interface-driven; customize the library with your own implementations
  - Manager-based; two managers for all connectivity and communication

## Applicability

RxCentralBle optimizes for the following use cases:

  - Where the ability to connect to and communicate with a Bluetooth LE peripheral is needed
  - Where the peripheral is Bluetooth 4.0 LE compliant and acts per the specification
  - Where the peripheral does not require Bluetooth 4.0 specified authentication
  - Where the peripheral is not an ultra-low power device

## Download

Once this is on maven central, use gradle to obtain the dependency:

```
dependencies {
  implementation 'com.uber:rx-central-ble:0.0.1'
}
```

## Usage

The below demonstrates simple usage of RxCentralBle.  Check out the [Wiki](https://github.com/uber/RxCentralBle/wiki) for details!

### Bluetooth Detection

Use the BluetoothDetector to detect the state of Bluetooth:

```
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

```
// Stop Bluetooth detection.
detection.dispose();
```

### Connection Management

Use the ConnectionManager to manage the lifecycle of connections to a peripheral and supply a fresh GattIO to the GattManager on every connection.

```
ScanMatcher scanMatcher;
ConnectionManager connectionManager;
GattManager gattManager;
Disposable connection

// Connect to a peripheral.  
connection = connectionManager
    .connect(scanMatcher, DEFAULT_SCAN_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT)
    .subscribe(
        gattIO -> {
          // Inject the latest connected GattIO into your GattManager.
          gattManager.setGattIO(gattIO);
        },
        error -> {
          // Connection lost.
        };
```

Dispose of your subscription to disconnect.  

```
// Disconnect.
connection.dispose();
```

### GATT Management

After injecting the latest connected GattIO into your GattManager, you can then queue operations and the GattManager will ensure these are executed in a serial FIFO fashion.  The GattManager is thread safe, so multiple consuming threads can queue operations and they will be reliably executed in the order they are subscribed.

``` 
GattManager gattManager;
Write write;
Disposable queued;

// Queue a write operaiton.
queued = gattManager
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

```       
// Cancel the write operation if it hasn't begun execution.
queued.dispose();
```

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

