package com.campusauction.auction;

import com.campusauction.auth.MarketplaceUser;
import com.campusauction.discovery.SavedSearch;
import com.campusauction.discovery.SavedSearchRepository;
import com.campusauction.notification.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AuctionImageStorage imageStorage;
    private final NotificationService notificationService;
    private final SavedSearchRepository savedSearchRepository;

    public AuctionService(
            AuctionRepository auctionRepository,
            BidRepository bidRepository,
            AuctionImageStorage imageStorage,
            NotificationService notificationService,
            SavedSearchRepository savedSearchRepository) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.imageStorage = imageStorage;
        this.notificationService = notificationService;
        this.savedSearchRepository = savedSearchRepository;
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
        closeExpiredAuctions();
        return auctionRepository.findBySeller_IdOrderByIdDesc(sellerUserId);
    }

    @Transactional
    public Auction createAuction(AuctionRequest request, MarketplaceUser seller) {
        Auction auction = request.toAuction(seller);
        validateAuction(auction);
        auction.setStatus("ACTIVE");
        Auction saved = auctionRepository.save(auction);
        notifySavedSearches(saved);
        return saved;
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
                savedAuction = auctionRepository.save(savedAuction);
            } catch (RuntimeException exception) {
                try {
                    imageStorage.delete(imageKey);
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
                throw exception;
            }
        }
        notifySavedSearches(savedAuction);
        return savedAuction;
    }

    @Transactional
    public Auction updateAuction(Long id, AuctionUpdateRequest request, MarketplaceUser currentUser) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found"));

        if (auction.getSellerUserId() == null || !auction.getSellerUserId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this listing");
        }

        if (!"ACTIVE".equals(auction.getStatus())) {
            throw new IllegalArgumentException("Only active listings can be edited");
        }

        long bidCount = bidRepository.countByAuctionId(id);

        if (request.title() != null && !request.title().isBlank()) {
            auction.setTitle(request.title().trim());
        }
        if (request.category() != null && !request.category().isBlank()) {
            auction.setCategory(request.category().trim());
        }
        if (request.conditionLabel() != null && !request.conditionLabel().isBlank()) {
            auction.setConditionLabel(request.conditionLabel().trim());
        }
        if (request.description() != null && !request.description().isBlank()) {
            auction.setDescription(request.description().trim());
        }

        if (bidCount > 0) {
            if (request.startingPrice() != null && request.startingPrice().compareTo(auction.getStartingPrice()) != 0) {
                throw new IllegalArgumentException("Cannot change starting price after bids have been placed");
            }
            if (request.minIncrement() != null && request.minIncrement().compareTo(auction.getMinIncrement()) != 0) {
                throw new IllegalArgumentException("Cannot change minimum increment after bids have been placed");
            }
        } else {
            if (request.startingPrice() != null) {
                if (request.startingPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Starting price must be positive");
                }
                auction.setStartingPrice(request.startingPrice());
            }
            if (request.minIncrement() != null) {
                if (request.minIncrement().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Minimum increment must be positive");
                }
                auction.setMinIncrement(request.minIncrement());
            }
        }

        if (request.closingTime() != null) {
            if (!request.closingTime().isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("Closing time must be in the future");
            }
            auction.setClosingTime(request.closingTime());
        }

        return auctionRepository.save(auction);
    }

    @Transactional
    public Auction cancelAuction(Long id, MarketplaceUser currentUser, String reason) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found"));

        if (auction.getSellerUserId() == null || !auction.getSellerUserId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this listing");
        }

        if ("CANCELLED".equals(auction.getStatus()) || "COMPLETED".equals(auction.getStatus())) {
            throw new IllegalArgumentException("Listing cannot be cancelled in status " + auction.getStatus());
        }

        auction.setStatus("CANCELLED");
        auction.setSellerDecision("CANCELLED");
        if (reason != null && !reason.isBlank()) {
            auction.setPickupNotes(reason.trim());
        }

        // Notify leading bidder if exists
        List<Bid> bids = bidRepository.findByAuctionIdOrderByCreatedAtDesc(id);
        if (!bids.isEmpty() && bids.get(0).getBidder() != null) {
            notificationService.notifyUser(
                    bids.get(0).getBidder(),
                    "Auction Cancelled",
                    "The auction for \"" + auction.getTitle() + "\" was cancelled by the seller.",
                    "/auctions/" + auction.getId()
            );
        }

        return auctionRepository.save(auction);
    }

    @Transactional
    public Bid placeBid(Long auctionId, MarketplaceUser bidder, BigDecimal amount) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Auction not found"));

        LocalDateTime now = LocalDateTime.now();

        if (!"ACTIVE".equals(auction.getStatus()) || !auction.getClosingTime().isAfter(now)) {
            closeIfExpired(auction);
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
        Bid previousLeadingBid = bids.isEmpty() ? null : bids.get(0);
        if (previousLeadingBid != null) {
            minimum = previousLeadingBid.getAmount().add(auction.getMinIncrement());
        }

        if (amount == null || amount.compareTo(minimum) < 0) {
            throw new IllegalArgumentException(
                    "Minimum bid amount is " + minimum);
        }

        Bid bid = new Bid(auction, bidder, amount);
        Bid savedBid = bidRepository.save(bid);

        // Anti-sniping: extend by 60 seconds if bid arrives in the final 30 seconds
        if (auction.getClosingTime().isBefore(now.plusSeconds(30))) {
            auction.setClosingTime(now.plusSeconds(60));
            auctionRepository.save(auction);
        }

        // Send Outbid notification to previous leader if different user
        if (previousLeadingBid != null && previousLeadingBid.getBidder() != null
                && !previousLeadingBid.getBidder().getId().equals(bidder.getId())) {
            notificationService.notifyUser(
                    previousLeadingBid.getBidder(),
                    "You've been outbid!",
                    "Another student placed a new leading bid of ₹" + amount + " on \"" + auction.getTitle() + "\".",
                    "/auctions/" + auction.getId()
            );
        }

        // Send New Bid notification to seller
        if (auction.getSeller() != null && !auction.getSeller().getId().equals(bidder.getId())) {
            notificationService.notifyUser(
                    auction.getSeller(),
                    "New bid on your listing",
                    bidder.getDisplayName() + " placed a bid of ₹" + amount + " on \"" + auction.getTitle() + "\".",
                    "/auctions/" + auction.getId()
            );
        }

        return savedBid;
    }

    public List<Bid> getBids(Long auctionId) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Auction not found");
        }
        return bidRepository.findByAuctionIdOrderByCreatedAtDesc(auctionId);
    }

    @Transactional
    public Auction approveProvisionalWinner(Long auctionId, MarketplaceUser seller,
            String location, LocalDateTime time, String notes) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found"));

        verifySellerOwnership(auction, seller);

        if (!"ENDED_PENDING_SELLER".equals(auction.getStatus())) {
            throw new IllegalArgumentException("Auction is not awaiting seller decision");
        }

        auction.setStatus("RESERVED_PENDING_PICKUP");
        auction.setSellerDecision("APPROVED");
        if (location != null && !location.isBlank()) {
            auction.setPickupLocation(location.trim());
        }
        if (time != null) {
            auction.setPickupTime(time);
        }
        if (notes != null && !notes.isBlank()) {
            auction.setPickupNotes(notes.trim());
        }

        // Notify winning bidder
        if (auction.getWinningBidder() != null) {
            notificationService.notifyUser(
                    auction.getWinningBidder(),
                    "Winning Bid Approved!",
                    seller.getDisplayName() + " approved your winning bid of ₹" + auction.getWinningBidAmount()
                            + " for \"" + auction.getTitle() + "\". Campus pickup is ready to coordinate.",
                    "/auctions/" + auction.getId()
            );
        }

        return auctionRepository.save(auction);
    }

    @Transactional
    public Auction rejectProvisionalWinner(Long auctionId, MarketplaceUser seller, String reason) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found"));

        verifySellerOwnership(auction, seller);

        if (!"ENDED_PENDING_SELLER".equals(auction.getStatus())) {
            throw new IllegalArgumentException("Auction is not awaiting seller decision");
        }

        auction.setStatus("REJECTED");
        auction.setSellerDecision("REJECTED");
        if (reason != null && !reason.isBlank()) {
            auction.setPickupNotes(reason.trim());
        }

        // Notify winning bidder
        if (auction.getWinningBidder() != null) {
            notificationService.notifyUser(
                    auction.getWinningBidder(),
                    "Provisional Bid Declined",
                    "The seller declined the provisional winning bid for \"" + auction.getTitle() + "\".",
                    "/auctions/" + auction.getId()
            );
        }

        return auctionRepository.save(auction);
    }

    @Transactional
    public Auction schedulePickup(Long auctionId, MarketplaceUser user,
            String location, LocalDateTime time, String notes) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found"));

        boolean isAuthorized = (auction.getSellerUserId() != null && auction.getSellerUserId().equals(user.getId()))
                || (auction.getWinningBidderUserId() != null && auction.getWinningBidderUserId().equals(user.getId()));
        if (!isAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to update pickup for this auction");
        }

        if (!"RESERVED_PENDING_PICKUP".equals(auction.getStatus())) {
            throw new IllegalArgumentException("Pickup can only be scheduled for reserved items");
        }

        if (location != null && !location.isBlank()) {
            auction.setPickupLocation(location.trim());
        }
        if (time != null) {
            auction.setPickupTime(time);
        }
        if (notes != null && !notes.isBlank()) {
            auction.setPickupNotes(notes.trim());
        }

        // Notify other party
        MarketplaceUser otherParty = user.getId().equals(auction.getSellerUserId())
                ? auction.getWinningBidder()
                : auction.getSeller();
        if (otherParty != null) {
            notificationService.notifyUser(
                    otherParty,
                    "Pickup Details Updated",
                    user.getDisplayName() + " updated pickup details for \"" + auction.getTitle() + "\".",
                    "/auctions/" + auction.getId()
            );
        }

        return auctionRepository.save(auction);
    }

    @Transactional
    public Auction confirmHandover(Long auctionId, MarketplaceUser user) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found"));

        boolean isAuthorized = (auction.getSellerUserId() != null && auction.getSellerUserId().equals(user.getId()))
                || (auction.getWinningBidderUserId() != null && auction.getWinningBidderUserId().equals(user.getId()));
        if (!isAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to confirm handover for this auction");
        }

        if (!"RESERVED_PENDING_PICKUP".equals(auction.getStatus())) {
            throw new IllegalArgumentException("Handover can only be confirmed for reserved items");
        }

        auction.setStatus("COMPLETED");
        auction.setHandoverConfirmedAt(LocalDateTime.now());

        // Notify both parties
        if (auction.getSeller() != null) {
            notificationService.notifyUser(
                    auction.getSeller(),
                    "Campus Handover Completed!",
                    "Campus handover for \"" + auction.getTitle() + "\" is confirmed complete. Enjoy!",
                    "/auctions/" + auction.getId()
            );
        }
        if (auction.getWinningBidder() != null) {
            notificationService.notifyUser(
                    auction.getWinningBidder(),
                    "Campus Handover Completed!",
                    "Campus handover for \"" + auction.getTitle() + "\" is confirmed complete. Enjoy!",
                    "/auctions/" + auction.getId()
            );
        }

        return auctionRepository.save(auction);
    }

    @Transactional
    public Auction recordFailedPickup(Long auctionId, MarketplaceUser seller, String reason) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found"));

        verifySellerOwnership(auction, seller);

        if (!"RESERVED_PENDING_PICKUP".equals(auction.getStatus())) {
            throw new IllegalArgumentException("Only reserved pickups can be marked as failed");
        }

        auction.setStatus("FAILED_PICKUP");
        auction.setSellerDecision("FAILED_PICKUP");
        if (reason != null && !reason.isBlank()) {
            auction.setPickupNotes(reason.trim());
        }

        // Notify buyer
        if (auction.getWinningBidder() != null) {
            notificationService.notifyUser(
                    auction.getWinningBidder(),
                    "Pickup Outcome Recorded",
                    "Pickup for \"" + auction.getTitle() + "\" was marked as failed by the seller.",
                    "/auctions/" + auction.getId()
            );
        }

        return auctionRepository.save(auction);
    }

    private void notifySavedSearches(Auction auction) {
        try {
            List<SavedSearch> searches = savedSearchRepository.findAll();
            for (SavedSearch search : searches) {
                if (search.getUser() == null || (auction.getSellerUserId() != null && auction.getSellerUserId().equals(search.getUser().getId()))) {
                    continue;
                }
                boolean matchesCategory = search.getCategory() == null || search.getCategory().isBlank()
                        || search.getCategory().equalsIgnoreCase("All categories")
                        || search.getCategory().equalsIgnoreCase(auction.getCategory());
                boolean matchesPrice = search.getMaxPrice() == null
                        || auction.getStartingPrice().compareTo(search.getMaxPrice()) <= 0;
                boolean matchesQuery = search.getQuery() == null || search.getQuery().isBlank()
                        || (auction.getTitle() + " " + auction.getDescription()).toLowerCase(Locale.ROOT).contains(search.getQuery().toLowerCase(Locale.ROOT));

                if (matchesCategory && matchesPrice && matchesQuery) {
                    notificationService.notifyUser(
                            search.getUser(),
                            "Saved Search Alert",
                            "New item matching your search: \"" + auction.getTitle() + "\" at ₹" + auction.getStartingPrice(),
                            "/auctions/" + auction.getId()
                    );
                }
            }
        } catch (Exception ignored) {}
    }

    private void verifySellerOwnership(Auction auction, MarketplaceUser seller) {
        if (auction.getSellerUserId() == null || !auction.getSellerUserId().equals(seller.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this listing");
        }
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

    private void closeExpiredAuctions() {
        for (Auction auction : auctionRepository.findAll()) {
            closeIfExpired(auction);
        }
    }

    private void closeIfExpired(Auction auction) {
        if ("ACTIVE".equals(auction.getStatus())
                && !auction.getClosingTime().isAfter(LocalDateTime.now())) {
            List<Bid> bids = bidRepository.findByAuctionIdOrderByCreatedAtDesc(auction.getId());
            if (bids.isEmpty()) {
                auction.setStatus("UNSOLD");
                if (auction.getSeller() != null) {
                    notificationService.notifyUser(
                            auction.getSeller(),
                            "Auction Ended (Unsold)",
                            "Your auction for \"" + auction.getTitle() + "\" ended with no bids.",
                            "/auctions/" + auction.getId()
                    );
                }
            } else {
                auction.setStatus("ENDED_PENDING_SELLER");
                Bid winningBid = bids.get(0);
                auction.setWinningBidder(winningBid.getBidder());
                auction.setWinningBidderName(winningBid.getBidderName());
                auction.setWinningBidAmount(winningBid.getAmount());

                if (auction.getSeller() != null) {
                    notificationService.notifyUser(
                            auction.getSeller(),
                            "Auction Ended: Action Required",
                            "Your auction for \"" + auction.getTitle() + "\" ended with provisional winner "
                                    + winningBid.getBidderName() + " (₹" + winningBid.getAmount() + "). Review and approve now.",
                            "/auctions/" + auction.getId()
                    );
                }
                if (winningBid.getBidder() != null) {
                    notificationService.notifyUser(
                            winningBid.getBidder(),
                            "Leading Bidder on Ended Auction",
                            "You are the provisional winner of \"" + auction.getTitle() + "\" at ₹"
                                    + winningBid.getAmount() + "! Awaiting seller approval.",
                            "/auctions/" + auction.getId()
                    );
                }
            }
            auctionRepository.save(auction);
        }
    }
}
