package com.company.messenger.domain.message;

import java.util.List;

public record MessageSliceResponse(
        List<MessageResponse> items,
        Long nextCursor,
        boolean hasNext
) {
}

