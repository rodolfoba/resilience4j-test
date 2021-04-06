package com.github.rodolfoba;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

interface CircuitBreakerFactory {

    CircuitBreakerRegistry getRegistry();

}
