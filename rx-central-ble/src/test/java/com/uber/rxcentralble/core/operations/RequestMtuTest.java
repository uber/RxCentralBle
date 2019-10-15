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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.reactivex.observers.TestObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.SingleSubject;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RequestMtuTest {

  @Mock
  Peripheral peripheral;

  private final TestScheduler testScheduler = new TestScheduler();

  private SingleSubject<Integer> readOperationSingle = SingleSubject.create();
  private TestObserver<Integer> readResultTestObserver;
  private RequestMtu requestMtu;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    RxJavaPlugins.setComputationSchedulerHandler(schedulerCallable -> testScheduler);

    when(peripheral.requestMtu(anyInt())).thenReturn(readOperationSingle);

    requestMtu = new RequestMtu(100, 5000);
    readResultTestObserver = requestMtu.result().test();
  }

  @After
  public void after() {
    RxJavaPlugins.reset();
  }

  @Test
  public void read_timeout() {
    requestMtu.execute(peripheral);

    testScheduler.advanceTimeBy(5000 + 1000, TimeUnit.MILLISECONDS);

    readResultTestObserver.assertError(TimeoutException.class);
  }

  @Test
  public void read_error() {
    requestMtu.execute(peripheral);

    readOperationSingle.onError(new PeripheralError(PeripheralError.Code.MISSING_CHARACTERISTIC));

    readResultTestObserver.assertError(PeripheralError.class);
  }

  @Test
  public void read_success_verifyOnlyOnce() {
    requestMtu.execute(peripheral);

    int result = 100;
    readOperationSingle.onSuccess(result);

    readResultTestObserver.assertValue(result);

    TestObserver<Integer> invalidObserver = requestMtu.result().test();
    invalidObserver.assertNotComplete();

    verify(peripheral, times(1)).requestMtu(anyInt());
  }

  @Test
  public void read_execute_withResult_verifyOnlyOnce() {
    readResultTestObserver = requestMtu.executeWithResult(peripheral).test();

    int result = 100;
    readOperationSingle.onSuccess(result);

    readResultTestObserver.assertValue(result);

    TestObserver<Integer> invalidObserver = requestMtu.result().test();
    invalidObserver.assertNotComplete();

    verify(peripheral, times(1)).requestMtu(anyInt());
  }
}
