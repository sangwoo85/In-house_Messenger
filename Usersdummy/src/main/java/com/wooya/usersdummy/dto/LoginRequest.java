package com.wooya.usersdummy.dto;

/**
 * 로그인 API 요청 Body를 표현하는 DTO입니다.
 *
 * @param id 사용자 ID
 * @param password 사용자 비밀번호
 */
public record LoginRequest(String id, String password) {
}
