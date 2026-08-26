package com.alibot.api.controller;

import com.alibot.api.dto.MasterResponse;
import com.alibot.api.dto.UserResponse;
import com.alibot.api.security.CurrentActor;
import com.alibot.domain.CommissionType;
import com.alibot.domain.Role;
import com.alibot.service.UserManagementService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** ТЗ п.5.1/9 — управление пользователями/мастерами, только SUPERADMIN (см. UserManagementService). */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserManagementService userManagementService;
    private final CurrentActor currentActor;

    @GetMapping("/api/v1/users")
    public List<UserResponse> list() {
        return userManagementService.list(currentActor.get()).stream().map(UserResponse::from).toList();
    }

    @PostMapping("/api/v1/users")
    public UserResponse createUser(@RequestBody CreateUserRequest req) {
        return UserResponse.from(
                userManagementService.createUser(req.telegramUserId(), req.name(), req.phone(), req.role(), currentActor.get()));
    }

    public record CreateUserRequest(long telegramUserId, String name, String phone, Role role) {
    }

    @PostMapping("/api/v1/users/{id}/master-profile")
    public MasterResponse createMasterProfile(@PathVariable UUID id, @RequestBody CreateMasterRequest req) {
        return MasterResponse.from(userManagementService.createMasterProfile(id, req.name(), req.phone(),
                req.commissionType(), req.commissionValue(), currentActor.get()));
    }

    public record CreateMasterRequest(String name, String phone, CommissionType commissionType, BigDecimal commissionValue) {
    }

    @PatchMapping("/api/v1/users/{id}/active")
    public UserResponse setActive(@PathVariable UUID id, @RequestBody ActiveRequest req) {
        return UserResponse.from(userManagementService.setActive(id, req.active(), currentActor.get()));
    }

    public record ActiveRequest(boolean active) {
    }

    @PatchMapping("/api/v1/users/{id}")
    public UserResponse updateProfile(@PathVariable UUID id, @RequestBody UpdateProfileRequest req) {
        return UserResponse.from(userManagementService.updateProfile(id, req.name(), req.phone(), currentActor.get()));
    }

    public record UpdateProfileRequest(String name, String phone) {
    }
}
