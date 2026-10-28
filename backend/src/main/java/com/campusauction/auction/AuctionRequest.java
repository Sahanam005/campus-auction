package com.campusauction.auction;

import com.campusauction.auth.MarketplaceUser;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuctionRequest(
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 40) String category,
        @NotBlank @Size(max = 30) String conditionLabel,
        @NotBlank @Size(max = 2000) String description,
        @NotNull @DecimalMin(value = "0.01") BigDecimal startingPrice,
        @NotNull @DecimalMin(value = "0.01") BigDecimal minIncrement,
        @NotNull LocalDateTime closingTime) {

    public Auction toAuction(MarketplaceUser seller) {
        Auction auction = new Auction();
        auction.setTitle(title);
        auction.setCategory(category);
        auction.setConditionLabel(conditionLabel);
        auction.setDescription(description);
        auction.setStartingPrice(startingPrice);
        auction.setMinIncrement(minIncrement);
        auction.setClosingTime(closingTime);
        auction.setSellerName(seller.getDisplayName());
        auction.setSeller(seller);
        return auction;
    }
}
