package com.vipul.agrishare.service;

import com.vipul.agrishare.dto.AuthResponse;
import com.vipul.agrishare.dto.LoginRequest;
import com.vipul.agrishare.dto.RegisterRequest;
import com.vipul.agrishare.entity.User;
import com.vipul.agrishare.exception.ApiException;
import com.vipul.agrishare.repository.UserRepository;
import com.vipul.agrishare.security.JwtService;
import com.vipul.agrishare.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        if (userRepository.existsByPhone(request.phone())) {
            throw ApiException.conflict("An account with this phone number already exists");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role() != null ? request.role() : User.Role.BOTH)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .address(request.address())
                .build();

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(new UserPrincipal(saved));

        return toAuthResponse(saved, token);
    }

    public AuthResponse login(LoginRequest request) {
        // Delegates credential checking to Spring Security's AuthenticationManager
        // (which uses CustomUserDetailsService + PasswordEncoder under the hood)
        // rather than re-implementing password comparison here.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));

        String token = jwtService.generateToken(new UserPrincipal(user));
        return toAuthResponse(user, token);
    }

    private AuthResponse toAuthResponse(User user, String token) {
        return new AuthResponse(
                token,
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}
