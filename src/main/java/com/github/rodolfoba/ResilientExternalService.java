package com.github.rodolfoba;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.vavr.API;
import io.vavr.control.Try;

import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.Predicates.instanceOf;

public class ResilientExternalService implements ExternalService {

    public static final String MAIN_CIRCUIT_BREAKER_NAME = "MainClientExecutorCB";
    public static final String FALLBACK_CIRCUIT_BREAKER_NAME = "FallbackClientExecutorCB";

    private final ClientExecutor executor;
    private final CircuitBreaker mainCircuitBreaker;
    private final CircuitBreaker fallbackCircuitBreaker;

    public ResilientExternalService(CircuitBreakerFactory factory, ClientExecutor executor) {
        this.executor = executor;

        mainCircuitBreaker = factory.getRegistry().circuitBreaker(MAIN_CIRCUIT_BREAKER_NAME);
        mainCircuitBreaker.getEventPublisher().onStateTransition(
                event -> System.out.println(MAIN_CIRCUIT_BREAKER_NAME + " " + event.getStateTransition()));

        fallbackCircuitBreaker = factory.getRegistry().circuitBreaker(FALLBACK_CIRCUIT_BREAKER_NAME);
        fallbackCircuitBreaker.getEventPublisher().onStateTransition(
                event -> System.out.println(FALLBACK_CIRCUIT_BREAKER_NAME + " " + event.getStateTransition()));
    }

    @Override
    public boolean call() {
        var attempt = Try.of(() -> mainCircuitBreaker.executeCheckedSupplier(executor::execute))
//                .recover(throwable -> executor.fallback())
                .recover(throwable -> Try.of(() -> fallbackCircuitBreaker.executeCheckedSupplier(executor::fallback)).get())
                .mapFailure(Case($(instanceOf(CallNotPermittedException.class)), e -> new RuntimeException(e)))
        ;

        return attempt.get();
    }
}
