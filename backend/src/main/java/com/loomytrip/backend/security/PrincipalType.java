package com.loomytrip.backend.security;

/**
 * Distinguishes which table a JWT subject was issued against.
 * Travelers live in {@code users}; back-office operators live in {@code admin}.
 * Carrying this in the token lets {@link JwtAuthenticationFilter} load the
 * principal from the correct table even when an email exists in both.
 */
public enum PrincipalType {
    USER,
    ADMIN
}
