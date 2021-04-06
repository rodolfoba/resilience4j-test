package com.github.rodolfoba;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.rodolfoba.ResilientExternalService.FALLBACK_CIRCUIT_BREAKER_NAME;
import static com.github.rodolfoba.ResilientExternalService.MAIN_CIRCUIT_BREAKER_NAME;
import static io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ResilientExternalServiceTest {

    CircuitBreakerFactory factory;
    ClientExecutor executor;
    ExternalService service;

    @BeforeEach
    public void beforeEach() {
        factory = new CircuitBreakerFactory() {

            private final CircuitBreakerRegistry registry;

            {
                var config = CircuitBreakerConfig.custom()
                        .slidingWindowSize(1)
                        .slidingWindowType(COUNT_BASED)
                        .automaticTransitionFromOpenToHalfOpenEnabled(false)
                        .build();

                registry = CircuitBreakerRegistry.of(config);
            }

            @Override
            public CircuitBreakerRegistry getRegistry() {
                return registry;
            }

        };
    }

    @Test
    public void closedCircuit() {
        // given
        executor = mock(ClientExecutor.class);
        when(executor.execute()).thenReturn(true);

        service = new ResilientExternalService(factory, executor);
        var circuitBreaker = factory.getRegistry().circuitBreaker(MAIN_CIRCUIT_BREAKER_NAME);
        circuitBreaker.transitionToClosedState();

        // when
        var result = service.call();

        // then
        assertTrue(result);
        verify(executor, times(1)).execute();
    }

    @Test
    public void openedCircuit() {
        // given
        executor = mock(ClientExecutor.class);
        when(executor.fallback()).thenReturn(false);

        service = new ResilientExternalService(factory, executor);
        var circuitBreaker = factory.getRegistry().circuitBreaker(MAIN_CIRCUIT_BREAKER_NAME);
        circuitBreaker.transitionToOpenState();

        // when
        var result = service.call();

        // then
        assertFalse(result);
        verify(executor, times(0)).execute();
    }

    @Test
    public void fallbackSuccess() {
        // given
        executor = mock(ClientExecutor.class);
        when(executor.execute()).thenThrow(new RuntimeException());
        when(executor.fallback()).thenReturn(false);
        service = new ResilientExternalService(factory, executor);

        // when
        var result = service.call();

        // then
        assertFalse(result);
        verify(executor, times(1)).fallback();
    }

    @Test
    public void fallbackFailure() {
        // given
        executor = mock(ClientExecutor.class);
        when(executor.execute()).thenThrow(new RuntimeException());
        when(executor.fallback()).thenThrow(new RuntimeException());
        service = new ResilientExternalService(factory, executor);

        // when
        var throwable = catchThrowable(() -> service.call());

        // then
        assertThat(throwable).isInstanceOf(RuntimeException.class);
        verify(executor, times(1)).execute();
        verify(executor, times(1)).fallback();
    }

    @Test
    public void fallbackWithOpenedCircuit() {
        // given
        executor = mock(ClientExecutor.class);
        when(executor.execute()).thenThrow(new RuntimeException());
        when(executor.fallback()).thenThrow(new RuntimeException());
        service = new ResilientExternalService(factory, executor);
        var mainClientExecutorCB = factory.getRegistry().circuitBreaker(MAIN_CIRCUIT_BREAKER_NAME);
        var fallbackClientExecutorCB = factory.getRegistry().circuitBreaker(FALLBACK_CIRCUIT_BREAKER_NAME);
        mainClientExecutorCB.transitionToOpenState();
        fallbackClientExecutorCB.transitionToOpenState();

        // when
        var throwable = catchThrowable(() -> service.call());

        // then
        assertThat(throwable).isExactlyInstanceOf(RuntimeException.class);
        verify(executor, times(0)).execute();
        verify(executor, times(0)).fallback();
    }

}
