package com.campusauction.discovery;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {
    List<SavedSearch> findByUser_IdOrderByIdDesc(Long userId);
}
