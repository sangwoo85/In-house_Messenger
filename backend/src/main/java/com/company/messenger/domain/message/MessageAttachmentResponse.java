package com.company.messenger.domain.message;

public record MessageAttachmentResponse(
        Long id,
        String originalName,
        String mimeType,
        long fileSize,
        String downloadUrl,
        boolean image
) {
}

