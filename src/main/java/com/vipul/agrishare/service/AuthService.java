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
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = StringUtils.hasText(request.email()) ? request.email().trim().toLowerCase() : null;

        if (email != null && userRepository.existsByEmail(email)) {
            throw ApiException.conflict("error.email.exists");
        }
        if (userRepository.existsByPhone(request.phone())) {
            throw ApiException.conflict("error.phone.exists");
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role() != null ? request.role() : User.Role.BOTH)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .address(request.address())
                .preferredLanguage(request.preferredLanguage() != null ? request.preferredLanguage() : "en")
                .build();

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(new UserPrincipal(saved));

        return toAuthResponse(saved, token);
    }

    public AuthResponse login(LoginRequest request) {
        // Delegates credential checking to Spring Security's AuthenticationManager
        // (which uses CustomUserDetailsService + PasswordEncoder under the hood)
        // rather than re-implementing password comparison here.
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.identifier().trim(), request.password())
        );

        if (!(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw ApiException.unauthorized("error.credentials.invalid");
        }

        User user = principal.getUser();
        String token = jwtService.generateToken(principal);
        return toAuthResponse(user, token);
    }

    public static AuthResponse toAuthResponse(User user, String token) {
        return new AuthResponse(
                token,
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name(),
                user.getPreferredLanguage()
        );
    }
}
