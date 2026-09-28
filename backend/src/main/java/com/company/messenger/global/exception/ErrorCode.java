package com.company.messenger.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    INVALID_SCHEDULE(HttpStatus.BAD_REQUEST, "SCHEDULE_001", "미래의 시작 시간, 시작 이후의 종료 시간, 장소와 알림 설정을 확인해 주세요."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_002", "일정 또는 알림을 찾을 수 없습니다."),
    SCHEDULE_MANAGEMENT_DENIED(HttpStatus.FORBIDDEN, "SCHEDULE_003", "등록한 사람만 일정을 변경할 수 있습니다."),
    INVALID_PROFILE_IMAGE(HttpStatus.BAD_REQUEST, "FILE_006", "프로필 사진은 5MB 이하, 최대 4096×4096 픽셀의 PNG 또는 JPEG 이미지만 사용할 수 있습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_001", "아이디 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_002", "유효하지 않은 토큰입니다."),
    INVALID_TOKEN_TYPE(HttpStatus.UNAUTHORIZED, "AUTH_003", "토큰 타입이 올바르지 않습니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH_004", "리프레시 토큰이 없습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_005", "리프레시 토큰이 일치하지 않습니다."),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_006", "세션이 만료되었습니다."),
    EXTERNAL_AUTH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_007", "사내 인증 서버에 연결할 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "사용자를 찾을 수 없습니다."),
    CHANNEL_NOT_FOUND(HttpStatus.NOT_FOUND, "CHANNEL_001", "채널을 찾을 수 없습니다."),
    CHANNEL_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CHANNEL_002", "채널에 접근할 수 없습니다."),
    INVALID_CHANNEL(HttpStatus.BAD_REQUEST, "CHANNEL_003", "채널 정보가 올바르지 않습니다."),
    CHANNEL_MANAGEMENT_DENIED(HttpStatus.FORBIDDEN, "CHANNEL_004", "채널 관리 권한이 없습니다."),
    MESSAGE_EDIT_DENIED(HttpStatus.FORBIDDEN, "MESSAGE_003", "전송한 메시지는 수정할 수 없습니다."),
    MESSAGE_ALREADY_READ(HttpStatus.CONFLICT, "MESSAGE_005", "상대방이 읽은 메시지는 삭제할 수 없습니다."),
    INVALID_REACTION(HttpStatus.BAD_REQUEST, "MESSAGE_006", "사용할 수 없는 이모티콘입니다."),
    MESSAGE_REACTION_DENIED(HttpStatus.FORBIDDEN, "MESSAGE_007", "삭제된 메시지에는 반응을 남길 수 없습니다."),
    MESSAGE_DELETE_DENIED(HttpStatus.FORBIDDEN, "MESSAGE_004", "본인의 메시지만 삭제할 수 있습니다."),
    CONFLICT(HttpStatus.CONFLICT, "COMMON_004", "다른 요청으로 정보가 변경되었습니다. 다시 시도하세요."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "MESSAGE_001", "메시지를 찾을 수 없습니다."),
    INVALID_MESSAGE(HttpStatus.BAD_REQUEST, "MESSAGE_002", "메시지 정보가 올바르지 않습니다."),
    INVALID_REPLY(HttpStatus.BAD_REQUEST, "MESSAGE_008", "답장할 원문이 삭제되었거나 이 대화방에서 찾을 수 없습니다."),
    INVALID_INTERNAL_API_KEY(HttpStatus.UNAUTHORIZED, "INTERNAL_001", "내부 API 키가 올바르지 않습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTICE_001", "알림을 찾을 수 없습니다."),
    NOTIFICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "NOTICE_002", "알림에 접근할 수 없습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "FILE_001", "파일을 찾을 수 없습니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "FILE_002", "파일 크기 제한을 초과했습니다."),
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "FILE_003", "빈 파일은 업로드할 수 없습니다."),
    FILE_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_004", "파일 저장에 실패했습니다."),
    FILE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "FILE_005", "파일에 접근할 수 없습니다."),
    INVALID_PAGE_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_003", "페이지 요청 범위가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_001", "인증이 필요합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
