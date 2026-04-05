package com.pos.api.service;

import com.pos.api.dto.ChangePasswordRequestDto;
import com.pos.api.dto.LoginRequestDto;
import com.pos.api.dto.LoginResponseDto;
import com.pos.api.dto.UserRequestDto;
import com.pos.api.dto.UserResponseDto;
import com.pos.api.entity.Branch;
import com.pos.api.entity.User;
import com.pos.api.exception.ApiException;
import com.pos.api.repository.UserRepository;
import com.pos.api.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final BranchService branchService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       BranchService branchService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.branchService = branchService;
    }

    public UserResponseDto create(UserRequestDto request) {
        if (userRepository.existsByUsername(request.getUsername().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "Username already exists.");
        }
        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setBranch(branchService.requireById(request.getBranchId()));
        return toUserResponse(userRepository.save(user));
    }

    public java.util.List<UserResponseDto> getAll() {
        return userRepository.findAll().stream().map(this::toUserResponse).toList();
    }

    public LoginResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        }

        Branch branch = ensureUserBranch(user);

        LoginResponseDto response = new LoginResponseDto();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRole(user.getRole());
        response.setTokenType("Bearer");
        response.setToken(jwtService.generateToken(user));
        response.setExpiresIn(jwtService.getExpirationMs());
        response.setMessage("Login successful");
        response.setBranchId(branch.getId());
        response.setBranchCode(branch.getCode());
        response.setBranchName(branch.getName());
        return response;
    }

    public User requireById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found: " + id));
    }

    public boolean changePassword(String username, ChangePasswordRequestDto request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            return false;
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return true;
    }

    private UserResponseDto toUserResponse(User user) {
        UserResponseDto dto = new UserResponseDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRole(user.getRole());
        Branch branch = ensureUserBranch(user);
        dto.setBranchId(branch.getId());
        dto.setBranchCode(branch.getCode());
        dto.setBranchName(branch.getName());
        return dto;
    }

    private Branch ensureUserBranch(User user) {
        if (user.getBranch() != null) {
            return user.getBranch();
        }
        Branch mainBranch = branchService.ensureMainBranch();
        user.setBranch(mainBranch);
        userRepository.save(user);
        return mainBranch;
    }
}
