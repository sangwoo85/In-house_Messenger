package com.company.messenger.domain.file;

/** Returns safe upload metadata and an authenticated download endpoint, never a filesystem path. */
public record FileUploadResponse(
        Long id,
        String originalName,
        String mimeType,
        long fileSize,
        String downloadUrl,
        boolean image
) {
    /** Maps persisted metadata to the attachment contract used by the message composer. */
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

