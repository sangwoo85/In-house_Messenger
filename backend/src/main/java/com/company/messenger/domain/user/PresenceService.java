package com.company.messenger.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final Duration PRESENCE_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    public void heartbeat(String userId, UserStatus status) {
        redisTemplate.opsForValue().set(key(userId), status.name(), PRESENCE_TTL);
    }

    public void markOffline(String userId) {
        redisTemplate.delete(key(userId));
    }

    public List<PresenceResponse> getPresence(List<String> userIds) {
        return userIds.stream()
                .map(userId -> new PresenceResponse(
                        userId,
                        getStatus(userId)
                ))
                .toList();
    }

    private UserStatus getStatus(String userId) {
        String value = redisTemplate.opsForValue().get(key(userId));
        return value != null ? UserStatus.valueOf(value) : UserStatus.OFFLINE;
    }

    public String key(String userId) {
        return "presence:" + userId;
    }
}
