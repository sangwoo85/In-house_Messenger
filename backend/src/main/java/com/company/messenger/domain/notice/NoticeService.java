package com.company.messenger.domain.notice;

import com.company.messenger.domain.user.PresenceService;
import com.company.messenger.domain.user.User;
import com.company.messenger.domain.user.UserRepository;
import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final UserRepository userRepository;
    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public NoticeResponse broadcast(BroadcastNoticeRequest request) {
        Notice notice = noticeRepository.save(Notice.create(request.title(), request.content(), request.sender()));
        NoticeResponse response = NoticeResponse.from(notice);
        messagingTemplate.convertAndSend("/topic/notice", response);
        return response;
    }

    @Transactional
    public UserNotificationResponse notifyUser(NotifyUserRequest request) {
        User user = userRepository.findByUserId(request.targetUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserNotification notification = userNotificationRepository.save(
                UserNotification.create(user, request.title(), request.content(), request.linkUrl())
        );
        UserNotificationResponse response = UserNotificationResponse.from(notification);

        if (presenceService.getPresence(java.util.List.of(user.getUserId())).getFirst().status() != com.company.messenger.domain.user.UserStatus.OFFLINE) {
            messagingTemplate.convertAndSendToUser(user.getUserId(), "/queue/notifications", response);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public UserNotificationPageResponse getNotifications(String userId, int page, int size) {
        Page<UserNotification> notifications = userNotificationRepository.findByUserUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(page, size)
        );
        return new UserNotificationPageResponse(
                notifications.getContent().stream().map(UserNotificationResponse::from).toList(),
                page,
                size,
                notifications.getTotalElements()
        );
    }

    @Transactional
    public void markNotificationRead(String userId, Long notificationId) {
        UserNotification notification = userNotificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }

        notification.markRead();
    }
}

