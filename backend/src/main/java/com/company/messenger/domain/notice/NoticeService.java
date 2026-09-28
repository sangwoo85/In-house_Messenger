package com.company.messenger.domain.notice;

import com.company.messenger.domain.user.User;
import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final com.company.messenger.global.response.RealtimeEvents events;
    private final com.company.messenger.domain.user.UserService userService;

    /** Persists the notice before publishing its chosen delivery mode after commit. */
    @Transactional
    public NoticeResponse broadcast(BroadcastNoticeRequest request) {
        Notice notice = noticeRepository.save(Notice.create(request.title(), request.content(), request.sender(),
                request.notificationType(), request.displayMode()));
        NoticeResponse response = NoticeResponse.from(notice);
        events.topic("/topic/notice", response);
        return response;
    }

    /** Resolves the recipient through the business API and stores every display mode. */
    @Transactional
    public UserNotificationResponse notifyUser(NotifyUserRequest request) {
        User user = userService.getOrCreateDirectoryUser(request.targetUserId());
        UserNotification notification = userNotificationRepository.save(
                UserNotification.create(user, request.title(), request.content(), request.linkUrl(),
                        request.notificationType(), request.displayMode())
        );
        UserNotificationResponse response = UserNotificationResponse.from(notification);

        events.user(user.getUserId(), "/queue/notifications", response);

        return response;
    }

    /** Returns this user's history and total unread count, including silent notifications. */
    @Transactional(readOnly = true)
    public UserNotificationPageResponse getNotifications(String userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }
        Page<UserNotification> notifications = userNotificationRepository.findByUserUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(page, size)
        );
        return new UserNotificationPageResponse(
                notifications.getContent().stream().map(UserNotificationResponse::from).toList(),
                page,
                size,
                notifications.getTotalElements(),
                userNotificationRepository.countByUserUserIdAndReadFalse(userId)
        );
    }

    /** Retrieves persisted notices with bounded pagination. */
    @Transactional(readOnly = true)
    public NoticePageResponse getNotices(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        var rows = noticeRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, size));
        return new NoticePageResponse(rows.getContent().stream().map(NoticeResponse::from).toList(), page, size, rows.getTotalElements());
    }

    /** Allows only the owning user to mark a notification as read. */
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
