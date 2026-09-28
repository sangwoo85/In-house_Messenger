package com.company.messenger.domain.notice;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    org.springframework.data.domain.Page<Notice> findAllByOrderByCreatedAtDescIdDesc(org.springframework.data.domain.Pageable pageable);
}

