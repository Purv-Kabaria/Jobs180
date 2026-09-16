package com.companytracker.security;

import com.companytracker.repo.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class TrackerUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public TrackerUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userAccountRepository.findByEmailIgnoreCase(username)
                .filter(u -> !u.isDeleted())
                .map(TrackerUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
