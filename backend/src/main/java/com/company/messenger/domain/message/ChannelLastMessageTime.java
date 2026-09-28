package com.company.messenger.domain.message;

import java.time.LocalDateTime;

/** Carries a channel's latest original send time, including messages later deleted. */
public record ChannelLastMessageTime(Long channelId, LocalDateTime lastMessageAt) { }
