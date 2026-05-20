package dev.springboot4docs.ch_29_jdbc_users;

import java.util.Arrays;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class JpaUserDetailsService implements UserDetailsService, UserDetailsPasswordService {

    private final AppUserRepository users;

    JpaUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = this.users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return toUserDetails(user);
    }

    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails user, String newPassword) {
        AppUser appUser = this.users.findByUsername(user.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException(user.getUsername()));
        appUser.setPasswordHash(newPassword);
        return toUserDetails(this.users.save(appUser));
    }

    private static UserDetails toUserDetails(AppUser user) {
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(authoritiesFrom(user.getRoles()))
                .disabled(!user.isEnabled())
                .build();
    }

    private static List<GrantedAuthority> authoritiesFrom(String roles) {
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter((role) -> !role.isBlank())
                .map((role) -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

}
