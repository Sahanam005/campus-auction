package com.campusauction.discovery;

import com.campusauction.auth.MarketplaceUser;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "saved_searches")
public class SavedSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private MarketplaceUser user;

    @Column(length = 150)
    private String query;

    @Column(length = 40)
    private String category;

    @Column(precision = 12, scale = 2)
    private BigDecimal maxPrice;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public SavedSearch() {}

    public SavedSearch(MarketplaceUser user, String query, String category, BigDecimal maxPrice) {
        this.user = user;
        this.query = query;
        this.category = category;
        this.maxPrice = maxPrice;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getQuery() { return query; }
    public String getCategory() { return category; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    @JsonIgnore
    public MarketplaceUser getUser() { return user; }
}
