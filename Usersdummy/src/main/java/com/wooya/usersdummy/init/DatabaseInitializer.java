package com.wooya.usersdummy.init;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Spring datasource 연결 전에 DummyUsers Database를 생성하는 초기화 클래스입니다.
 */
public class DatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /**
     * @title 애플리케이션 환경 설정을 읽어 필요한 Database를 생성한다.
     * @param applicationContext 초기화 대상 Spring ApplicationContext로 사용되는 값
     */
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();

        String bootstrapUrl = environment.getProperty(
                "dummy-users.database.bootstrap-url",
                "jdbc:mysql://localhost:3306/mysql?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
        );
        String username = environment.getProperty("spring.datasource.username", "root");
        String password = environment.getProperty("spring.datasource.password", "");
        String databaseName = environment.getProperty("dummy-users.database.name", "DummyUsers");

        createDatabaseIfNeeded(bootstrapUrl, username, password, databaseName);
    }

    /**
     * @title 지정된 MySQL Database가 없으면 생성한다.
     * @param bootstrapUrl Database 생성 전 MySQL 기본 DB 연결에 사용되는 JDBC URL
     * @param username MySQL 연결에 사용되는 사용자명
     * @param password MySQL 연결에 사용되는 비밀번호
     * @param databaseName 생성 대상 Database 이름으로 사용되는 값
     */
    private void createDatabaseIfNeeded(String bootstrapUrl, String username, String password, String databaseName) {
        String sql = "CREATE DATABASE IF NOT EXISTS `%s` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
                .formatted(databaseName);

        try (Connection connection = DriverManager.getConnection(bootstrapUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to initialize database: " + databaseName, ex);
        }
    }
}
