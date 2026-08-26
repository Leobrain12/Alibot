package com.alibot.repository;

import com.alibot.domain.Role;
import com.alibot.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByTelegramUserId(Long telegramUserId);
    boolean existsByTelegramUserId(Long telegramUserId);
    List<User> findByRoleInAndActiveTrue(List<Role> roles);
}
