package com.wooya.usersdummy.init;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.wooya.usersdummy.entity.User;
import com.wooya.usersdummy.repository.UserRepository;

/**
 * 애플리케이션 실행 시 Users 테이블에 기본 더미 사용자를 생성하는 초기화 클래스입니다.
 */
@Component
public class DummyUserInitializer implements CommandLineRunner {

    private static final int DUMMY_USER_COUNT = 30;
    private static final String DEFAULT_PASSWORD = "1234";
    private static final List<String> REQUIRED_IDS = List.of("ksswy", "ksswy1", "ksswy2");
    private static final List<String> DEPTS = List.of("외환팀", "영업팀", "개발팀", "QA");
    private static final List<String> GROUPS = List.of("개발자", "사용자", "관리자");

    private final UserRepository userRepository;
    private final Random random = new Random();

    /**
     * @title 더미 사용자 초기화 객체를 생성한다.
     * @param userRepository 더미 사용자 조회와 저장에 사용되는 Repository
     */
    public DummyUserInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @title Users 테이블이 비어 있을 때 더미 사용자 30명을 저장한다.
     * @param args 애플리케이션 실행 인자로 사용되는 값
     */
    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        userRepository.saveAll(createDummyUsers());
    }

    /**
     * @title 필수 ID를 포함한 더미 사용자 30명을 생성한다.
     * @return 저장할 더미 사용자 목록
     */
    private List<User> createDummyUsers() {
        List<User> users = new ArrayList<>();
        Set<String> ids = new java.util.HashSet<>();

        for (String id : REQUIRED_IDS) {
            ids.add(id);
            users.add(createUser(id, users.size()));
        }

        while (users.size() < DUMMY_USER_COUNT) {
            String id = randomId();
            if (ids.add(id)) {
                users.add(createUser(id, users.size()));
            }
        }

        return users;
    }

    /**
     * @title 더미 사용자 한 명을 생성한다.
     * @param id 더미 사용자 ID로 사용되는 값
     * @param index 이름과 순환 필드 선택에 사용되는 순번
     * @return 생성된 더미 사용자 Entity
     */
    private User createUser(String id, int index) {
        return new User(
                id,
                DEFAULT_PASSWORD,
                "사용자" + (index + 1),
                DEPTS.get(index % DEPTS.size()),
                GROUPS.get(index % GROUPS.size()),
                id + "@wooya.com"
        );
    }

    /**
     * @title 6글자 이내의 랜덤 사용자 ID를 생성한다.
     * @return 랜덤 사용자 ID
     */
    private String randomId() {
        int number = random.nextInt(900_000) + 100_000;
        return Integer.toString(number, 36);
    }
}
