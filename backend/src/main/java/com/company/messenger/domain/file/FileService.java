package com.company.messenger.domain.file;

import com.company.messenger.domain.user.User;
import com.company.messenger.domain.user.UserRepository;
import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Stores attachment bytes outside the database and authorizes access through message sharing. */
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileAttachmentRepository fileAttachmentRepository;
    private final UserRepository userRepository;
    private final FileProperties fileProperties;
    private final com.company.messenger.domain.message.MessageRepository messageRepository;

    /**
     * Stores an authenticated upload under a generated name and records its owner.
     * @param userId authenticated employee ID
     * @param file uploaded attachment, bounded by the configured size limit
     * @return metadata used when the sender attaches the file to a message
     */
    @Transactional
    public FileUploadResponse upload(String userId, MultipartFile file) {
        validate(file);

        User uploader = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        try {
            Files.createDirectories(fileProperties.storageDirectory());
            String originalName = originalName(file);
            String extension = StringUtils.getFilenameExtension(originalName);
            String storedFileName = UUID.randomUUID() + (extension != null ? "." + extension : "");
            Path storedPath = fileProperties.storageDirectory().resolve(storedFileName);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, storedPath, StandardCopyOption.REPLACE_EXISTING);
            }

            FileAttachment saved = fileAttachmentRepository.save(FileAttachment.create(
                    originalName,
                    storedPath.toString(),
                    mimeType(file),
                    file.getSize(),
                    uploader
            ));

            return FileUploadResponse.from(saved);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    /**
     * Grants access only to the uploader or an active member of a channel sharing this file.
     * @param userId authenticated employee ID
     * @param fileId stored attachment ID
     * @return bytes with a safe download filename
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(String userId, Long fileId) {
        userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        FileAttachment fileAttachment = fileAttachmentRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

        if (!fileAttachment.getUploader().getUserId().equals(userId)
                && messageRepository.countAccessibleAttachment(fileId, userId) == 0) {
            throw new BusinessException(ErrorCode.FILE_ACCESS_DENIED);
        }

        Resource resource = new FileSystemResource(fileAttachment.getStoredPath());
        if (!resource.exists()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileAttachment.getOriginalName(), java.nio.charset.StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(parseMediaType(fileAttachment.getMimeType()))
                .body(resource);
    }

    /** Rejects empty or oversized files before writing any bytes. */
    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }

        long maxSize = isImage(file) ? fileProperties.imageMaxBytes() : fileProperties.otherMaxBytes();
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
    }

    /** Chooses the image limit; serving always remains behind authorization. */
    private boolean isImage(MultipartFile file) {
        return file.getContentType() != null && file.getContentType().startsWith("image/");
    }

    /** Removes path components from the client-supplied display filename. */
    private String originalName(MultipartFile file) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : ""
        ));
        return StringUtils.hasText(name) ? name : "file";
    }

    /** Normalizes the declared content type without using it as an authorization decision. */
    private String mimeType(MultipartFile file) {
        return parseMediaType(file.getContentType()).toString();
    }

    /** Treats missing or malformed MIME values as generic binary data. */
    private MediaType parseMediaType(String value) {
        try {
            return value != null ? MediaType.parseMediaType(value) : MediaType.APPLICATION_OCTET_STREAM;
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
