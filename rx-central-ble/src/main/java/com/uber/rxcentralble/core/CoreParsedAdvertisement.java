package com.uber.rxcentralble.core;

import android.support.annotation.Nullable;

import com.uber.rxcentralble.ParsedAdvertisement;
import com.uber.rxcentralble.Utils;

import java.io.UnsupportedEncodingException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CoreParsedAdvertisement implements ParsedAdvertisement {

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
  private final Map<Integer, byte[]> eirDataMap = new HashMap<>();
  private final Map<Integer, byte[]> mfgDataMap = new HashMap<>();

  private final byte[] rawAdData;

  @Nullable
  String name;

  public CoreParsedAdvertisement(byte[] rawAdData) {
    this.rawAdData = rawAdData;

    ByteBuffer byteBuffer = ByteBuffer.wrap(rawAdData).order(ByteOrder.LITTLE_ENDIAN);
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
  @Nullable
  public byte[] getEIRData(int eirDataType) {
    return eirDataMap.get(eirDataType);
  }

  @Override
  public byte[] getRawAdvertisement() {
    return rawAdData;
  }

  private void parseAdData(int dataType, int length, ByteBuffer byteBuffer) {
    if (length <= 0) {
      return;
    }

    int position = byteBuffer.position();
    byte[] eirData = new byte[length];
    byteBuffer.get(eirData);
    eirDataMap.put(dataType, eirData);
    byteBuffer.position(position);

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

  public static class Factory implements ParsedAdvertisement.Factory {

    @Override
    public ParsedAdvertisement produce(byte[] rawAdData) {
      return new CoreParsedAdvertisement(rawAdData);
    }
  }
}
