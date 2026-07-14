package com.dev.ResQNet;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class customLogin implements AuthenticationSuccessHandler{
    
    @Autowired
    userRepo repo;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException{

        String name = authentication.getName();

        userEntity u = repo.findByUsername(name);
        if(u.getRoles().contains("USER")){
            response.sendRedirect("/userDashboard.html");
        }
        else{
            u.setAdminStatus(Admin.AVAILABLE);
            repo.save(u);
            response.sendRedirect("/adminDashboard.html");
        }
    }

}
