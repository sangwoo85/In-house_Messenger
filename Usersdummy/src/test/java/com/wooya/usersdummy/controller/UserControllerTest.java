package com.wooya.usersdummy.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.wooya.usersdummy.dto.UserResponse;
import com.wooya.usersdummy.service.UserService;

/**
 * UserController의 HTTP 응답 상태와 JSON Body를 검증하는 테스트입니다.
 */
class UserControllerTest {

    private UserService userService;
    private MockMvc mockMvc;

    /**
     * @title Controller 단위 테스트에 필요한 MockMvc와 Mock Service를 준비한다.
     */
    @BeforeEach
    void setUp() {
        userService = org.mockito.Mockito.mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService)).build();
    }

    /**
     * @title 전체 사용자 조회 API가 사용자 목록과 group JSON 필드를 반환하는지 검증한다.
     * @throws Exception MockMvc 요청 처리 중 발생할 수 있는 예외
     */
    @Test
    void getUserListReturnsUsers() throws Exception {
        when(userService.getUserList()).thenReturn(List.of(
                new UserResponse("ksswy", "1234", "사용자1", "개발팀", "개발자", "ksswy@wooya.com")
        ));

        mockMvc.perform(get("/api/v1/getUserList"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("ksswy"))
                .andExpect(jsonPath("$[0].group").value("개발자"));
    }

    /**
     * @title 로그인 성공 시 200 상태와 성공 메시지를 반환하는지 검증한다.
     * @throws Exception MockMvc 요청 처리 중 발생할 수 있는 예외
     */
    @Test
    void loginReturnsOkWhenCredentialMatches() throws Exception {
        when(userService.login("ksswy", "1234")).thenReturn(true);

        mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"ksswy\",\"password\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("login success"));
    }

    /**
     * @title 로그인 실패 시 403 상태와 실패 메시지를 반환하는지 검증한다.
     * @throws Exception MockMvc 요청 처리 중 발생할 수 있는 예외
     */
    @Test
    void loginReturnsForbiddenWhenCredentialDoesNotMatch() throws Exception {
        when(userService.login("ksswy", "wrong")).thenReturn(false);

        mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"ksswy\",\"password\":\"wrong\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("login failed"));
    }
}
