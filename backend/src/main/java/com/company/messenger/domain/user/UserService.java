package com.company.messenger.domain.user;

import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.exception.ErrorCode;
import com.company.messenger.global.external.InternalAuthClient;
import com.company.messenger.global.external.InternalAuthClient.ExternalDirectoryUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Stores messenger identity references while keeping account existence authoritative in the business API. */
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final InternalAuthClient internalAuthClient;
    private final PresenceService presenceService;

    /** Saves the exact profile returned by successful external authentication. */
    @Transactional
    public User loginWithProfile(ExternalDirectoryUser profile) {
        User user = sync(profile);
        user.markLoggedIn();
        return user;
    }

    /** Resolves an external identity and marks it online; never authenticates local credentials. */
    @Transactional
    public User findOrCreateByUserId(String userId) {
        User user = getOrCreateDirectoryUser(userId);
        user.markLoggedIn();
        return user;
    }

    /** Returns an existing local identity reference, without implying external account validity. */
    @Transactional(readOnly = true)
    public User getByUserId(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /** Reads the authoritative directory and overlays current Redis presence. */
    @Transactional
    public List<UserProfileResponse> getDirectory(String currentUserId) {
        return synchronizeDirectory(internalAuthClient.fetchUsers(), currentUserId);
    }

    /** Imports a validated API or cached organization snapshot; it is never used for authentication. */
    @Transactional
    public List<UserProfileResponse> synchronizeDirectory(List<ExternalDirectoryUser> directory, String currentUserId) {
        List<ExternalDirectoryUser> visible = directory.stream().filter(user -> !user.userId().equals(currentUserId)).toList();
        Map<String, UserStatus> statuses = presenceService.getPresence(visible.stream().map(ExternalDirectoryUser::userId).toList())
                .stream().collect(Collectors.toMap(PresenceResponse::userId, PresenceResponse::status));
        return visible.stream().map(this::sync)
                .map(user -> UserProfileResponse.from(user, statuses.getOrDefault(user.getUserId(), UserStatus.OFFLINE))).toList();
    }

    /** Revalidates account existence through the API, including during refresh-token renewal. */
    @Transactional
    public User getOrCreateDirectoryUser(String userId) {
        return resolveDirectoryUsers(List.of(userId)).getFirst();
    }

    /** Resolves a complete recipient list against one authoritative API response. */
    @Transactional
    public List<User> resolveDirectoryUsers(List<String> userIds) {
        var directory = internalAuthClient.fetchUsers().stream().collect(Collectors.toMap(
                ExternalDirectoryUser::userId, profile -> profile, (left, right) -> left));
        return userIds.stream().distinct().map(id -> {
            var profile = directory.get(id);
            if (profile == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            return sync(profile);
        }).toList();
    }

    private User sync(ExternalDirectoryUser profile) {
        User user = userRepository.findByUserId(profile.userId()).orElseGet(() -> userRepository.save(
                User.createDirectoryUser(profile.userId(), profile.nickname(), null, profile.department(), profile.userGroup())));
        user.syncProfile(profile.nickname(), null, profile.department(), profile.userGroup());
        user.setDepartmentId(profile.departmentId());
        return user;
    }

    /** Clears the persistent login flag after the session and heartbeat are revoked. */
    @Transactional
    public void markLoggedOut(String userId) {
        userRepository.findByUserId(userId).ifPresent(User::markLoggedOut);
    }
}
