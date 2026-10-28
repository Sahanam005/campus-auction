package com.campusauction.assistant;

import com.campusauction.auction.Auction;
import com.campusauction.auction.AuctionRepository;
import com.campusauction.auction.Bid;
import com.campusauction.auction.BidRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AssistantService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;

    public AssistantService(AuctionRepository auctionRepository, BidRepository bidRepository) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
    }

    public AssistantResponse processQuery(String userMessage, BigDecimal statedBudget, Long contextAuctionId) {
        String msg = userMessage != null ? userMessage.trim().toLowerCase(Locale.ROOT) : "";

        // Extract budget from message if stated e.g. "budget is 500" or "under 1000" or "within 300"
        BigDecimal effectiveBudget = statedBudget;
        Pattern budgetPattern = Pattern.compile("(?:budget\\s*(?:is|of)?\\s*(?:₹|rs\\.?)?\\s*|under\\s*(?:₹|rs\\.?)?\\s*|below\\s*(?:₹|rs\\.?)?\\s*|within\\s*(?:₹|rs\\.?)?\\s*)(\\d+(?:\\.\\d{1,2})?)");
        Matcher budgetMatcher = budgetPattern.matcher(msg);
        if (budgetMatcher.find()) {
            try {
                effectiveBudget = new BigDecimal(budgetMatcher.group(1));
            } catch (Exception ignored) {}
        }

        // Check if query is asking about bidding rules
        boolean askingRules = msg.contains("rule") || msg.contains("how to bid") || msg.contains("how it work")
                || msg.contains("increment") || msg.contains("snip") || msg.contains("extension")
                || msg.contains("close") || msg.contains("pickup") || msg.contains("handover");

        // Check if query is asking to place a bid on an item
        boolean askingToBid = (msg.contains("bid") || msg.contains("offer") || msg.contains("place"))
                && (contextAuctionId != null || containsAuctionReference(msg));

        List<Auction> activeAuctions = auctionRepository.findAll().stream()
                .filter(a -> "ACTIVE".equals(a.getStatus()) && a.getClosingTime().isAfter(LocalDateTime.now()))
                .collect(Collectors.toList());

        // 1. Handle Bidding Intent
        if (askingToBid) {
            Auction targetAuction = null;
            if (contextAuctionId != null) {
                targetAuction = auctionRepository.findById(contextAuctionId).orElse(null);
            }
            if (targetAuction == null) {
                targetAuction = findAuctionMentioned(msg, activeAuctions);
            }

            if (targetAuction != null) {
                return generateBidProposalResponse(targetAuction, effectiveBudget, msg);
            }
        }

        // 2. Handle Rules Inquiry
        if (askingRules) {
            String rulesExplanation = "Here are the official Campus Auction bidding rules:\n\n"
                    + "• Minimum Bid Increments: Every new bid must meet or exceed the starting price, or the previous highest bid plus the item's minimum increment.\n"
                    + "• Anti-Sniping Protection: If a valid bid is placed in the final 30 seconds of an auction, the closing time is automatically extended by 60 seconds from that bid to give everyone a fair chance.\n"
                    + "• Seller Decisions: When an auction ends with bids, the seller reviews and approves the winning bidder before reserving the item.\n"
                    + "• Campus Handover: Once approved, buyer and seller coordinate an in-person handover on campus with no shipping fees.\n"
                    + "• Budget Safety: You can state your budget at any time, and I will strictly prevent you from bidding beyond your limit.";

            return new AssistantResponse(
                    "Here is a summary of how bidding works on campus:",
                    activeAuctions.stream().limit(3).toList(),
                    null,
                    rulesExplanation,
                    effectiveBudget
            );
        }

        // 3. Search and Discovery from REAL database items
        List<Auction> matchedListings = filterRealListings(activeAuctions, msg, effectiveBudget);

        StringBuilder reply = new StringBuilder();
        if (matchedListings.isEmpty()) {
            if (effectiveBudget != null) {
                reply.append("I searched the live campus exchange, but found no active listings within your stated budget of ₹")
                        .append(effectiveBudget).append(". ");
            } else {
                reply.append("I searched all live campus listings, but found no items matching your request right now. ");
            }
            reply.append("Never fear: new items are listed by campus members daily! Would you like to save this search or explore all active listings?");
        } else {
            reply.append("I found ").append(matchedListings.size())
                    .append(" real active listing").append(matchedListings.size() == 1 ? "" : "s")
                    .append(" on campus");
            if (effectiveBudget != null) {
                reply.append(" within your budget of ₹").append(effectiveBudget);
            }
            reply.append(". Here are the best matches from the live marketplace:");
        }

        return new AssistantResponse(
                reply.toString(),
                matchedListings,
                null,
                null,
                effectiveBudget
        );
    }

    private AssistantResponse generateBidProposalResponse(Auction auction, BigDecimal statedBudget, String msg) {
        List<Bid> bids = bidRepository.findByAuctionIdOrderByCreatedAtDesc(auction.getId());
        BigDecimal currentPrice = bids.isEmpty() ? auction.getStartingPrice() : bids.get(0).getAmount();
        BigDecimal nextMinBid = bids.isEmpty() ? auction.getStartingPrice() : currentPrice.add(auction.getMinIncrement());

        // Check if user specified a specific bid amount e.g. "bid 350"
        BigDecimal proposedAmount = nextMinBid;
        Pattern amountPattern = Pattern.compile("(?:bid|offer|place)?\\s*(?:₹|rs\\.?)?\\s*(\\d+(?:\\.\\d{1,2})?)");
        Matcher m = amountPattern.matcher(msg);
        while (m.find()) {
            try {
                BigDecimal candidate = new BigDecimal(m.group(1));
                if (candidate.compareTo(nextMinBid) >= 0) {
                    proposedAmount = candidate;
                }
            } catch (Exception ignored) {}
        }

        // Enforce stated budget
        boolean exceedsBudget = statedBudget != null && proposedAmount.compareTo(statedBudget) > 0;

        if (exceedsBudget) {
            String reply = "Budget Warning: The next valid bid for \"" + auction.getTitle() + "\" is ₹" + proposedAmount
                    + ", which exceeds your stated budget of ₹" + statedBudget + ". "
                    + "To protect your budget, I cannot place this bid. You can adjust your budget if you wish to proceed.";

            return new AssistantResponse(
                    reply,
                    List.of(auction),
                    new BidProposal(auction.getId(), auction.getTitle(), currentPrice, auction.getMinIncrement(),
                            proposedAmount, statedBudget, false, true),
                    "Your stated budget is strictly enforced.",
                    statedBudget
            );
        }

        String reply = "I found the listing for \"" + auction.getTitle() + "\". "
                + "The current price is ₹" + currentPrice + " (minimum next bid is ₹" + nextMinBid + "). "
                + "I have prepared a bid of ₹" + proposedAmount + " within your budget of ₹"
                + (statedBudget != null ? statedBudget : proposedAmount) + ". "
                + "Please click below to explicitly confirm and place your bid:";

        return new AssistantResponse(
                reply,
                List.of(auction),
                new BidProposal(auction.getId(), auction.getTitle(), currentPrice, auction.getMinIncrement(),
                        proposedAmount, statedBudget, true, true),
                null,
                statedBudget
        );
    }

    private List<Auction> filterRealListings(List<Auction> active, String query, BigDecimal maxPrice) {
        return active.stream().filter(a -> {
            boolean matchesKeyword = true;
            if (!query.isBlank()) {
                String fullText = (a.getTitle() + " " + a.getDescription() + " " + a.getCategory() + " " + a.getConditionLabel()).toLowerCase(Locale.ROOT);
                String[] words = query.split("\\s+");
                matchesKeyword = Arrays.stream(words)
                        .filter(w -> !isCommonStopWord(w))
                        .anyMatch(fullText::contains);
            }

            BigDecimal currentPrice = getCurrentPrice(a);
            boolean matchesPrice = maxPrice == null || currentPrice.compareTo(maxPrice) <= 0;
            return matchesKeyword && matchesPrice;
        }).collect(Collectors.toList());
    }

    private BigDecimal getCurrentPrice(Auction a) {
        List<Bid> bids = bidRepository.findByAuctionIdOrderByCreatedAtDesc(a.getId());
        return bids.isEmpty() ? a.getStartingPrice() : bids.get(0).getAmount();
    }

    private boolean containsAuctionReference(String msg) {
        return msg.contains("item") || msg.contains("calculator") || msg.contains("cycle") || msg.contains("book") || msg.contains("laptop");
    }

    private Auction findAuctionMentioned(String msg, List<Auction> auctions) {
        for (Auction a : auctions) {
            if (msg.contains(a.getTitle().toLowerCase(Locale.ROOT)) || msg.contains(a.getCategory().toLowerCase(Locale.ROOT))) {
                return a;
            }
        }
        return auctions.isEmpty() ? null : auctions.get(0);
    }

    private boolean isCommonStopWord(String w) {
        return Set.of("the", "a", "an", "is", "for", "in", "to", "and", "me", "find", "show", "get", "any", "some", "what", "are", "i", "want", "need", "under", "budget", "within", "below").contains(w);
    }

    public record AssistantResponse(
            String reply,
            List<Auction> listings,
            BidProposal proposedAction,
            String explanation,
            BigDecimal statedBudget
    ) {}

    public record BidProposal(
            Long auctionId,
            String auctionTitle,
            BigDecimal currentPrice,
            BigDecimal minIncrement,
            BigDecimal proposedAmount,
            BigDecimal statedBudget,
            boolean withinBudget,
            boolean requiresConfirmation
    ) {}
}
