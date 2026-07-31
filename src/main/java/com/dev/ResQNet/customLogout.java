package com.dev.ResQNet;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class customLogout implements LogoutSuccessHandler{

    @Autowired
    userRepo repo;

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException{

        String name = authentication.getName();

        userEntity u = repo.findByUsername(name);
        u.setAdminStatus(Admin.OFFLINE);
        repo.save(u);

        response.sendRedirect("/Login.html");
    }
}
