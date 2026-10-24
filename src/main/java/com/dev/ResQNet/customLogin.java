package com.dev.ResQNet;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class customLogin implements AuthenticationSuccessHandler {

    private final userRepo repo;

    public customLogin(userRepo repo) {
        this.repo = repo;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication)
            throws IOException, ServletException {

        String name = authentication.getName();
        userEntity u = repo.findByUsername(name);

        if (u == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String redirectUrl = "/dashboard/user";
        if (u.getRoles().contains("USER")) {
            redirectUrl = "/dashboard/user";
        } else if (u.getRoles().contains("ADMIN")) {
            u.setAdminStatus(Admin.AVAILABLE);
            repo.save(u);
            redirectUrl = "/dashboard/admin";
        } else if (u.getRoles().contains("STATION_MANAGER")) {
            redirectUrl = "/dashboard/station";
        } else if (u.getRoles().contains("WORKER")) {
            redirectUrl = "/dashboard/worker";
        }

        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));

        if (isAjax) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_OK);
            StringBuilder rolesJson = new StringBuilder("[");
            for (int i = 0; i < u.getRoles().size(); i++) {
                rolesJson.append("\"").append(u.getRoles().get(i)).append("\"");
                if (i < u.getRoles().size() - 1) {
                    rolesJson.append(",");
                }
            }
            rolesJson.append("]");
            response.getWriter().write(
                "{\"username\":\"" + name + "\",\"roles\":" + rolesJson +
                ",\"redirect\":\"" + redirectUrl + "\"}"
            );
        } else {
            response.sendRedirect(redirectUrl);
        }
    }
}
