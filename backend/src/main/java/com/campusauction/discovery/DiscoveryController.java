package com.campusauction.discovery;

import com.campusauction.auction.Auction;
import com.campusauction.auction.AuctionRepository;
import com.campusauction.auth.CurrentMarketplaceUser;
import com.campusauction.auth.MarketplaceUser;
import com.campusauction.notification.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DiscoveryController {

    private final SavedSearchRepository savedSearchRepository;
    private final AuctionQuestionRepository questionRepository;
    private final AuctionRepository auctionRepository;
    private final NotificationService notificationService;
    private final CurrentMarketplaceUser currentUser;

    public DiscoveryController(
            SavedSearchRepository savedSearchRepository,
            AuctionQuestionRepository questionRepository,
            AuctionRepository auctionRepository,
            NotificationService notificationService,
            CurrentMarketplaceUser currentUser) {
        this.savedSearchRepository = savedSearchRepository;
        this.questionRepository = questionRepository;
        this.auctionRepository = auctionRepository;
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    // --- Saved Searches ---

    @GetMapping("/saved-searches")
    public List<SavedSearch> getMySavedSearches(Authentication authentication) {
        return savedSearchRepository.findByUser_IdOrderByIdDesc(currentUser.require(authentication).getId());
    }

    @PostMapping("/saved-searches")
    @ResponseStatus(HttpStatus.CREATED)
    public SavedSearch createSavedSearch(
            @RequestBody SavedSearchRequest request,
            Authentication authentication) {
        MarketplaceUser user = currentUser.require(authentication);
        SavedSearch search = new SavedSearch(user, request.query(), request.category(), request.maxPrice());
        return savedSearchRepository.save(search);
    }

    @DeleteMapping("/saved-searches/{id}")
    public void deleteSavedSearch(@PathVariable Long id, Authentication authentication) {
        MarketplaceUser user = currentUser.require(authentication);
        SavedSearch search = savedSearchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved search not found"));
        if (!search.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to delete this saved search");
        }
        savedSearchRepository.delete(search);
    }

    // --- Questions and Answers ---

    @GetMapping("/auctions/{id}/questions")
    public List<AuctionQuestion> getQuestions(@PathVariable Long id) {
        return questionRepository.findByAuctionIdOrderByCreatedAtAsc(id);
    }

    @PostMapping("/auctions/{id}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AuctionQuestion askQuestion(
            @PathVariable Long id,
            @Valid @RequestBody AskQuestionRequest request,
            Authentication authentication) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found"));
        MarketplaceUser user = currentUser.require(authentication);

        AuctionQuestion question = new AuctionQuestion(id, user, request.questionText().trim());
        AuctionQuestion saved = questionRepository.save(question);

        // Notify seller if seller is a registered user and not the questioner
        if (auction.getSellerUserId() != null && !auction.getSellerUserId().equals(user.getId())) {
            auctionRepository.findById(id).ifPresent(a -> {
                // Fetch seller user reference if available
                notificationService.notifyUser(
                        null,
                        "New question on " + a.getTitle(),
                        user.getDisplayName() + " asked: \"" + request.questionText().trim() + "\"",
                        "/auctions/" + a.getId()
                );
            });
        }

        return saved;
    }

    @PostMapping("/auctions/{id}/questions/{questionId}/answer")
    @Transactional
    public AuctionQuestion answerQuestion(
            @PathVariable Long id,
            @PathVariable Long questionId,
            @Valid @RequestBody AnswerQuestionRequest request,
            Authentication authentication) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found"));
        MarketplaceUser seller = currentUser.require(authentication);

        if (auction.getSellerUserId() == null || !auction.getSellerUserId().equals(seller.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the listing seller can answer questions");
        }

        AuctionQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));

        if (!question.getAuctionId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question does not belong to this auction");
        }

        question.setAnswerText(request.answerText().trim());
        AuctionQuestion saved = questionRepository.save(question);

        // Notify questioner
        if (question.getQuestioner() != null) {
            notificationService.notifyUser(
                    question.getQuestioner(),
                    "Answer received on " + auction.getTitle(),
                    seller.getDisplayName() + " replied: \"" + request.answerText().trim() + "\"",
                    "/auctions/" + auction.getId()
            );
        }

        return saved;
    }

    public record SavedSearchRequest(String query, String category, BigDecimal maxPrice) {}

    public record AskQuestionRequest(@NotBlank @Size(max = 1000) String questionText) {}

    public record AnswerQuestionRequest(@NotBlank @Size(max = 2000) String answerText) {}
}
