package com.br.remote_server.services;

import com.br.remote_server.models.User;
import com.br.remote_server.repositorys.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(final String username)
            throws UsernameNotFoundException {

        return (userRepository.findByEmail(username)).map(user ->
                User.builder()
                        .email(username)
                        .password(user.getPassword())
                        .build()
        ).orElseThrow(() -> new UsernameNotFoundException(
                "User with username [%s] not found".formatted(username)));
    }
}