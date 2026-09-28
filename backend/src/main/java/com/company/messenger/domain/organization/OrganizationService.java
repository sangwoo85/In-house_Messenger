package com.company.messenger.domain.organization;

import com.company.messenger.domain.user.UserService;
import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.exception.ErrorCode;
import com.company.messenger.global.external.ExternalAuthProperties;
import com.company.messenger.global.external.InternalAuthClient;
import com.company.messenger.global.external.InternalAuthClient.ExternalDepartment;
import com.company.messenger.global.external.InternalAuthClient.ExternalDirectoryUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/** Refreshes organization data on demand after its TTL, retaining a clearly marked snapshot during outages. */
@Service
@RequiredArgsConstructor
public class OrganizationService {
    private final OrganizationSnapshotRepository snapshots;
    private final InternalAuthClient external;
    private final ExternalAuthProperties properties;
    private final UserService users;
    private final ObjectMapper json;
    private LocalDateTime failedSnapshotTime;
    private LocalDateTime retryAfter = LocalDateTime.MIN;

    /** Returns a complete tree plus users; cached data is for browsing and never for account authentication. */
    @Transactional
    public synchronized OrganizationResponse getDirectory(String currentUserId) {
        OrganizationSnapshot snapshot = snapshots.findById(1).orElse(null);
        boolean expired = snapshot == null || snapshot.getSyncedAt().plusSeconds(Math.max(1, properties.directoryRefreshSeconds()))
                .isBefore(LocalDateTime.now());
        // A shared outage must not make each queued coworker wait through the same API timeout.
        boolean stale = expired && snapshot != null && snapshot.getSyncedAt().equals(failedSnapshotTime)
                && LocalDateTime.now().isBefore(retryAfter);
        if (expired && !stale) {
            try {
                List<ExternalDirectoryUser> accounts = external.fetchUsers();
                List<ExternalDepartment> departments = properties.legacyUserApi()
                        ? legacyDepartments(accounts) : external.fetchDepartments();
                validate(departments, accounts);
                snapshot = snapshots.save(new OrganizationSnapshot(json.writeValueAsString(new DirectoryData(departments, accounts))));
                failedSnapshotTime = null;
            } catch (BusinessException | JsonProcessingException exception) {
                if (snapshot == null) throw new BusinessException(ErrorCode.EXTERNAL_AUTH_UNAVAILABLE);
                failedSnapshotTime = snapshot.getSyncedAt();
                retryAfter = LocalDateTime.now().plusSeconds(30);
                stale = true;
            }
        }
        try {
            DirectoryData data = json.readValue(snapshot.getDataJson(), DirectoryData.class);
            return new OrganizationResponse(data.departments(), users.synchronizeDirectory(data.users(), currentUserId),
                    snapshot.getSyncedAt(), stale);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_AUTH_UNAVAILABLE);
        }
    }

    private List<ExternalDepartment> legacyDepartments(List<ExternalDirectoryUser> users) {
        // The local demo API has only department names; production always reads real parent IDs.
        return users.stream().map(ExternalDirectoryUser::department).filter(name -> name != null && !name.isBlank())
                .distinct().map(name -> new ExternalDepartment(name, null, name)).toList();
    }

    private void validate(List<ExternalDepartment> departments, List<ExternalDirectoryUser> accounts) {
        var byId = new HashMap<String, ExternalDepartment>();
        for (var department : departments) {
            if (byId.put(department.id(), department) != null) throw invalidDirectory();
        }
        for (var department : departments) {
            var visited = new HashSet<String>();
            ExternalDepartment current = department;
            while (current != null) {
                if (!visited.add(current.id()) || visited.size() > 100) throw invalidDirectory();
                String parent = current.parentId();
                if (parent != null && !byId.containsKey(parent)) throw invalidDirectory();
                current = parent == null ? null : byId.get(parent);
            }
        }
        for (var account : accounts) {
            if (account.departmentId() != null && !byId.containsKey(account.departmentId())) throw invalidDirectory();
        }
    }

    private BusinessException invalidDirectory() {
        return new BusinessException(ErrorCode.EXTERNAL_AUTH_UNAVAILABLE);
    }

    /** The persisted snapshot contains only API directory attributes, not live presence or image bytes. */
    public record DirectoryData(List<ExternalDepartment> departments, List<ExternalDirectoryUser> users) { }
}
