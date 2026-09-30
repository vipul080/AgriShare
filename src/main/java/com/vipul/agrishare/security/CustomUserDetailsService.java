package com.vipul.agrishare.security;

import com.vipul.agrishare.entity.User;
import com.vipul.agrishare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Used at login: the identifier is either an email address or a phone number.
     */
    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        return findByIdentifier(identifier)
                .map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user found for identifier"));
    }

    /**
     * Used by the JWT filter: tokens carry the immutable user id.
     */
    public UserDetails loadUserById(Long id) {
        return userRepository.findById(id)
                .map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user found with id " + id));
    }

    public Optional<User> findByIdentifier(String identifier) {
        if (identifier == null) {
            return Optional.empty();
        }
        String trimmed = identifier.trim();
        return trimmed.contains("@")
                ? userRepository.findByEmail(trimmed.toLowerCase())
                : userRepository.findByPhone(trimmed);
    }
}
