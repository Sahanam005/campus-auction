
package com.campusauction.auction;

import com.campusauction.auth.MarketplaceUser;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "auctions")
public class Auction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 30)
    private String conditionLabel;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal startingPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal minIncrement;

    @Column(nullable = false)
    private LocalDateTime closingTime;

    @Column(nullable = false, length = 100)
    private String sellerName;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_user_id")
    private MarketplaceUser seller;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

    @Column(length = 100)
    private String winningBidderName;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winning_bidder_user_id")
    private MarketplaceUser winningBidder;

    @Column(precision = 12, scale = 2)
    private BigDecimal winningBidAmount;

    @Column(length = 200)
    private String pickupLocation;

    private LocalDateTime pickupTime;

    @Column(length = 1000)
    private String pickupNotes;

    @Column(length = 30)
    private String sellerDecision;

    private LocalDateTime handoverConfirmedAt;

    @Version
    private Long version;

    public Auction() {}

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getConditionLabel() { return conditionLabel; }
    public String getDescription() { return description; }
    public BigDecimal getStartingPrice() { return startingPrice; }
    public BigDecimal getMinIncrement() { return minIncrement; }
    public LocalDateTime getClosingTime() { return closingTime; }
    public String getSellerName() { return sellerName; }
    @JsonIgnore
    public MarketplaceUser getSeller() { return seller; }
    @JsonIgnore
    public Long getSellerUserId() { return seller == null ? null : seller.getId(); }
    public String getImageUrl() { return imageUrl; }
    public String getStatus() { return status; }
    public String getWinningBidderName() { return winningBidderName; }
    @JsonIgnore
    public MarketplaceUser getWinningBidder() { return winningBidder; }
    public Long getWinningBidderUserId() { return winningBidder == null ? null : winningBidder.getId(); }
    public BigDecimal getWinningBidAmount() { return winningBidAmount; }
    public String getPickupLocation() { return pickupLocation; }
    public LocalDateTime getPickupTime() { return pickupTime; }
    public String getPickupNotes() { return pickupNotes; }
    public String getSellerDecision() { return sellerDecision; }
    public LocalDateTime getHandoverConfirmedAt() { return handoverConfirmedAt; }
    public Long getVersion() { return version; }

    public void setTitle(String v) { title = v; }
    public void setCategory(String v) { category = v; }
    public void setConditionLabel(String v) { conditionLabel = v; }
    public void setDescription(String v) { description = v; }
    public void setStartingPrice(BigDecimal v) { startingPrice = v; }
    public void setMinIncrement(BigDecimal v) { minIncrement = v; }
    public void setClosingTime(LocalDateTime v) { closingTime = v; }
    public void setSellerName(String v) { sellerName = v; }
    @JsonIgnore
    public void setSeller(MarketplaceUser v) { seller = v; }
    public void setImageUrl(String v) { imageUrl = v; }
    public void setStatus(String v) { status = v; }
    public void setWinningBidderName(String v) { winningBidderName = v; }
    public void setWinningBidder(MarketplaceUser v) { winningBidder = v; }
    public void setWinningBidAmount(BigDecimal v) { winningBidAmount = v; }
    public void setPickupLocation(String v) { pickupLocation = v; }
    public void setPickupTime(LocalDateTime v) { pickupTime = v; }
    public void setPickupNotes(String v) { pickupNotes = v; }
    public void setSellerDecision(String v) { sellerDecision = v; }
    public void setHandoverConfirmedAt(LocalDateTime v) { handoverConfirmedAt = v; }
}
