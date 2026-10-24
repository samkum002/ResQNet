
package com.dev.ResQNet;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class customLogout implements LogoutSuccessHandler {

    private final userRepo repo;

    public customLogout(userRepo repo) {
        this.repo = repo;
    }

    @Override
    public void onLogoutSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication)
            throws IOException, ServletException {

        if (authentication != null) {
            String name = authentication.getName();
            userEntity u = repo.findByUsername(name);

            if (u != null && u.getRoles().contains("ADMIN")) {
                u.setAdminStatus(Admin.OFFLINE);
                repo.save(u);
            }
        }

        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));

        if (isAjax) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"message\":\"Logged out successfully\"}");
        } else {
            response.sendRedirect("/login");
        }
    }
}
