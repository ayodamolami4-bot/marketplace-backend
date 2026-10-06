package com.marketplace.backend.auth;

import com.marketplace.backend.user.User;
import com.marketplace.backend.user.UserRole;
import com.marketplace.backend.user.UserRoleRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UserDetailsImpl implements UserDetails {

    private final User user;
    private final List<GrantedAuthority> authorities;

    public UserDetailsImpl(User user, UserRoleRepository userRoleRepository) {
        this.user = user;

        this.authorities = userRoleRepository.findByUserId(user.getId())
                .stream()
                .map(UserRole::getRole)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(
                        "ROLE_" + role.getName().name()
                ))
                .toList();
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() != com.marketplace.backend.user.UserStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == com.marketplace.backend.user.UserStatus.ACTIVE;
    }
}