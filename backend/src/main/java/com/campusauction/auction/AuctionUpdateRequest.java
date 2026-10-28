package com.campusauction.auction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuctionUpdateRequest(
        @Size(max = 150) String title,
        @Size(max = 40) String category,
        @Size(max = 30) String conditionLabel,
        @Size(max = 2000) String description,
        @DecimalMin(value = "0.01") BigDecimal startingPrice,
        @DecimalMin(value = "0.01") BigDecimal minIncrement,
        LocalDateTime closingTime) {
}
