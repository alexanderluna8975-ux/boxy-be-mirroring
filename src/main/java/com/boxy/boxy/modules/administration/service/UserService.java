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

        if (userRepository.findByUsernameAndDeletedAtIsNull(request.getUsername()).isPresent()) {
            throw new BusinessException("USERNAME_EXISTS", "Username already in use.");
        }
        if (userRepository.findByEmailAndDeletedAtIsNull(request.getEmail()).isPresent()) {
            throw new BusinessException("EMAIL_EXISTS", "Email already in use.");
        }

        User user = User.builder()
                .company(company)
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
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
