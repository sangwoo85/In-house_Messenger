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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileAttachmentRepository fileAttachmentRepository;
    private final UserRepository userRepository;
    private final FileProperties fileProperties;

    @Transactional
    public FileUploadResponse upload(String userId, MultipartFile file) {
        validate(file);

        User uploader = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        try {
            Files.createDirectories(fileProperties.storageDirectory());
            String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
            String storedFileName = UUID.randomUUID() + (extension != null ? "." + extension : "");
            Path storedPath = fileProperties.storageDirectory().resolve(storedFileName);
            Files.copy(file.getInputStream(), storedPath, StandardCopyOption.REPLACE_EXISTING);

            FileAttachment saved = fileAttachmentRepository.save(FileAttachment.create(
                    file.getOriginalFilename(),
                    storedPath.toString(),
                    file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    file.getSize(),
                    uploader
            ));

            return FileUploadResponse.from(saved);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(String userId, Long fileId) {
        userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        FileAttachment fileAttachment = fileAttachmentRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

        Resource resource = new FileSystemResource(fileAttachment.getStoredPath());
        if (!resource.exists()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(fileAttachment.getOriginalName())
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(fileAttachment.getMimeType()))
                .body(resource);
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }

        long maxSize = isImage(file) ? fileProperties.imageMaxBytes() : fileProperties.otherMaxBytes();
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
    }

    private boolean isImage(MultipartFile file) {
        return file.getContentType() != null && file.getContentType().startsWith("image/");
    }
}

