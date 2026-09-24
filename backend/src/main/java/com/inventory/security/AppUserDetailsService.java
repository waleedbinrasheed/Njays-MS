package com.inventory.security;

import com.inventory.repository.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    /** Accepts a username or an email address. */
    @Override
    @Transactional(readOnly = true)
    public AppUserPrincipal loadUserByUsername(String login) {
        return users.findForLogin(login.trim())
                .map(AppUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }

    @Transactional(readOnly = true)
    public Optional<AppUserPrincipal> loadById(Long id) {
        return users.findWithAuthoritiesById(id).map(AppUserPrincipal::from);
    }
}
