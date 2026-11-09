package com.br.remote_server.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.br.remote_server.enums.Provider;
import com.br.remote_server.models.User;
import com.br.remote_server.repositorys.UserRepository;

@Service
public class CustomOidcUserService extends OidcUserService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String email = oidcUser.getAttribute("email");
        String firstName = oidcUser.getAttribute("given_name");
        String lastName = oidcUser.getAttribute("family_name");
        String providerId = oidcUser.getSubject();

        String nameProvider = userRequest.getClientRegistration().getRegistrationId();
        Provider provider = Provider.valueOf(nameProvider.toUpperCase());

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> User.builder()
                        .email(email)
                        .firstName(firstName)
                        .lastName(lastName)
                        .authProvider(provider)
                        .providerUserId(providerId)
                        .build());

        userRepository.save(user);

        return new CustomOAuth2User(oidcUser, user);
    }
}
