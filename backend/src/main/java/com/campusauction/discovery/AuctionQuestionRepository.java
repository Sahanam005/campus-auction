package com.campusauction.discovery;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuctionQuestionRepository extends JpaRepository<AuctionQuestion, Long> {
    List<AuctionQuestion> findByAuctionIdOrderByCreatedAtAsc(Long auctionId);
}
