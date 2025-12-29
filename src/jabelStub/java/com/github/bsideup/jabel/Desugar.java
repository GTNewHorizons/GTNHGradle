package com.github.bsideup.jabel;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

/**
 * Stub annotation for Jabel compatibility.
 * When using JVM Downgrader mode instead of Jabel, this stub allows code
 * that uses @Desugar to compile without requiring Jabel on the classpath.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(value = TYPE)
public @interface Desugar {}
