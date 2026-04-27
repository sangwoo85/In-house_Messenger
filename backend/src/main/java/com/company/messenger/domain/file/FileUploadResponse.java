package com.company.messenger.domain.file;

public record FileUploadResponse(
        Long id,
        String originalName,
        String mimeType,
        long fileSize,
        String downloadUrl,
        boolean image
) {
    public static FileUploadResponse from(FileAttachment fileAttachment) {
        return new FileUploadResponse(
                fileAttachment.getId(),
                fileAttachment.getOriginalName(),
                fileAttachment.getMimeType(),
                fileAttachment.getFileSize(),
                "/api/v1/files/" + fileAttachment.getId(),
                fileAttachment.getMimeType().startsWith("image/")
        );
    }
}

