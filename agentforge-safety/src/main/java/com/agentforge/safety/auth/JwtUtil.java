package com.agentforge.safety.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
  public class JwtUtil {

      @Value("${jwt.secret:}")
      private String secret;

      @Value("${jwt.expiration:86400000}")
      private long expiration; // 默认24小时

      private SecretKey getSigningKey() {
          return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
      }

      public String generateToken(Long userId, String username, String role) {
          return Jwts.builder()
                  .subject(username)
                  .claim("userId", userId)
                  .claim("role", role)
                  .issuedAt(new Date())
                  .expiration(new Date(System.currentTimeMillis() + expiration))
                  .signWith(getSigningKey())
                  .compact();
      }

      public Claims parseToken(String token) {
          return Jwts.parser()
                  .verifyWith(getSigningKey())
                  .build()
                  .parseSignedClaims(token)
                  .getPayload();
      }
  }