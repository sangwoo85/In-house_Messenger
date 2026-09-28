package com.company.messenger.domain.message;

import java.util.List;

/** Groups each emoji with its users so clients can show counts and the current selection. */
public record MessageReactionResponse(String emoji, List<String> userIds) { }
