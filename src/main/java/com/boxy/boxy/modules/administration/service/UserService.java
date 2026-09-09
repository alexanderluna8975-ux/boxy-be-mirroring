package com.boxy.boxy.modules.administration.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.administration.dto.CreateUserRequest;
import com.boxy.boxy.modules.administration.dto.UserBranchAssignmentDto;
import com.boxy.boxy.modules.administration.dto.UserDto;
import com.boxy.boxy.modules.administration.entity.Branch;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.entity.Role;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.entity.UserBranchRole;
import com.boxy.boxy.modules.administration.repository.BranchRepository;
import com.boxy.boxy.modules.administration.repository.CompanyRepository;
import com.boxy.boxy.modules.administration.repository.RoleRepository;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return userRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(Long id) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return toDto(user);
    }

    @Transactional
    public UserDto createUser(CreateUserRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        String username = request.getUsername();
        if (username == null || username.isBlank()) {
            username = request.getEmail().split("@")[0];
        }

        String rawPassword = request.getPassword();
        if (rawPassword == null || rawPassword.isBlank()) {
            rawPassword = "Password#2026!Secured$";
        }

        String firstName = request.getFirstName();
        String lastName = request.getLastName();
        if (firstName == null || firstName.isBlank()) {
            firstName = username;
        }
        if (lastName == null || lastName.isBlank()) {
            lastName = "Boxy";
        }

        if (userRepository.findByUsernameAndDeletedAtIsNull(username).isPresent()) {
            throw new BusinessException("USERNAME_EXISTS", "Username already in use.");
        }
        if (userRepository.findByEmailAndDeletedAtIsNull(request.getEmail()).isPresent()) {
            throw new BusinessException("EMAIL_EXISTS", "Email already in use.");
        }

        User user = User.builder()
                .company(company)
                .username(username)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .firstName(firstName)
                .lastName(lastName)
                .avatarUrl(request.getAvatarUrl())
                .status("ACTIVE")
                .build();

        if (request.getBranchAssignments() != null) {
            for (var assignReq : request.getBranchAssignments()) {
                Branch branch = branchRepository.findByIdAndDeletedAtIsNull(assignReq.getBranchId())
                        .orElseThrow(() -> new ResourceNotFoundException("Branch", assignReq.getBranchId()));
                Role role = roleRepository.findById(assignReq.getRoleId())
                        .orElseThrow(() -> new ResourceNotFoundException("Role", assignReq.getRoleId()));

                UserBranchRole ubr = UserBranchRole.builder()
                        .user(user)
                        .branch(branch)
                        .role(role)
                        .isDefault(assignReq.isDefault())
                        .build();

                user.getBranchRoles().add(ubr);
            }
        }

        User saved = userRepository.save(user);
        return toDto(saved);
    }

    @Transactional
    public UserDto updateUser(Long id, CreateUserRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        if (request.getFirstName() != null && !request.getFirstName().isBlank()) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null && !request.getLastName().isBlank()) user.setLastName(request.getLastName());
        if (request.getEmail() != null && !request.getEmail().isBlank()) user.setEmail(request.getEmail());
        if (request.getUsername() != null && !request.getUsername().isBlank()) user.setUsername(request.getUsername());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getBranchAssignments() != null && !request.getBranchAssignments().isEmpty()) {
            user.getBranchRoles().clear();
            for (var assignReq : request.getBranchAssignments()) {
                Branch branch = branchRepository.findByIdAndDeletedAtIsNull(assignReq.getBranchId())
                        .orElseThrow(() -> new ResourceNotFoundException("Branch", assignReq.getBranchId()));
                Role role = roleRepository.findById(assignReq.getRoleId())
                        .orElseThrow(() -> new ResourceNotFoundException("Role", assignReq.getRoleId()));

                UserBranchRole ubr = UserBranchRole.builder()
                        .user(user)
                        .branch(branch)
                        .role(role)
                        .isDefault(assignReq.isDefault())
                        .build();

                user.getBranchRoles().add(ubr);
            }
        }

        return toDto(userRepository.save(user));
    }

    @Transactional
    public UserDto deactivateUser(Long id) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        String newStatus = "ACTIVE".equalsIgnoreCase(user.getStatus()) ? "INACTIVE" : "ACTIVE";
        user.setStatus(newStatus);
        return toDto(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public boolean checkUserUnique(String field, String value, Long excludeId) {
        Optional<User> existing = "email".equalsIgnoreCase(field)
                ? userRepository.findByEmailAndDeletedAtIsNull(value)
                : userRepository.findByUsernameAndDeletedAtIsNull(value);
        if (existing.isEmpty()) {
            return true;
        }
        return excludeId != null && existing.get().getId().equals(excludeId);
    }

    private UserDto toDto(User user) {
        List<UserBranchAssignmentDto> assignments = user.getBranchRoles().stream()
                .map(ubr -> UserBranchAssignmentDto.builder()
                        .branchId(ubr.getBranch().getId())
                        .branchName(ubr.getBranch().getName())
                        .roleId(ubr.getRole().getId())
                        .roleName(ubr.getRole().getName())
                        .isDefault(Boolean.TRUE.equals(ubr.getIsDefault()))
                        .build())
                .toList();

        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .branchAssignments(assignments)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
