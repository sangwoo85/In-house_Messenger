package com.wooya.usersdummy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wooya.usersdummy.dto.UserResponse;
import com.wooya.usersdummy.entity.User;
import com.wooya.usersdummy.repository.UserRepository;

/**
 * UserService의 사용자 조회와 로그인 검증 로직을 검증하는 테스트입니다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    /**
     * @title Repository에서 조회한 사용자 목록이 응답 DTO로 변환되는지 검증한다.
     */
    @Test
    void getUserListReturnsAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(
                new User("ksswy", "1234", "사용자1", "개발팀", "개발자", "ksswy@wooya.com"),
                new User("ksswy1", "1234", "사용자2", "영업팀", "사용자", "ksswy1@wooya.com")
        ));

        List<UserResponse> users = userService.getUserList();

        assertThat(users).hasSize(2);
        assertThat(users.getFirst().id()).isEqualTo("ksswy");
        assertThat(users.getFirst().userGroup()).isEqualTo("개발자");
    }

    /**
     * @title ID와 비밀번호가 일치하는 사용자가 존재하면 로그인 검증이 성공하는지 확인한다.
     */
    @Test
    void loginReturnsTrueWhenUserExistsWithPassword() {
        when(userRepository.findByIdAndPassword("ksswy", "1234"))
                .thenReturn(Optional.of(new User("ksswy", "1234", "사용자1", "개발팀", "개발자", "ksswy@wooya.com")));

        assertThat(userService.login("ksswy", "1234")).isTrue();
    }

    /**
     * @title ID와 비밀번호가 일치하지 않으면 로그인 검증이 실패하는지 확인한다.
     */
    @Test
    void loginReturnsFalseWhenUserDoesNotMatch() {
        when(userRepository.findByIdAndPassword("ksswy", "wrong")).thenReturn(Optional.empty());

        assertThat(userService.login("ksswy", "wrong")).isFalse();
    }
}
