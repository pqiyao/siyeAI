package com.example.sillyspringboot.admin.security;

import com.example.sillyspringboot.admin.config.RuoYiAdminProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class RuoYiAdminJwtService {

    private final RuoYiAdminProperties props;

    public RuoYiAdminJwtService(RuoYiAdminProperties props) {
        this.props = props;
    }

    public String createToken(String username) {
        long now = System.currentTimeMillis();
        long expMs = Math.max(1, props.getJwtExpireHours()) * 3600_000L;
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expMs))
                .signWith(signingKey())
                .compact();
    }

    public String parseUsername(String token) {
        Claims claims =
                Jwts.parser()
                        .verifyWith(signingKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
        return claims.getSubject();
    }

    private SecretKey signingKey() {
        byte[] bytes = props.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, Math.min(bytes.length, 32));
            bytes = padded;
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
