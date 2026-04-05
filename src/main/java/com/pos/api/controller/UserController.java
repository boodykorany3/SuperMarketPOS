package com.pos.api.controller;

import com.pos.api.dto.ChangePasswordRequestDto;
import com.pos.api.dto.LoginRequestDto;
import com.pos.api.dto.LoginResponseDto;
import com.pos.api.dto.UserRequestDto;
import com.pos.api.dto.UserResponseDto;
import com.pos.api.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDto createUser(@Valid @RequestBody UserRequestDto request) {
        return userService.create(request);
    }

    @GetMapping("/users")
    public List<UserResponseDto> getUsers() {
        return userService.getAll();
    }

    @PostMapping("/auth/login")
    public LoginResponseDto login(@Valid @RequestBody LoginRequestDto request) {
        return userService.login(request);
    }

    @PostMapping("/users/change-password")
    public boolean changePassword(Authentication authentication,
                                  @Valid @RequestBody ChangePasswordRequestDto request) {
        return userService.changePassword(authentication.getName(), request);
    }
}
