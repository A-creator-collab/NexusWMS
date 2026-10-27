package com.nexuswms.security;

import com.nexuswms.repository.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class NexusUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    public NexusUserDetailsService(UserRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        com.nexuswms.model.User u = users.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException(username));
        return org.springframework.security.core.userdetails.User.withUsername(u.username)
                .password(u.passwordHash).roles(u.role.name()).disabled(!u.active).build();
    }
}
