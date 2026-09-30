package com.vipul.agrishare.service;

import com.vipul.agrishare.dto.RegisterRequest;
import com.vipul.agrishare.entity.User;
import com.vipul.agrishare.exception.ApiException;
import com.vipul.agrishare.repository.UserRepository;
import com.vipul.agrishare.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authService = new AuthService(userRepository, passwordEncoder, jwtService, authenticationManager);
    }

    private RegisterRequest request(String email) {
        return new RegisterRequest(
                "Ramesh Kumar", email, "9876543210",
                "password123", User.Role.OWNER, 30.0, 77.0, "Paonta Sahib", "hi"
        );
    }

    @Test
    void register_throwsConflict_whenEmailAlreadyExists() {
        RegisterRequest request = request("ramesh@example.com");
        when(userRepository.existsByEmail("ramesh@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApiException.class)
                .hasMessage("error.email.exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_throwsConflict_whenPhoneAlreadyExists() {
        RegisterRequest request = request(null);
        when(userRepository.existsByPhone("9876543210")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApiException.class)
                .hasMessage("error.phone.exists");
    }

    @Test
    void register_savesUserAndReturnsToken_whenEmailAndPhoneAreFree() {
        RegisterRequest request = request("Ramesh@Example.com");

        when(userRepository.existsByEmail("ramesh@example.com")).thenReturn(false);
        when(userRepository.existsByPhone(request.phone())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtService.generateToken(any())).thenReturn("fake-jwt-token");

        var response = authService.register(request);

        assertThat(response.token()).isEqualTo("fake-jwt-token");
        assertThat(response.email()).isEqualTo("ramesh@example.com");
        assertThat(response.role()).isEqualTo("OWNER");
        assertThat(response.preferredLanguage()).isEqualTo("hi");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_allowsMissingEmail_forPhoneOnlyFarmers() {
        RegisterRequest request = request(null);
        when(userRepository.existsByPhone(request.phone())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(2L);
            return u;
        });
        when(jwtService.generateToken(any())).thenReturn("t");

        var response = authService.register(request);

        assertThat(response.email()).isNull();
        verify(userRepository, never()).existsByEmail(any());
    }
}
