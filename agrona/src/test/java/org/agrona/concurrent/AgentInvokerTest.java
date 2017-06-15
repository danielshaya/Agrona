/*
 * Copyright 2014-2017 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.agrona.concurrent;

import org.agrona.ErrorHandler;
import org.agrona.LangUtil;
import org.agrona.concurrent.status.AtomicCounter;
import org.junit.Test;

import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

public class AgentInvokerTest
{
    private final ErrorHandler mockErrorHandler = mock(ErrorHandler.class);
    private final AtomicCounter mockAtomicCounter = mock(AtomicCounter.class);
    private final Agent mockAgent = mock(Agent.class);
    private final AgentInvoker invoker = new AgentInvoker(mockErrorHandler, mockAtomicCounter, mockAgent);

    @Test
    public void shouldReturnAgent()
    {
        assertThat(invoker.agent(), is(mockAgent));
    }

    @Test
    public void shouldNotDoWorkOnClosedRunnerButCallOnClose() throws Exception
    {
        invoker.close();
        invoker.invoke();

        verify(mockAgent, never()).onStart();
        verify(mockAgent, never()).doWork();
        verify(mockErrorHandler, never()).onError(any());
        verify(mockAtomicCounter, never()).increment();
        verify(mockAgent).onClose();
    }

    @Test
    public void shouldReportExceptionThrownByAgent() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(1);
        final RuntimeException expectedException = new RuntimeException();
        when(mockAgent.doWork()).thenThrow(expectedException);

        invoker.invoke();

        verify(mockAgent).onStart();
        verify(mockAgent).doWork();
        verify(mockErrorHandler).onError(expectedException);
        verify(mockAtomicCounter).increment();
        verify(mockAgent, never()).onClose();

        reset(mockAgent);
        invoker.invoke();
        verify(mockAgent, never()).onStart();
        verify(mockAgent).doWork();

        reset(mockAgent);
        invoker.close();

        verify(mockAgent, never()).onStart();
        verify(mockAgent, never()).doWork();
        verify(mockAgent).onClose();
    }

    @Test
    public void shouldNotReportClosedByInterruptException() throws Exception
    {
        when(mockAgent.doWork()).thenThrow(new ClosedByInterruptException());

        assertExceptionNotReported();
    }

    @Test
    public void shouldNotReportRethrownClosedByInterruptException() throws Exception
    {
        when(mockAgent.doWork()).thenAnswer(
            (inv) ->
            {
                try
                {
                    throw new ClosedByInterruptException();
                }
                catch (final ClosedByInterruptException ex)
                {
                    LangUtil.rethrowUnchecked(ex);
                }

                return null;
            });

        assertExceptionNotReported();
    }

    private void assertExceptionNotReported() throws InterruptedException
    {
        invoker.invoke();
        invoker.close();
        verify(mockAgent).onStart();
        verify(mockErrorHandler, never()).onError(any());
        verify(mockAtomicCounter, never()).increment();
    }
}