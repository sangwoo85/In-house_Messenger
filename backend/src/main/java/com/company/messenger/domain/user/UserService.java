package com.company.messenger.domain.user;

import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.exception.ErrorCode;
import com.company.messenger.global.external.InternalAuthClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final InternalAuthClient internalAuthClient;
    private final PresenceService presenceService;

    @Transactional
    public User findOrCreateByUserId(String userId) {
        Optional<InternalAuthClient.ExternalDirectoryUser> externalUser = findExternalUser(userId);

        return userRepository.findByUserId(userId)
                .map(user -> {
                    externalUser.ifPresent(profile -> user.syncProfile(profile.nickname(), profile.profileImageUrl()));
                    user.markLoggedIn();
                    return user;
                })
                .orElseGet(() -> userRepository.save(externalUser
                        .map(profile -> User.createLoggedIn(profile.userId(), profile.nickname(), profile.profileImageUrl()))
                        .orElseGet(() -> User.create(userId))));
    }

    @Transactional(readOnly = true)
    public User getByUserId(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public List<UserProfileResponse> getDirectory(String userId) {
        List<InternalAuthClient.ExternalDirectoryUser> externalUsers = safeFetchUsers().stream()
                .filter(externalUser -> !externalUser.userId().equals(userId))
                .toList();

        if (externalUsers.isEmpty()) {
            return userRepository.findDirectory(userId).stream()
                    .map(UserProfileResponse::from)
                    .toList();
        }

        Map<String, UserStatus> presenceByUserId = presenceService.getPresence(
                        externalUsers.stream().map(InternalAuthClient.ExternalDirectoryUser::userId).toList()
                ).stream()
                .collect(java.util.stream.Collectors.toMap(PresenceResponse::userId, PresenceResponse::status));

        Map<String, User> localUsersByUserId = userRepository.findAllByUserIdIn(
                        externalUsers.stream().map(InternalAuthClient.ExternalDirectoryUser::userId).toList()
                ).stream()
                .collect(java.util.stream.Collectors.toMap(User::getUserId, user -> user, (left, right) -> left, LinkedHashMap::new));

        for (InternalAuthClient.ExternalDirectoryUser externalUser : externalUsers) {
            User localUser = localUsersByUserId.get(externalUser.userId());
            if (localUser == null) {
                localUser = userRepository.save(User.createDirectoryUser(
                        externalUser.userId(),
                        externalUser.nickname(),
                        externalUser.profileImageUrl()
                ));
                localUsersByUserId.put(localUser.getUserId(), localUser);
                continue;
            }

            localUser.syncProfile(externalUser.nickname(), externalUser.profileImageUrl());
        }

        return externalUsers.stream()
                .map(externalUser -> {
                    User localUser = localUsersByUserId.get(externalUser.userId());
                    return new UserProfileResponse(
                            localUser.getId(),
                            localUser.getUserId(),
                            localUser.getNickname(),
                            localUser.getProfileImageUrl(),
                            presenceByUserId.getOrDefault(localUser.getUserId(), UserStatus.OFFLINE)
                    );
                })
                .toList();
    }

    @Transactional
    public User getOrCreateDirectoryUser(String userId) {
        Optional<InternalAuthClient.ExternalDirectoryUser> externalUser = findExternalUser(userId);

        return userRepository.findByUserId(userId)
                .map(user -> {
                    externalUser.ifPresent(profile -> user.syncProfile(profile.nickname(), profile.profileImageUrl()));
                    return user;
                })
                .orElseGet(() -> {
                    InternalAuthClient.ExternalDirectoryUser profile = externalUser
                            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                    return userRepository.save(User.createDirectoryUser(
                            profile.userId(),
                            profile.nickname(),
                            profile.profileImageUrl()
                    ));
                });
    }

    @Transactional
    public void markLoggedOut(String userId) {
        userRepository.findByUserId(userId).ifPresent(User::markLoggedOut);
    }

    private Optional<InternalAuthClient.ExternalDirectoryUser> findExternalUser(String userId) {
        return safeFetchUsers().stream()
                .filter(user -> user.userId().equals(userId))
                .findFirst();
    }

    private List<InternalAuthClient.ExternalDirectoryUser> safeFetchUsers() {
        List<InternalAuthClient.ExternalDirectoryUser> users = internalAuthClient.fetchUsers();
        return users != null ? users : List.of();
    }
}
