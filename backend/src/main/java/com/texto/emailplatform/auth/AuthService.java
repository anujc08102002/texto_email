package com.texto.emailplatform.auth;

import com.texto.emailplatform.auth.api.AuthResponse;
import com.texto.emailplatform.auth.api.AuthUserResponse;
import com.texto.emailplatform.auth.api.LoginRequest;
import com.texto.emailplatform.auth.api.RegisterRequest;
import com.texto.emailplatform.auth.domain.DashboardSessionEntity;
import com.texto.emailplatform.auth.domain.DashboardSessionRepository;
import com.texto.emailplatform.auth.domain.UserEntity;
import com.texto.emailplatform.auth.domain.UserRepository;
import com.texto.emailplatform.auth.security.DashboardAuthenticator;
import com.texto.emailplatform.auth.security.DashboardPrincipal;
import com.texto.emailplatform.auth.security.HashedApiKeyAuthenticator;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.common.security.TenantRoles;
import com.texto.emailplatform.subscription.SubscriptionService;
import com.texto.emailplatform.tenant.domain.TenantEntity;
import com.texto.emailplatform.tenant.domain.TenantRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final Duration SESSION_TTL = Duration.ofDays(7);

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final DashboardSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final DashboardAuthenticator dashboardAuthenticator;
    private final SubscriptionService subscriptionService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            DashboardSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            DashboardAuthenticator dashboardAuthenticator,
            SubscriptionService subscriptionService
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.dashboardAuthenticator = dashboardAuthenticator;
        this.subscriptionService = subscriptionService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT.value(), "EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
        }

        TenantEntity tenant = tenantRepository.save(TenantEntity.create(request.organization(), uniqueSlug(request.organization())));
        String displayName = displayNameFromEmail(email);
        UserEntity user = userRepository.save(UserEntity.create(
                tenant.getId(),
                email,
                passwordEncoder.encode(request.password()),
                displayName,
                TenantRoles.OWNER
        ));
        subscriptionService.createFreeForTenant(tenant.getId());
        return issueSession(user, tenant);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        DashboardPrincipal principal = dashboardAuthenticator.authenticate(request.email(), request.password())
                .orElseThrow(AuthService::authenticationFailed);
        UserEntity user = userRepository.findById(principal.userId())
                .orElseThrow(AuthService::authenticationFailed);
        TenantEntity tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(AuthService::authenticationFailed);
        return issueSession(user, tenant);
    }

    /**
     * Invalidates the session behind the supplied bearer token. Unknown tokens are ignored so the
     * endpoint cannot be used to probe for valid sessions.
     */
    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        sessionRepository.deleteByTokenHash(HashedApiKeyAuthenticator.sha256(rawToken.trim()));
    }

    @Transactional(readOnly = true)
    public Optional<DashboardPrincipal> authenticateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String hash = HashedApiKeyAuthenticator.sha256(rawToken.trim());
        return sessionRepository.findByTokenHash(hash)
                .filter(session -> !session.isExpired())
                .flatMap(session -> userRepository.findById(session.getUserId())
                        .filter(user -> "ACTIVE".equals(user.getStatus()))
                        .map(user -> new DashboardPrincipal(user.getId(), user.getTenantId(), user.getEmail(), user.getRole())));
    }

    @Transactional(readOnly = true)
    public AuthUserResponse currentUser(DashboardPrincipal principal) {
        TenantEntity tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED.value(), "UNAUTHENTICATED", "Authentication is required"));
        return new AuthUserResponse(principal.userId(), principal.tenantId(), principal.email(), principal.role(), tenant.getName());
    }

    private AuthResponse issueSession(UserEntity user, TenantEntity tenant) {
        String token = generateToken();
        sessionRepository.save(DashboardSessionEntity.create(
                user.getId(),
                tenant.getId(),
                HashedApiKeyAuthenticator.sha256(token),
                Instant.now().plus(SESSION_TTL)
        ));
        AuthUserResponse profile = new AuthUserResponse(
                user.getId(),
                tenant.getId(),
                user.getEmail(),
                user.getRole(),
                tenant.getName()
        );
        return AuthResponse.bearer(token, profile);
    }

    private String uniqueSlug(String organization) {
        String base = TenantSlugger.slugify(organization);
        String slug = base;
        int suffix = 2;
        while (tenantRepository.findBySlug(slug).isPresent()) {
            String ending = "-" + suffix;
            int maxBase = Math.max(1, 63 - ending.length());
            slug = base.substring(0, Math.min(base.length(), maxBase)) + ending;
            suffix++;
        }
        return slug;
    }

    private static ApiException authenticationFailed() {
        return new ApiException(HttpStatus.UNAUTHORIZED.value(), "AUTHENTICATION_FAILED", "Authentication failed");
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "txt_" + HexFormat.of().formatHex(bytes);
    }

    private static String displayNameFromEmail(String email) {
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        return local.isBlank() ? email : local;
    }
}
