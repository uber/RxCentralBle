package com.uber.rxcentralble;

import android.support.annotation.Nullable;

import java.util.UUID;

public interface ParsedAdvertisement {

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
   * Get the EIR data type value.
   *
   */
  @Nullable
  byte[] getEIRData(int eirDataType);

  /**
   * Get the raw advertisement data.
   * @return the raw advertisement data.
   */
  byte[] getRawAdvertisement();

  /**
   * Factory to produce ParsedAdvertisement implementations.
   */
  interface Factory {

    ParsedAdvertisement produce(byte[] rawAdData);
  }
}
