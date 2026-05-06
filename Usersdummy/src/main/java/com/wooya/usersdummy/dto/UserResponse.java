package com.wooya.usersdummy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wooya.usersdummy.entity.User;

/**
 * 사용자 목록 조회 API 응답 Body의 단일 사용자 정보를 표현하는 DTO입니다.
 *
 * @param id 사용자 ID
 * @param password 사용자 비밀번호
 * @param name 사용자 이름
 * @param dept 사용자 부서
 * @param userGroup 사용자 권한 그룹
 * @param email 사용자 이메일
 */
public record UserResponse(
        String id,
        String password,
        String name,
        String dept,
        @JsonProperty("group") String userGroup,
        String email
) {

    /**
     * @title User Entity를 API 응답 DTO로 변환한다.
     * @param user 응답 DTO 생성에 사용되는 사용자 Entity
     * @return 사용자 응답 DTO
     */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getPassword(),
                user.getName(),
                user.getDept(),
                user.getUserGroup(),
                user.getEmail()
        );
    }
}
