package com.campusauction.auth;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Component
public class CurrentMarketplaceUser {
    private final MarketplaceUserRepository userRepository;

    public CurrentMarketplaceUser(MarketplaceUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public MarketplaceUser require(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Sign in is required");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        UNAUTHORIZED, "Account is no longer available"));
    }
}
