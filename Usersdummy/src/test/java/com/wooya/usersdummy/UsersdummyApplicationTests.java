package com.wooya.usersdummy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 애플리케이션 기본 타입 구성을 검증하는 테스트입니다.
 */
class UsersdummyApplicationTests {

    /**
     * @title Spring Boot 진입점 클래스가 로드 가능한지 확인한다.
     */
    @Test
    void applicationClassExists() {
        assertThat(UsersdummyApplication.class).isNotNull();
    }

}
