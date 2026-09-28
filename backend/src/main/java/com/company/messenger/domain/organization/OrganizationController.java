package com.company.messenger.domain.organization;

import com.company.messenger.global.auth.AuthenticatedUser;
import com.company.messenger.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only organization tree available to signed-in messenger users. */
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {
    private final OrganizationService organizations;

    /** Returns parent-child departments and coworkers, excluding the requesting user from selection. */
    @GetMapping
    public ApiResponse<OrganizationResponse> getDirectory(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(organizations.getDirectory(user.userId()));
    }
}
