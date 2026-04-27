package com.company.messenger.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_001", "아이디 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_002", "유효하지 않은 토큰입니다."),
    INVALID_TOKEN_TYPE(HttpStatus.UNAUTHORIZED, "AUTH_003", "토큰 타입이 올바르지 않습니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH_004", "리프레시 토큰이 없습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_005", "리프레시 토큰이 일치하지 않습니다."),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_006", "세션이 만료되었습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "사용자를 찾을 수 없습니다."),
    CHANNEL_NOT_FOUND(HttpStatus.NOT_FOUND, "CHANNEL_001", "채널을 찾을 수 없습니다."),
    CHANNEL_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CHANNEL_002", "채널에 접근할 수 없습니다."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "MESSAGE_001", "메시지를 찾을 수 없습니다."),
    INVALID_INTERNAL_API_KEY(HttpStatus.UNAUTHORIZED, "INTERNAL_001", "내부 API 키가 올바르지 않습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTICE_001", "알림을 찾을 수 없습니다."),
    NOTIFICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "NOTICE_002", "알림에 접근할 수 없습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "FILE_001", "파일을 찾을 수 없습니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "FILE_002", "파일 크기 제한을 초과했습니다."),
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "FILE_003", "빈 파일은 업로드할 수 없습니다."),
    FILE_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_004", "파일 저장에 실패했습니다."),
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
