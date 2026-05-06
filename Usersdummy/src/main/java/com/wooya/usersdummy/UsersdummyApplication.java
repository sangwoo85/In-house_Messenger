package com.wooya.usersdummy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.wooya.usersdummy.init.DatabaseInitializer;

/**
 * Dummy Users API 애플리케이션의 Spring Boot 진입점입니다.
 */
@SpringBootApplication
public class UsersdummyApplication {

    /**
     * @title 애플리케이션 실행 전에 MySQL Database 초기화 로직을 등록하고 Spring Boot를 시작한다.
     * @param args 애플리케이션 실행 인자로 사용되는 값
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(UsersdummyApplication.class);
        application.addInitializers(new DatabaseInitializer());
        application.run(args);
    }

}
