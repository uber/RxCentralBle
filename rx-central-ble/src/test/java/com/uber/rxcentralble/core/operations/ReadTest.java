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

import com.uber.rxcentralble.PeripheralError;
import com.uber.rxcentralble.Peripheral;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.reactivex.observers.TestObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.SingleSubject;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ReadTest {

  @Mock
  Peripheral peripheral;

  private final TestScheduler testScheduler = new TestScheduler();
  private final UUID svcUuid = UUID.randomUUID();
  private final UUID chrUuid = UUID.randomUUID();

  private SingleSubject<byte[]> readOperationSingle = SingleSubject.create();
  private TestObserver<byte[]> readResultTestObserver;
  private Read read;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    RxJavaPlugins.setComputationSchedulerHandler(schedulerCallable -> testScheduler);

    when(peripheral.read(any(), any())).thenReturn(readOperationSingle);

    read = new Read(svcUuid, chrUuid, 5000);
    readResultTestObserver = read.result().test();
  }

  @After
  public void after() {
    RxJavaPlugins.reset();
  }

  @Test
  public void read_timeout() {
    read.execute(peripheral);

    testScheduler.advanceTimeBy(5000 + 1000, TimeUnit.MILLISECONDS);

    readResultTestObserver.assertError(TimeoutException.class);
  }

  @Test
  public void read_error() {
    read.execute(peripheral);

    readOperationSingle.onError(new PeripheralError(PeripheralError.Code.MISSING_CHARACTERISTIC));

    readResultTestObserver.assertError(PeripheralError.class);
  }

  @Test
  public void read_success_verifyOnlyOnce() {
    read.execute(peripheral);

    byte[] result = new byte[] {0x00};
    readOperationSingle.onSuccess(result);

    readResultTestObserver.assertValue(result);

    TestObserver<byte[]> invalidObserver = read.result().test();
    invalidObserver.assertNotComplete();

    verify(peripheral, times(1)).read(any(), any());
  }

  @Test
  public void read_execute_WithResult_verifyOnlyOnce() {
    readResultTestObserver = read.executeWithResult(peripheral).test();

    byte[] result = new byte[] {0x00};
    readOperationSingle.onSuccess(result);

    readResultTestObserver.assertValue(result);

    TestObserver<byte[]> invalidObserver = read.result().test();
    invalidObserver.assertNotComplete();

    verify(peripheral, times(1)).read(any(), any());
  }
}
