package com.example.finanzas.service;

import com.example.finanzas.dto.auth.AuthResponseDto;
import com.example.finanzas.dto.auth.LoginRequestDto;
import com.example.finanzas.dto.auth.RegisterRequestDto;
import com.example.finanzas.dto.auth.UserDto;
import com.example.finanzas.entity.Cliente;
import com.example.finanzas.entity.enums.RolUsuario;
import com.example.finanzas.exception.BadRequestException;
import com.example.finanzas.exception.UnauthorizedException;
import com.example.finanzas.repository.ClienteRepository;
import com.example.finanzas.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponseDto register(RegisterRequestDto request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        if (clienteRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }

        Cliente cliente = new Cliente();
        cliente.setDni(generatePlaceholderDni());
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
                .firstName(firstName)
                .fullName(cliente.getNombre())
                .email(cliente.getEmail())
                .role(cliente.getRol().name().toLowerCase())
                .build();
    }

    private String generatePlaceholderDni() {
        String dni;
        do {
            dni = String.format("%08d", System.nanoTime() % 100_000_000L);
        } while (clienteRepository.existsByDni(dni));
        return dni;
    }
}
