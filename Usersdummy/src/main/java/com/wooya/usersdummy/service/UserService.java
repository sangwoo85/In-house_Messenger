package com.wooya.usersdummy.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wooya.usersdummy.dto.UserResponse;
import com.wooya.usersdummy.repository.UserRepository;

/**
 * 사용자 조회와 로그인 검증 비즈니스 로직을 처리하는 Service입니다.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    /**
     * @title 사용자 Service를 생성한다.
     * @param userRepository 사용자 조회에 사용되는 Repository
     */
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @title 전체 사용자 목록을 조회해 API 응답 DTO 목록으로 변환한다.
     * @return 전체 사용자 응답 목록
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getUserList() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    /**
     * @title 사용자 ID와 비밀번호가 DB에 존재하는지 검증한다.
     * @param id 사용자 식별에 사용되는 ID
     * @param password 로그인 검증에 사용되는 비밀번호
     * @return 인증 정보가 일치하면 true, 그렇지 않으면 false
     */
    @Transactional(readOnly = true)
    public boolean login(String id, String password) {
        if (id == null || password == null) {
            return false;
        }
        return userRepository.findByIdAndPassword(id, password).isPresent();
    }
}
