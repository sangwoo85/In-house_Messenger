package com.company.messenger.domain.file;

import com.company.messenger.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_path", nullable = false, length = 500)
    private String storedPath;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private FileAttachment(
            String originalName,
            String storedPath,
            String mimeType,
            long fileSize,
            User uploader,
            LocalDateTime createdAt
    ) {
        this.originalName = originalName;
        this.storedPath = storedPath;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.uploader = uploader;
        this.createdAt = createdAt;
    }

    public static FileAttachment create(
            String originalName,
            String storedPath,
            String mimeType,
            long fileSize,
            User uploader
    ) {
        return FileAttachment.builder()
                .originalName(originalName)
                .storedPath(storedPath)
                .mimeType(mimeType)
                .fileSize(fileSize)
                .uploader(uploader)
                .createdAt(LocalDateTime.now())
                .build();
    }
}

