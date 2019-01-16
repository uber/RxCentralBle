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
package com.uber.rxcentralble.core.operations;

import android.support.v4.util.Pair;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.jakewharton.rxrelay2.Relay;
import com.uber.rxcentralble.GattIO;
import com.uber.rxcentralble.GattOperation;
import com.uber.rxcentralble.Optional;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.SingleTransformer;
import io.reactivex.subjects.ReplaySubject;

/**
 * Core abstract implementation of a characteristic write operation. Use the basic {@link Write}
 * operation if it's a simple write; extend this to listen for notifications or other means that a
 * peripheral may respond to the write that would conclude a completed operation.
 *
 * <p>This operation provides built-in chunking capabilities, where the given data is 'chunked' into
 * individual byte arrays of size determined by the GattIO maxWriteLength(). Each chunk is written
 * to the GattIO in a serial manner and the result Single completes after all chunks have been
 * written to the peripheral.
 *
 * @param <T> the type of result.
 */
public abstract class AbstractWrite<T> implements GattOperation<T> {

  private final BehaviorRelay<Integer> bytesSentRelay = BehaviorRelay.createDefault(0);

  private final Relay<Optional<GattIO>> gattRelay = BehaviorRelay.createDefault(Optional.empty());
  private final Single<T> writeSingle;

  public AbstractWrite(UUID svc, UUID chr, byte[] data, int timeoutMs) {
    writeSingle =
        write(svc, chr, data)
            .compose(postWrite())
            .toObservable()
            .share()
            .firstOrError()
            .timeout(timeoutMs, TimeUnit.MILLISECONDS);
  }

  @Override
  public Single<T> result() {
    return writeSingle;
  }

  @Override
  public void execute(GattIO gattIO) {
    gattRelay.accept(Optional.of(gattIO));
  }

  @Override
  public Single<T> executeWithResult(GattIO gattIO) {
    return writeSingle
            .doOnSubscribe(disposable -> execute(gattIO));
  }

  protected Single<GattIO> write(UUID svc, UUID chr, byte[] data) {
    return gattRelay
        .filter(Optional::isPresent)
        .map(Optional::get)
        .firstOrError()
        .doOnSuccess(g -> gattRelay.accept(Optional.empty()))
        .flatMapObservable(gattIO -> chunks(gattIO, data, gattIO.getMaxWriteLength()))
        .zipWith(bytesSentRelay, (gattChunk, bytesSent) -> gattChunk)
        .flatMapSingle(
            gattChunk ->
                gattChunk
                    .first
                    .write(svc, chr, gattChunk.second)
                    .doOnComplete(
                        () ->
                            bytesSentRelay.accept(
                                bytesSentRelay.getValue() + gattChunk.second.length))
                    .andThen(Single.just(gattChunk.first)))
        .lastOrError()
        .doOnSubscribe(d -> bytesSentRelay.accept(0));
  }

  protected abstract SingleTransformer<GattIO, T> postWrite();

  /**
   * Take a byte array and chunk it into sub-arrays of maxWriteLength in size. Emits each chunk on
   * the returned Observable once subscribed.
   *
   * @param gattIO gattIO to operate on.
   * @param data data to chunk.
   * @param maxWriteLength may size of a chunk.
   * @return Observable that emits each chunk, then completes.
   */
  protected Observable<Pair<GattIO, byte[]>> chunks(
      GattIO gattIO, byte[] data, int maxWriteLength) {

    ReplaySubject<Pair<GattIO, byte[]>> chunks = ReplaySubject.create();

    byte[] chunk = new byte[maxWriteLength];
    ByteBuffer byteBuffer = ByteBuffer.wrap(data);

    while (maxWriteLength <= byteBuffer.remaining()) {
      byteBuffer.get(chunk, 0, chunk.length);
      chunks.onNext(new Pair<>(gattIO, chunk));
    }

    if (byteBuffer.hasRemaining()) {
      byte[] remaining = new byte[byteBuffer.remaining()];
      byteBuffer.get(remaining, 0, byteBuffer.remaining());
      chunks.onNext(new Pair<>(gattIO, remaining));
    }

    chunks.onComplete();

    return chunks;
  }
}
