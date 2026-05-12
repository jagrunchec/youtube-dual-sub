package com.dualsub.config;

import com.dualsub.model.User;
import com.dualsub.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable : " + email));

        return org.springframework.security.core.userdetails.User
            .withUsername(user.getEmail())
            .password(user.getPasswordHash())
            // Spring Security expects "ROLE_XXX" prefix — roles() adds it automatically
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
            .accountLocked(!user.isActive())
            .build();
    }
}
