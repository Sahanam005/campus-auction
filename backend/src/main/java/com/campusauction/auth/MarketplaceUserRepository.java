package com.campusauction.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketplaceUserRepository extends JpaRepository<MarketplaceUser, Long> {
    Optional<MarketplaceUser> findByEmail(String email);
    boolean existsByEmail(String email);
}
