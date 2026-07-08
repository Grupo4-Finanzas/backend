package com.example.finanzas.service;

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
import com.example.finanzas.entity.PasswordResetToken;
import com.example.finanzas.entity.enums.RolUsuario;
import com.example.finanzas.exception.BadRequestException;
import com.example.finanzas.exception.UnauthorizedException;
import com.example.finanzas.repository.ClienteRepository;
import com.example.finanzas.repository.PasswordResetTokenRepository;
import com.example.finanzas.security.JwtTokenProvider;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final ClienteRepository clienteRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthResponseDto register(RegisterRequestDto request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        if (clienteRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }
        if (clienteRepository.existsByDni(request.getDocumentNumber())) {
            throw new BadRequestException("DNI already registered");
        }

        Cliente cliente = new Cliente();
        cliente.setDni(request.getDocumentNumber());
        cliente.setNombre(request.getFullName());
        cliente.setEmail(request.getEmail());
        cliente.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        cliente.setRol(RolUsuario.CLIENT);

        Cliente saved = clienteRepository.save(cliente);
        return buildAuthResponse(saved);
    }

    public AuthResponseDto login(LoginRequestDto request) {
        Cliente cliente = clienteRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), cliente.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        return buildAuthResponse(cliente);
    }

    public UserDto getCurrentUser(Cliente cliente) {
        return toUserDto(cliente);
    }

    @Transactional
    public UserDto updateProfile(Cliente cliente, UpdateProfileRequestDto request) {
        if (clienteRepository.existsByDniAndIdClienteNot(request.getDocumentNumber(), cliente.getIdCliente())) {
            throw new BadRequestException("DNI already registered");
        }
        cliente.setDni(request.getDocumentNumber());
        cliente.setNombre(request.getFullName());
        return toUserDto(clienteRepository.save(cliente));
    }

    @Transactional
    public void changePassword(Cliente cliente, ChangePasswordRequestDto request) {
        if (!passwordEncoder.matches(request.getCurrentPassword(), cliente.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        updatePassword(cliente, request.getNewPassword(), request.getConfirmPassword());
    }

    @Transactional
    public ForgotPasswordResponseDto forgotPassword(ForgotPasswordRequestDto request) {
        return clienteRepository.findByEmail(request.getEmail())
                .map(cliente -> {
                    PasswordResetToken passwordResetToken = new PasswordResetToken();
                    passwordResetToken.setCliente(cliente);
                    passwordResetToken.setToken(generateResetToken());
                    passwordResetToken.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
                    PasswordResetToken saved = passwordResetTokenRepository.save(passwordResetToken);
                    return ForgotPasswordResponseDto.builder()
                            .message("Password reset token generated")
                            .resetToken(saved.getToken())
                            .build();
                })
                .orElseGet(() -> ForgotPasswordResponseDto.builder()
                        .message("If the email exists, a reset token was generated")
                        .build());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequestDto request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid reset token"));

        if (resetToken.getUsedAt() != null) {
            throw new BadRequestException("Reset token already used");
        }
        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Reset token expired");
        }

        updatePassword(resetToken.getCliente(), request.getNewPassword(), request.getConfirmPassword());
        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);
    }

    private AuthResponseDto buildAuthResponse(Cliente cliente) {
        String token = jwtTokenProvider.generateToken(cliente.getIdCliente(), cliente.getEmail());
        return AuthResponseDto.builder()
                .token(token)
                .user(toUserDto(cliente))
                .build();
    }

    private UserDto toUserDto(Cliente cliente) {
        String firstName = cliente.getNombre().split(" ")[0];
        return UserDto.builder()
                .id(cliente.getIdCliente())
                .documentNumber(cliente.getDni())
                .firstName(firstName)
                .fullName(cliente.getNombre())
                .email(cliente.getEmail())
                .role(cliente.getRol().name().toLowerCase())
                .build();
    }

    private void updatePassword(Cliente cliente, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new BadRequestException("Passwords do not match");
        }
        cliente.setPasswordHash(passwordEncoder.encode(newPassword));
        clienteRepository.save(cliente);
    }

    private String generateResetToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
