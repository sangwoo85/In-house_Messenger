package com.wooya.usersdummy.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wooya.usersdummy.entity.User;

/**
 * User Entity의 JPA Repository입니다.
 */
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * @title 사용자 ID와 비밀번호가 모두 일치하는 사용자를 조회한다.
     * @param id 사용자 식별에 사용되는 ID
     * @param password 로그인 검증에 사용되는 비밀번호
     * @return 일치하는 사용자 Optional
     */
    Optional<User> findByIdAndPassword(String id, String password);
}
