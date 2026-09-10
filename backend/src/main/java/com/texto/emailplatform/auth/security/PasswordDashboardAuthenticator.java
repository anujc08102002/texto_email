package com.texto.emailplatform.auth.security;

import com.texto.emailplatform.auth.domain.UserEntity;
import com.texto.emailplatform.auth.domain.UserRepository;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordDashboardAuthenticator implements DashboardAuthenticator {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordDashboardAuthenticator(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DashboardPrincipal> authenticate(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findFirstByEmailIgnoreCase(email.trim())
                .filter(user -> "ACTIVE".equals(user.getStatus()))
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .map(PasswordDashboardAuthenticator::toPrincipal);
    }

    private static DashboardPrincipal toPrincipal(UserEntity user) {
        return new DashboardPrincipal(user.getId(), user.getTenantId(), user.getEmail(), user.getRole());
    }
}
