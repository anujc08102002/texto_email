package com.texto.emailplatform.campaign;

import com.texto.emailplatform.auth.security.DashboardPrincipal;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.common.security.Permission;
import com.texto.emailplatform.common.security.RolePermissions;
import com.texto.emailplatform.common.security.SecuritySupport;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CampaignAuthorizationService {
    public boolean canRead() {
        return SecuritySupport.dashboardPrincipal(SecurityContextHolder.getContext().getAuthentication())
                .map(principal -> RolePermissions.has(principal.role(), Permission.EMAIL_READ)).orElse(false);
    }
    public boolean canManage() {
        return SecuritySupport.dashboardPrincipal(SecurityContextHolder.getContext().getAuthentication())
                .map(principal -> RolePermissions.has(principal.role(), Permission.EMAIL_SEND)).orElse(false);
    }
    public DashboardPrincipal requireSender() {
        DashboardPrincipal principal = SecuritySupport.requireDashboardPrincipal(
                SecurityContextHolder.getContext().getAuthentication()
        );
        if (!RolePermissions.has(principal.role(), Permission.EMAIL_SEND)) {
            throw new ApiException(403, "FORBIDDEN", "Access is denied");
        }
        return principal;
    }
}