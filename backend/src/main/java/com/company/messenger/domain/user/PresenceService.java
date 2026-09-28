package com.company.messenger.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final Duration PRESENCE_TTL = Duration.ofSeconds(90);

    private final StringRedisTemplate redisTemplate;
    private final com.company.messenger.config.RedisKeyspace keyspace;

    /** Keeps availability alive across normal twenty-second desktop heartbeat intervals. */
    public void heartbeat(String userId, UserStatus status) {
        redisTemplate.opsForValue().set(key(userId), status.name(), PRESENCE_TTL);
    }

    /** Removes availability immediately on explicit logout. */
    public void markOffline(String userId) {
        redisTemplate.delete(key(userId));
    }

    /** Returns OFFLINE after disconnects stop refreshing the ninety-second heartbeat. */
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

    /** Namespaces presence keys so they do not collide with business-system Redis data. */
    public String key(String userId) {
        return keyspace.key("presence:" + userId);
    }
}
