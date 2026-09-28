package com.company.messenger.domain.organization;

import org.springframework.data.jpa.repository.JpaRepository;

/** Stores the latest complete external directory without storing business-system credentials. */
public interface OrganizationSnapshotRepository extends JpaRepository<OrganizationSnapshot, Integer> { }
