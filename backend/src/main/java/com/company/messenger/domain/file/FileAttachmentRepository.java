package com.company.messenger.domain.file;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persists attachment metadata; authorization is enforced by the file service. */
public interface FileAttachmentRepository extends JpaRepository<FileAttachment, Long> {
}

