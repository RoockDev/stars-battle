package com.starsbattle.battles.config;

import com.starsbattle.battles.domain.AttackRoller;
import com.starsbattle.battles.domain.RandomAttackRoller;
import com.starsbattle.battles.domain.RandomSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Wires the pure, framework-free battle domain classes (PR8) into the
 * Spring context. {@link RandomSource} deliberately stays an injectable
 * seam (design: "Core Interfaces") rather than a hardcoded
 * {@code Math.random()} call inside {@link RandomAttackRoller} itself, so
 * tests can substitute a deterministic double.
 */
@Configuration
public class BattleDomainConfig {

    @Bean
    RandomSource randomSource() {
        return () -> ThreadLocalRandom.current().nextDouble();
    }

    @Bean
    AttackRoller attackRoller(RandomSource randomSource) {
        return new RandomAttackRoller(randomSource);
    }
}
