package com.wooya.usersdummy.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wooya.usersdummy.dto.LoginRequest;
import com.wooya.usersdummy.dto.LoginResponse;
import com.wooya.usersdummy.dto.UserResponse;
import com.wooya.usersdummy.service.UserService;

/**
 * 사용자 조회와 로그인 요청을 처리하는 REST Controller입니다.
 */
@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final UserService userService;

    /**
     * @title 사용자 API Controller를 생성한다.
     * @param userService 사용자 조회와 로그인 검증에 사용되는 Service
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * @title DB에 저장된 전체 사용자 목록을 조회한다.
     * @return 전체 사용자 응답 목록
     */
    @GetMapping("/getUserList")
    public List<UserResponse> getUserList() {
        return userService.getUserList();
    }

    /**
     * @title 사용자 ID와 비밀번호를 검증해 로그인 성공 또는 실패 응답을 반환한다.
     * @param request 로그인 검증에 사용되는 요청 Body
     * @return 인증 성공 시 200, 실패 시 403 응답
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        if (userService.login(request.id(), request.password())) {
            return ResponseEntity.ok(new LoginResponse("login success"));
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new LoginResponse("login failed"));
    }
}
