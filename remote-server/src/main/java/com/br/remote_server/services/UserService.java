package com.br.remote_server.services;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import com.br.remote_server.dtos.users.AuthenticationRequestDTO;
import com.br.remote_server.dtos.users.AuthenticationResponseDTO;
import com.br.remote_server.dtos.users.UserRequestDTO;
import com.br.remote_server.dtos.users.UserResponseDTO;
import com.br.remote_server.enums.Provider;
import com.br.remote_server.exceptions.UnauthorizedUser;
import com.br.remote_server.exceptions.UserAlreadyRegistered;
import com.br.remote_server.infra.security.SecurityConfigurations;
import com.br.remote_server.infra.security.TokenService;
import com.br.remote_server.models.User;
import com.br.remote_server.repositorys.UserRepository;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private SecurityConfigurations securityConfiguration;

    public UserResponseDTO createUser(UserRequestDTO data) {
        if (userRepository.existsByEmail(data.email()) || userRepository.existsByEmail(data.email())) {
            throw new UserAlreadyRegistered();// Retorna erro 400 se login ou e-mail já existir
        }
        User userEntity = User.builder()
            .email(data.email())
            .password(securityConfiguration.passwordEncoder().encode(data.password()))
            .firstName(data.firstName())
            .lastName(data.lastName())
            .authProvider(Provider.LOCATION)
            .build();

        return new UserResponseDTO(userRepository.save(userEntity));
    }

    public AuthenticationResponseDTO authenticate(final AuthenticationRequestDTO data) {

        // Authenticate the user
        var token = new UsernamePasswordAuthenticationToken(data.username(), data.password());
        var authentication = authenticationManager.authenticate(token);

        // Generate JWT access token
        String accessToken = tokenService.generateToken(((User) authentication.getPrincipal()).getUsername());

        // Fetch user and create refresh token
        User user = userRepository.findByEmail(data.username()).orElseThrow(() -> new UnauthorizedUser("Usário não autorizado"));

        String refreshToken = tokenService.generateRefreshToken(user.getUsername());

        return new AuthenticationResponseDTO(accessToken, refreshToken);
    }

    public AuthenticationResponseDTO refreshToken(String refreshToken) {
        String subject = tokenService.getSubject(refreshToken); // Valida o token JWT
        final var newAccessToken = tokenService.generateToken(subject);
        return new AuthenticationResponseDTO(newAccessToken, refreshToken);
    }

    public UserResponseDTO getCurrentUser(User user) {
        return new UserResponseDTO(user);
    }
}