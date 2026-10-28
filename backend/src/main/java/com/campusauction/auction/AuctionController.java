
package com.campusauction.auction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auctions")
@CrossOrigin(origins = "http://localhost:5173")
public class AuctionController {

    private final AuctionService auctionService;

    public AuctionController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @GetMapping
    public List<Auction> getAllAuctions() {
        return auctionService.getAllAuctions();
    }

    @GetMapping("/{id}")
    public Auction getAuction(@PathVariable Long id) {
        return auctionService.getAuction(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Auction createAuction(@Valid @RequestBody Auction auction) {
        return auctionService.createAuction(auction);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Auction createAuctionWithImage(
            @Valid @RequestPart("auction") Auction auction,
            @RequestPart("image") MultipartFile image) throws IOException {
        return auctionService.createAuction(auction, image);
    }

    @GetMapping("/{id}/bids")
    public List<Bid> getBids(@PathVariable Long id) {
        return auctionService.getBids(id);
    }

    @PostMapping("/{id}/bids")
    @ResponseStatus(HttpStatus.CREATED)
    public Bid placeBid(
            @PathVariable Long id,
            @Valid @RequestBody BidRequest request) {
        return auctionService.placeBid(
                id, request.bidderName(), request.amount());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(
            IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", exception.getMessage()));
    }

    public record BidRequest(
            @NotBlank String bidderName,
            @NotNull @DecimalMin("0.01") BigDecimal amount) {
    }
}
