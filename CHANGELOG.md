Changelog
=========

Version 1.0.0
----------------------------

* Initial release

Version 1.0.1
----------------------------

* Initial release; rev'ed for Maven Central release

Version 1.0.2
----------------------------

* ScanMatcher now using ObservableTransformer

Version 1.0.3
----------------------------

* Resolve issues with v1.0.2
* Fix chunking logic in AbstractWrite

Version 1.0.4
----------------------------

* Scanner fixes for Android 7+
* Resolved MTU handling issues
* Implemented logging via RxCentralLogger

Version 1.0.5
----------------------------

* New RSSI and Service Scan Matchers

Version 1.0.6
----------------------------

* Resolve CoreBluetoothDetector unsubscribe crash on devices that don't support BLE
* Simplified core instance constructors

Version 1.0.7
----------------------------

* Rewind byte buffer in AbstractWrite to support reactive retry logic

Version 1.1.1
----------------------------

* Improved Scanning and Connectivity APIs
