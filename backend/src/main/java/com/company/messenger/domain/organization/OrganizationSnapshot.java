package com.company.messenger.domain.organization;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One durable, validated business-directory snapshot for read-only organization browsing. */
@Entity
@Table(name = "organization_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationSnapshot {
    @Id
    private Integer id;
    @Lob
    @Column(name = "data_json", nullable = false, columnDefinition = "LONGTEXT")
    private String dataJson;
    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    /** Creates the singleton snapshot after the complete API response has been validated. */
    public OrganizationSnapshot(String dataJson) {
        this.id = 1;
        this.dataJson = dataJson;
        this.syncedAt = LocalDateTime.now();
    }
}
