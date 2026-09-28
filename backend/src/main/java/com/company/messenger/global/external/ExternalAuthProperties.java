package com.company.messenger.global.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** External business APIs and the explicit adapter used only by the local Usersdummy service. */
@ConfigurationProperties(prefix = "app.external")
public record ExternalAuthProperties(
        String authBaseUrl,
        @DefaultValue("5") int authTimeoutSeconds,
        @DefaultValue("/api/v1/login") String authLoginPath,
        @DefaultValue("/api/v1/users") String userListPath,
        @DefaultValue("/api/v1/organizations") String organizationListPath,
        @DefaultValue("false") boolean legacyUserApi,
        @DefaultValue("300") int directoryRefreshSeconds
) {
    @ConstructorBinding
    public ExternalAuthProperties { }

    /** Compatibility constructor for callers that explicitly use the old Usersdummy contract. */
    public ExternalAuthProperties(String baseUrl, int timeout, String loginPath, String usersPath) {
        this(baseUrl, timeout, loginPath, usersPath, "/api/v1/organizations", true, 300);
    }
}
