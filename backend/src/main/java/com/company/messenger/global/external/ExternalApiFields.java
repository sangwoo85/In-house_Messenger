package com.company.messenger.global.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** JSON names and optional dot-separated response roots supplied by each business installation. */
@ConfigurationProperties(prefix = "app.external.fields")
public record ExternalApiFields(
        @DefaultValue("emprId") String loginId,
        @DefaultValue("password") String password,
        @DefaultValue("emprId") String userId,
        @DefaultValue("userName") String userName,
        @DefaultValue("departmentName") String departmentName,
        @DefaultValue("departmentId") String departmentId,
        @DefaultValue("userGroup") String userGroup,
        @DefaultValue("") String loginRoot,
        @DefaultValue("") String usersRoot,
        @DefaultValue("") String organizationsRoot,
        @DefaultValue("id") String organizationId,
        @DefaultValue("parentId") String organizationParentId,
        @DefaultValue("name") String organizationName,
        @DefaultValue("success") String loginSuccess,
        @DefaultValue("true") String loginSuccessValue
) {
    /** Standard business-system JSON contract, also used by direct unit-test construction. */
    public static ExternalApiFields defaults() {
        return new ExternalApiFields("emprId", "password", "emprId", "userName", "departmentName",
                "departmentId", "userGroup", "", "", "", "id", "parentId", "name", "success", "true");
    }
}
