package com.dev.ResQNet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class customUserDetails implements UserDetailsService{
    
    @Autowired
    userRepo repo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException{

        userEntity user = repo.findByUsername(username);

        if(user==null){
            throw new UsernameNotFoundException("User not found with name : "+ username);
        }

        return User.builder().username(user.getUsername()).
        password(user.getPassword()).roles(user.getRoles().toArray(new String[0])).build();
    }
}
