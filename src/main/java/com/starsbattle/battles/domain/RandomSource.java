package com.starsbattle.battles.domain;

/**
 * Seam for injecting/mocking randomness (design: "Core Interfaces"). Must
 * return a uniform draw in {@code [0,1)}, matching the source's RNG
 * contract exactly, so tests can force deterministic tier boundaries.
 */
@FunctionalInterface
public interface RandomSource {

    double nextDouble();
}
