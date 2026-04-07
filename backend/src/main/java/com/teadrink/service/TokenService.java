package com.teadrink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Token（演示用）；重启服务后需重新登录。生产可换 Redis / JWT。
 */
@Service
public class TokenService {

    private final long ttlMillis;

    private static final class Entry {
        final long userId;
        final long expireAt;

        Entry(long userId, long expireAt) {
            this.userId = userId;
            this.expireAt = expireAt;
        }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public TokenService(@Value("${app.auth.token-ttl-hours:168}") int tokenTtlHours) {
        this.ttlMillis = tokenTtlHours * 3600_000L;
    }

    public String createToken(long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        long expireAt = System.currentTimeMillis() + ttlMillis;
        store.put(token, new Entry(userId, expireAt));
        return token;
    }

    public Optional<Long> validateAndGetUserId(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        Entry e = store.get(token);
        if (e == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() > e.expireAt) {
            store.remove(token);
            return Optional.empty();
        }
        return Optional.of(e.userId);
    }

    public void removeToken(String token) {
        if (token != null) {
            store.remove(token);
        }
    }
}
