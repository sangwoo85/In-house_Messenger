package com.company.messenger.domain.message;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UnreadCountService {

    private final StringRedisTemplate redisTemplate;

    public void increment(Long channelId, String userId) {
        redisTemplate.opsForValue().increment(key(channelId, userId));
    }

    public void reset(Long channelId, String userId) {
        redisTemplate.opsForValue().set(key(channelId, userId), "0");
    }

    public long get(Long channelId, String userId) {
        String value = redisTemplate.opsForValue().get(key(channelId, userId));
        return value != null ? Long.parseLong(value) : 0L;
    }

    public String key(Long channelId, String userId) {
        return "unread:" + channelId + ":" + userId;
    }
}
