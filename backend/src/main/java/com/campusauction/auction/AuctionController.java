package com.campusauction.auction;

import com.campusauction.auth.CurrentMarketplaceUser;
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
import java.time.LocalDateTime;
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

    @PutMapping("/{id}")
    public Auction updateAuction(
            @PathVariable Long id,
            @Valid @RequestBody AuctionUpdateRequest request,
            Authentication authentication) {
        return auctionService.updateAuction(id, request, currentUser.require(authentication));
    }

    @PostMapping("/{id}/cancel")
    public Auction cancelAuction(
            @PathVariable Long id,
            @RequestBody(required = false) ActionReasonRequest request,
            Authentication authentication) {
        String reason = request != null ? request.reason() : null;
        return auctionService.cancelAuction(id, currentUser.require(authentication), reason);
    }

    @DeleteMapping("/{id}")
    public Auction deleteAuction(
            @PathVariable Long id,
            Authentication authentication) {
        return auctionService.cancelAuction(id, currentUser.require(authentication), "Listing cancelled by seller");
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

    @PostMapping("/{id}/approve")
    public Auction approveWinner(
            @PathVariable Long id,
            @RequestBody(required = false) PickupDetailsRequest request,
            Authentication authentication) {
        String location = request != null ? request.pickupLocation() : null;
        LocalDateTime time = request != null ? request.pickupTime() : null;
        String notes = request != null ? request.pickupNotes() : null;
        return auctionService.approveProvisionalWinner(
                id, currentUser.require(authentication), location, time, notes);
    }

    @PostMapping("/{id}/reject")
    public Auction rejectWinner(
            @PathVariable Long id,
            @RequestBody(required = false) ActionReasonRequest request,
            Authentication authentication) {
        String reason = request != null ? request.reason() : null;
        return auctionService.rejectProvisionalWinner(
                id, currentUser.require(authentication), reason);
    }

    @PostMapping("/{id}/pickup")
    public Auction schedulePickup(
            @PathVariable Long id,
            @RequestBody PickupDetailsRequest request,
            Authentication authentication) {
        String location = request != null ? request.pickupLocation() : null;
        LocalDateTime time = request != null ? request.pickupTime() : null;
        String notes = request != null ? request.pickupNotes() : null;
        return auctionService.schedulePickup(
                id, currentUser.require(authentication), location, time, notes);
    }

    @PostMapping("/{id}/handover")
    public Auction confirmHandover(
            @PathVariable Long id,
            Authentication authentication) {
        return auctionService.confirmHandover(id, currentUser.require(authentication));
    }

    @PostMapping("/{id}/pickup-failed")
    public Auction recordFailedPickup(
            @PathVariable Long id,
            @RequestBody(required = false) ActionReasonRequest request,
            Authentication authentication) {
        String reason = request != null ? request.reason() : null;
        return auctionService.recordFailedPickup(
                id, currentUser.require(authentication), reason);
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

    public record ActionReasonRequest(String reason) {
    }

    public record PickupDetailsRequest(
            String pickupLocation,
            LocalDateTime pickupTime,
            String pickupNotes) {
    }
}
