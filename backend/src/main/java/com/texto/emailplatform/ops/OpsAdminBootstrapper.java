package com.texto.emailplatform.ops;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.ops.domain.PlatformAdminEntity;
import com.texto.emailplatform.ops.domain.PlatformAdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OpsAdminBootstrapper implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OpsAdminBootstrapper.class);

    private final EmailPlatformProperties properties;
    private final PlatformAdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    public OpsAdminBootstrapper(
            EmailPlatformProperties properties,
            PlatformAdminRepository adminRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.getOps().getBootstrapEmail();
        String password = properties.getOps().getBootstrapPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (adminRepository.count() > 0) {
            return;
        }
        if (password.length() < 8) {
            throw new IllegalStateException("OPS_BOOTSTRAP_PASSWORD must be at least 8 characters");
        }
        adminRepository.save(PlatformAdminEntity.create(
                email,
                passwordEncoder.encode(password),
                "Platform Admin"
        ));
        log.info("Bootstrapped first platform admin (email not logged)");
    }
}
