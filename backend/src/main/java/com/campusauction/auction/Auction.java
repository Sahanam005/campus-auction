
package com.campusauction.auction;

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

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

    @Version
    private Long version;

    protected Auction() {}

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getConditionLabel() { return conditionLabel; }
    public String getDescription() { return description; }
    public BigDecimal getStartingPrice() { return startingPrice; }
    public BigDecimal getMinIncrement() { return minIncrement; }
    public LocalDateTime getClosingTime() { return closingTime; }
    public String getSellerName() { return sellerName; }
    public String getImageUrl() { return imageUrl; }
    public String getStatus() { return status; }
    public Long getVersion() { return version; }

    public void setTitle(String v) { title = v; }
    public void setCategory(String v) { category = v; }
    public void setConditionLabel(String v) { conditionLabel = v; }
    public void setDescription(String v) { description = v; }
    public void setStartingPrice(BigDecimal v) { startingPrice = v; }
    public void setMinIncrement(BigDecimal v) { minIncrement = v; }
    public void setClosingTime(LocalDateTime v) { closingTime = v; }
    public void setSellerName(String v) { sellerName = v; }
    public void setImageUrl(String v) { imageUrl = v; }
    public void setStatus(String v) { status = v; }
}
