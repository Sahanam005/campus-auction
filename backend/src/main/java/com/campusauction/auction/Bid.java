
package com.campusauction.auction;

import com.campusauction.auth.MarketplaceUser;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bids")
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @Column(nullable = false)
    private String bidderName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bidder_user_id")
    private MarketplaceUser bidder;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Bid() {
    }

    public Bid(Auction auction, MarketplaceUser bidder, BigDecimal amount) {
        this.auction = auction;
        this.bidderName = bidder.getDisplayName();
        this.bidder = bidder;
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    @JsonIgnore
    public Auction getAuction() {
        return auction;
    }

    public String getBidderName() {
        return bidderName;
    }

    @JsonIgnore
    public Long getBidderUserId() {
        return bidder == null ? null : bidder.getId();
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
