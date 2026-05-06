package com.wooya.usersdummy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Users 테이블과 매핑되는 사용자 Entity입니다.
 */
@Entity
@Table(name = "Users")
public class User {

    @Id
    @Column(length = 6, nullable = false)
    private String id;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String dept;

    @Column(name = "`group`", nullable = false)
    private String userGroup;

    @Column(nullable = false)
    private String email;

    protected User() {
    }

    /**
     * @title 사용자 Entity를 생성한다.
     * @param id 사용자 식별에 사용되는 ID
     * @param password 로그인 검증에 사용되는 비밀번호
     * @param name 사용자 표시 이름으로 사용되는 값
     * @param dept 사용자 부서 정보로 사용되는 값
     * @param userGroup 사용자 권한 그룹 정보로 사용되는 값
     * @param email 사용자 이메일 주소로 사용되는 값
     */
    public User(String id, String password, String name, String dept, String userGroup, String email) {
        this.id = id;
        this.password = password;
        this.name = name;
        this.dept = dept;
        this.userGroup = userGroup;
        this.email = email;
    }

    /**
     * @title 사용자 ID를 반환한다.
     * @return 사용자 ID
     */
    public String getId() {
        return id;
    }

    /**
     * @title 사용자 비밀번호를 반환한다.
     * @return 사용자 비밀번호
     */
    public String getPassword() {
        return password;
    }

    /**
     * @title 사용자 이름을 반환한다.
     * @return 사용자 이름
     */
    public String getName() {
        return name;
    }

    /**
     * @title 사용자 부서를 반환한다.
     * @return 사용자 부서
     */
    public String getDept() {
        return dept;
    }

    /**
     * @title 사용자 권한 그룹을 반환한다.
     * @return 사용자 권한 그룹
     */
    public String getUserGroup() {
        return userGroup;
    }

    /**
     * @title 사용자 이메일을 반환한다.
     * @return 사용자 이메일
     */
    public String getEmail() {
        return email;
    }
}
