
package com.campusauction.auction;

import com.campusauction.auth.MarketplaceUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AuctionImageStorage imageStorage;

    public AuctionService(
            AuctionRepository auctionRepository,
            BidRepository bidRepository,
            AuctionImageStorage imageStorage) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.imageStorage = imageStorage;
    }

    public List<Auction> getAllAuctions() {
        closeExpiredAuctions();
        return auctionRepository.findAll();
    }

    public Auction getAuction(Long id) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Auction not found"));
        closeIfExpired(auction);
        return auction;
    }

    public List<Auction> getAuctionsForSeller(Long sellerUserId) {
        return auctionRepository.findBySeller_IdOrderByIdDesc(sellerUserId);
    }

    @Transactional
    public Auction createAuction(AuctionRequest request, MarketplaceUser seller) {
        Auction auction = request.toAuction(seller);
        validateAuction(auction);
        auction.setStatus("ACTIVE");
        return auctionRepository.save(auction);
    }

    @Transactional(rollbackFor = IOException.class)
    public Auction createAuction(AuctionRequest request, MarketplaceUser seller,
            MultipartFile image) throws IOException {
        Auction auction = request.toAuction(seller);
        validateAuction(auction);
        auction.setStatus("ACTIVE");
        Auction savedAuction = auctionRepository.save(auction);
        if (image != null && !image.isEmpty()) {
            String imageKey = imageStorage.store(image);
            try {
                savedAuction.setImageUrl("/api/images/" + imageKey);
                return auctionRepository.save(savedAuction);
            } catch (RuntimeException exception) {
                try {
                    imageStorage.delete(imageKey);
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
                throw exception;
            }
        }
        return auctionRepository.save(savedAuction);
    }

    private void validateAuction(Auction auction) {
        if (auction.getTitle() == null || auction.getTitle().isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }

        if (auction.getStartingPrice() == null
                || auction.getStartingPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Starting price must be positive");
        }

        if (auction.getMinIncrement() == null
                || auction.getMinIncrement().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Minimum increment must be positive");
        }

        if (auction.getClosingTime() == null
                || !auction.getClosingTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Closing time must be in the future");
        }

    }

    @Transactional
    public Bid placeBid(Long auctionId, MarketplaceUser bidder, BigDecimal amount) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Auction not found"));

        if (auction.getClosingTime().isBefore(LocalDateTime.now())
                || auction.getStatus().equals("ENDED_PENDING_SELLER")
                || auction.getStatus().equals("CLOSED")) {
            auction.setStatus("ENDED_PENDING_SELLER");
            auctionRepository.save(auction);
            throw new IllegalArgumentException("Auction has ended");
        }

        boolean isSeller = auction.getSellerUserId() != null
                ? bidder.getId().equals(auction.getSellerUserId())
                : bidder.getDisplayName().equalsIgnoreCase(auction.getSellerName());
        if (isSeller) {
            throw new IllegalArgumentException("Sellers cannot bid on their own items");
        }

        List<Bid> bids = bidRepository.findByAuctionIdOrderByCreatedAtDesc(auctionId);

        BigDecimal minimum = auction.getStartingPrice();

        if (!bids.isEmpty()) {
            minimum = bids.get(0).getAmount().add(auction.getMinIncrement());
        }

        if (amount == null || amount.compareTo(minimum) < 0) {
            throw new IllegalArgumentException(
                    "Minimum bid amount is " + minimum);
        }

        Bid bid = new Bid(auction, bidder, amount);
        return bidRepository.save(bid);
    }

    public List<Bid> getBids(Long auctionId) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Auction not found");
        }
        return bidRepository.findByAuctionIdOrderByCreatedAtDesc(auctionId);
    }

    private void closeExpiredAuctions() {
        for (Auction auction : auctionRepository.findAll()) {
            closeIfExpired(auction);
        }
    }

    private void closeIfExpired(Auction auction) {
        if ("ACTIVE".equals(auction.getStatus())
                && !auction.getClosingTime().isAfter(LocalDateTime.now())) {
            auction.setStatus("ENDED_PENDING_SELLER");
            auctionRepository.save(auction);
        }
    }
}
