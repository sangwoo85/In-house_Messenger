package com.company.messenger.domain.user;

import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User findOrCreateByUserId(String userId) {
        return userRepository.findByUserId(userId)
                .map(user -> {
                    user.markLoggedIn();
                    return user;
                })
                .orElseGet(() -> userRepository.save(User.create(userId)));
    }

    @Transactional(readOnly = true)
    public User getByUserId(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> getDirectory(String userId) {
        return userRepository.findDirectory(userId).stream()
                .map(UserProfileResponse::from)
                .toList();
    }

    @Transactional
    public void markLoggedOut(String userId) {
        userRepository.findByUserId(userId).ifPresent(User::markLoggedOut);
    }
}
