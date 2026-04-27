package com.company.messenger.global.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RedisSessionRegistry implements SessionRegistry {

    private final StringRedisTemplate redisTemplate;

    @Override
    public Optional<String> findSessionId(String userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    @Override
    public void save(String userId, String sessionId) {
        redisTemplate.opsForValue().set(key(userId), sessionId);
    }

    @Override
    public void delete(String userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(String userId) {
        return "session:" + userId;
    }
}
