package com.afrisanjaya.shipment.logistics.service;

import com.afrisanjaya.shipment.logistics.api.dto.AuthResponse;
import com.afrisanjaya.shipment.logistics.api.dto.LoginRequest;
import com.afrisanjaya.shipment.logistics.api.dto.RegisterRequest;
import com.afrisanjaya.shipment.logistics.api.exception.ShipmentNotFoundException;
import com.afrisanjaya.shipment.logistics.domain.entity.User;
import com.afrisanjaya.shipment.logistics.domain.repository.UserRepository;
import com.afrisanjaya.shipment.logistics.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        User user = User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .role("OPERATOR")
                .build();
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        log.info("[AUTH] User registered: {}", user.getUsername());
        return new AuthResponse(token, user.getUsername());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Invalid username or password"));

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        log.info("[AUTH] User logged in: {}", user.getUsername());
        return new AuthResponse(token, user.getUsername());
    }
}
