
package com.campusauction.auction;

import com.campusauction.auth.CurrentMarketplaceUser;
import com.campusauction.auth.MarketplaceUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auctions")
public class AuctionController {

    private final AuctionService auctionService;
    private final CurrentMarketplaceUser currentUser;

    public AuctionController(AuctionService auctionService, CurrentMarketplaceUser currentUser) {
        this.auctionService = auctionService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<Auction> getAllAuctions() {
        return auctionService.getAllAuctions();
    }

    @GetMapping("/{id}")
    public Auction getAuction(@PathVariable Long id) {
        return auctionService.getAuction(id);
    }

    @GetMapping("/mine")
    public List<Auction> getMyAuctions(Authentication authentication) {
        return auctionService.getAuctionsForSeller(currentUser.require(authentication).getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Auction createAuction(@Valid @RequestBody AuctionRequest request,
            Authentication authentication) {
        return auctionService.createAuction(request, currentUser.require(authentication));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Auction createAuctionWithImage(
            @Valid @RequestPart("auction") AuctionRequest request,
            @RequestPart("image") MultipartFile image,
            Authentication authentication) throws IOException {
        return auctionService.createAuction(request, currentUser.require(authentication), image);
    }

    @GetMapping("/{id}/bids")
    public List<Bid> getBids(@PathVariable Long id) {
        return auctionService.getBids(id);
    }

    @PostMapping("/{id}/bids")
    @ResponseStatus(HttpStatus.CREATED)
    public Bid placeBid(
            @PathVariable Long id,
            @Valid @RequestBody BidRequest request,
            Authentication authentication) {
        return auctionService.placeBid(
                id, currentUser.require(authentication), request.amount());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(
            IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", exception.getMessage()));
    }

    public record BidRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount) {
    }
}
