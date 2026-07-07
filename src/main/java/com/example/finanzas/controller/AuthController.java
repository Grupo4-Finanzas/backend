package com.example.finanzas.controller;

import com.example.finanzas.dto.auth.AuthResponseDto;
import com.example.finanzas.dto.auth.ChangePasswordRequestDto;
import com.example.finanzas.dto.auth.ForgotPasswordRequestDto;
import com.example.finanzas.dto.auth.ForgotPasswordResponseDto;
import com.example.finanzas.dto.auth.LoginRequestDto;
import com.example.finanzas.dto.auth.RegisterRequestDto;
import com.example.finanzas.dto.auth.ResetPasswordRequestDto;
import com.example.finanzas.dto.auth.UpdateProfileRequestDto;
import com.example.finanzas.dto.auth.UserDto;
import com.example.finanzas.entity.Cliente;
import com.example.finanzas.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponseDto register(@Valid @RequestBody RegisterRequestDto request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponseDto login(@Valid @RequestBody LoginRequestDto request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal Cliente cliente) {
        return authService.getCurrentUser(cliente);
    }

    @PatchMapping("/me")
    public UserDto updateMe(
            @Valid @RequestBody UpdateProfileRequestDto request,
            @AuthenticationPrincipal Cliente cliente) {
        return authService.updateProfile(cliente, request);
    }

    @PostMapping("/me/password")
    public void changePassword(
            @Valid @RequestBody ChangePasswordRequestDto request,
            @AuthenticationPrincipal Cliente cliente) {
        authService.changePassword(cliente, request);
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponseDto forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto request) {
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequestDto request) {
        authService.resetPassword(request);
    }
}
