package com.starsbattle.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional per-endpoint override of the success envelope's {@code message}
 * field. When absent, {@code EnvelopeResponseBodyAdvice} falls back to the
 * default success message.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ApiMessage {

    String value();
}
