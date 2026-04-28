package com.company.messenger.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserId(String userId);

    @Query("""
            select u
            from User u
            where u.userId <> :userId
            order by u.nickname asc, u.userId asc
            """)
    List<User> findDirectory(String userId);
}
