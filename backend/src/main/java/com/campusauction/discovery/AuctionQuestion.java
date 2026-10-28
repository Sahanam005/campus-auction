package com.campusauction.discovery;

import com.campusauction.auth.MarketplaceUser;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auction_questions")
public class AuctionQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long auctionId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "questioner_user_id")
    private MarketplaceUser questioner;

    @Column(nullable = false, length = 100)
    private String questionerName;

    @Column(nullable = false, length = 1000)
    private String questionText;

    @Column(length = 2000)
    private String answerText;

    private LocalDateTime answeredAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AuctionQuestion() {}

    public AuctionQuestion(Long auctionId, MarketplaceUser questioner, String questionText) {
        this.auctionId = auctionId;
        this.questioner = questioner;
        this.questionerName = questioner.getDisplayName();
        this.questionText = questionText;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getAuctionId() { return auctionId; }
    public String getQuestionerName() { return questionerName; }
    public String getQuestionText() { return questionText; }
    public String getAnswerText() { return answerText; }
    public LocalDateTime getAnsweredAt() { return answeredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @JsonIgnore
    public MarketplaceUser getQuestioner() { return questioner; }
    public Long getQuestionerUserId() { return questioner == null ? null : questioner.getId(); }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
        this.answeredAt = LocalDateTime.now();
    }
}
