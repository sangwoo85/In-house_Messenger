package com.wooya.usersdummy.dto;

/**
 * 로그인 API 응답 Body를 표현하는 DTO입니다.
 *
 * @param message 로그인 처리 결과 메시지
 */
public record LoginResponse(String message) {
}
