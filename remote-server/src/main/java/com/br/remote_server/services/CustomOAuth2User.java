package com.br.remote_server.services;

import java.util.Collection;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.br.remote_server.models.User;

import lombok.AllArgsConstructor;

@AllArgsConstructor 
public class CustomOAuth2User implements OidcUser, UserDetails {

    @Autowired 
    private OidcUser oidcUser;
    @Autowired 
    private User user;

    @Override
    public Map<String, Object> getAttributes() {
        return oidcUser.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getAuthorities();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getName() {
        return oidcUser.getFullName(); // ou getAttribute("name")
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    // ---- Métodos específicos do OidcUser ----
    @Override
    public OidcIdToken getIdToken() {
        return oidcUser.getIdToken();
    }

    @Override
    public Map<String, Object> getClaims() {
        return Map.of();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return oidcUser.getUserInfo();
    }
}