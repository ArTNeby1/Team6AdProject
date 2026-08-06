package com.loomytrip.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.loomytrip.backend.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    /** Claim name that records whether the subject is a traveler or an admin. */
    private static final String CLAIM_TYPE = "typ";

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** Issues a token for a traveler account (the {@code users} table). */
    public String generateToken(Long userId, String email) {
        return buildToken(userId, email, PrincipalType.USER);
    }

    /** Issues a token for a back-office admin account (the {@code admin} table). */
    public String generateAdminToken(Long adminId, String email) {
        return buildToken(adminId, email, PrincipalType.ADMIN);
    }

    private String buildToken(Long subjectId, String email, PrincipalType type) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.expirationMs());
        return Jwts.builder()
                .subject(String.valueOf(subjectId))
                .claim("email", email)
                .claim(CLAIM_TYPE, type.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public Long extractUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    /**
     * Reads the principal type from the token. Tokens issued before this claim
     * existed default to {@link PrincipalType#USER} so old sessions keep working.
     */
    public PrincipalType extractType(String token) {
        String raw = parseClaims(token).get(CLAIM_TYPE, String.class);
        if (raw == null) {
            return PrincipalType.USER;
        }
        try {
            return PrincipalType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return PrincipalType.USER;
        }
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception ex) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
