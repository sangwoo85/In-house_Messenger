package com.company.messenger.domain.organization;

import com.company.messenger.domain.user.UserProfileResponse;
import com.company.messenger.global.external.InternalAuthClient.ExternalDepartment;

import java.time.LocalDateTime;
import java.util.List;

/** The UI builds its tree from parent IDs and explicitly labels stale snapshots during API outages. */
public record OrganizationResponse(List<ExternalDepartment> departments, List<UserProfileResponse> users,
                                   LocalDateTime syncedAt, boolean stale) { }
