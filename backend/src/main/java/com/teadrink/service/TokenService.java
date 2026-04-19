package com.teadrink.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 登录态存储：优先使用 Redis；若 Redis 不可用则降级到 JVM 内存，保证开发期可用。
 * - key:  auth:token:{token}
 * - val:  userId（字符串）
 * - TTL:  app.auth.token-ttl-hours（默认 168h）
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final String KEY_PREFIX = "auth:token:";

    private final long ttlSeconds;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final class Entry {
        final long userId;
        final long expireAt;

        Entry(long userId, long expireAt) {
            this.userId = userId;
            this.expireAt = expireAt;
        }
    }

    private final java.util.Map<String, Entry> fallbackStore = new ConcurrentHashMap<>();

    public TokenService(@Value("${app.auth.token-ttl-hours:168}") int tokenTtlHours) {
        this.ttlSeconds = tokenTtlHours * 3600L;
    }

    public String createToken(long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            stringRedisTemplate.opsForValue()
                    .set(KEY_PREFIX + token, String.valueOf(userId), ttlSeconds, TimeUnit.SECONDS);
            log.info("[Redis] 写入登录 token, userId={}, ttl={}s", userId, ttlSeconds);
        } catch (Exception e) {
            log.warn("[Redis] 写入 token 失败，降级到内存: {}", e.getMessage());
            fallbackStore.put(token, new Entry(userId, System.currentTimeMillis() + ttlSeconds * 1000L));
        }
        return token;
    }

    public Optional<Long> validateAndGetUserId(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        try {
            String val = stringRedisTemplate.opsForValue().get(KEY_PREFIX + token);
            if (val != null && !val.isEmpty()) {
                try {
                    return Optional.of(Long.parseLong(val));
                } catch (NumberFormatException ignore) {
                    return Optional.empty();
                }
            }
        } catch (Exception e) {
            log.warn("[Redis] 校验 token 失败，降级到内存: {}", e.getMessage());
        }
        Entry e = fallbackStore.get(token);
        if (e == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() > e.expireAt) {
            fallbackStore.remove(token);
            return Optional.empty();
        }
        return Optional.of(e.userId);
    }

    public void removeToken(String token) {
        if (token == null) {
            return;
        }
        try {
            Boolean deleted = stringRedisTemplate.delete(KEY_PREFIX + token);
            log.info("[Redis] 删除 token, deleted={}", deleted);
        } catch (Exception e) {
            log.warn("[Redis] 删除 token 失败: {}", e.getMessage());
        }
        fallbackStore.remove(token);
    }
}
