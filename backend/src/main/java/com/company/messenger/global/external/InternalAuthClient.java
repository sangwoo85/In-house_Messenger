package com.company.messenger.global.external;

import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Calls the authoritative business APIs; cached messenger rows never validate a password. */
@Component
public class InternalAuthClient {
    private final WebClient client;
    private final ExternalAuthProperties properties;
    private final ExternalApiFields fields;

    /** Uses site-configured endpoints and JSON names. */
    @Autowired
    public InternalAuthClient(WebClient internalAuthWebClient, ExternalAuthProperties properties, ExternalApiFields fields) {
        this.client = internalAuthWebClient;
        this.properties = properties;
        this.fields = fields;
    }

    /** Compatibility constructor for isolated client tests. */
    public InternalAuthClient(WebClient client, ExternalAuthProperties properties) {
        this(client, properties, ExternalApiFields.defaults());
    }

    /** Validates credentials and returns the profile from the successful business login response. */
    public ExternalDirectoryUser login(String emprId, String password) {
        try {
            String idField = properties.legacyUserApi() ? "id" : fields.loginId();
            JsonNode response = client.post().uri(properties.authLoginPath()).contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(idField, emprId, fields.password(), password))
                    .retrieve().bodyToMono(JsonNode.class).block();
            // The demo login has no profile body. This adapter is never enabled implicitly in production.
            if (properties.legacyUserApi()) {
                return fetchUsers().stream().filter(user -> user.userId().equals(emprId)).findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            }
            JsonNode success = field(response, fields.loginSuccess());
            if (success != null && !success.isNull() && !success.asText().equals(fields.loginSuccessValue())) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }
            ExternalDirectoryUser profile = readUser(root(response, fields.loginRoot()), emprId);
            if (!emprId.equals(profile.userId())) throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            return profile;
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }
            throw unavailable();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    /** Fetches current external accounts for existence checks and user selection. */
    public List<ExternalDirectoryUser> fetchUsers() {
        try {
            JsonNode response = client.get().uri(properties.userListPath()).retrieve().bodyToMono(JsonNode.class).block();
            JsonNode array = root(response, fields.usersRoot());
            if (array == null || !array.isArray()) throw unavailable();
            List<ExternalDirectoryUser> users = new ArrayList<>();
            var ids = new HashSet<String>();
            for (JsonNode node : array) {
                ExternalDirectoryUser user = readUser(node, null);
                if (!ids.add(user.userId())) throw unavailable();
                users.add(user);
            }
            return List.copyOf(users);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    /** Reads the authoritative flat parent-child list; organization service validates its tree structure. */
    public List<ExternalDepartment> fetchDepartments() {
        try {
            JsonNode response = client.get().uri(properties.organizationListPath()).retrieve().bodyToMono(JsonNode.class).block();
            JsonNode array = root(response, fields.organizationsRoot());
            if (array == null || !array.isArray()) throw unavailable();
            List<ExternalDepartment> departments = new ArrayList<>();
            for (JsonNode node : array) departments.add(new ExternalDepartment(
                    required(node, fields.organizationId(), 100), optional(node, fields.organizationParentId(), 100),
                    required(node, fields.organizationName(), 100)));
            return List.copyOf(departments);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private ExternalDirectoryUser readUser(JsonNode node, String fallbackId) {
        if (node == null || !node.isObject()) throw unavailable();
        String id = optional(node, properties.legacyUserApi() ? "id" : fields.userId(), 50);
        if (id == null) id = fallbackId;
        if (id == null || id.isBlank()) throw unavailable();
        String department = optional(node, properties.legacyUserApi() ? "dept" : fields.departmentName(), 100);
        String departmentId = properties.legacyUserApi() ? department : optional(node, fields.departmentId(), 100);
        return new ExternalDirectoryUser(id, required(node, properties.legacyUserApi() ? "name" : fields.userName(), 50),
                null, department, optional(node, properties.legacyUserApi() ? "group" : fields.userGroup(), 100), departmentId);
    }

    private JsonNode root(JsonNode value, String path) {
        return path == null || path.isBlank() ? value : field(value, path);
    }

    private JsonNode field(JsonNode node, String path) {
        if (path == null || path.isBlank()) return null;
        for (String part : path.split("\\.")) {
            if (node == null) return null;
            node = node.get(part);
        }
        return node;
    }

    private String required(JsonNode node, String name, int maxLength) {
        String value = optional(node, name, maxLength);
        if (value == null) throw unavailable();
        return value;
    }

    private String optional(JsonNode node, String name, int maxLength) {
        JsonNode value = field(node, name);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() && !value.isIntegralNumber()) throw unavailable();
        String text = value.asText().trim();
        if (text.length() > maxLength) throw unavailable();
        return text.isEmpty() ? null : text;
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.EXTERNAL_AUTH_UNAVAILABLE);
    }

    /** Profile reference from the business API; arbitrary external image URLs are intentionally ignored. */
    public record ExternalDirectoryUser(String userId, String nickname, String profileImageUrl,
                                        String department, String userGroup, String departmentId) {
        /** Retains the original constructor for adapters that do not expose department IDs. */
        public ExternalDirectoryUser(String userId, String nickname, String profileImageUrl, String department, String userGroup) {
            this(userId, nickname, profileImageUrl, department, userGroup, null);
        }
    }

    /** An external organization node; a null parent identifies a root. */
    public record ExternalDepartment(String id, String parentId, String name) { }
}
