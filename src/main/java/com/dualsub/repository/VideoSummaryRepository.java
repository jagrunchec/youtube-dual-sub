package com.dualsub.repository;

import com.dualsub.model.VideoSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VideoSummaryRepository extends JpaRepository<VideoSummary, Long> {
    Optional<VideoSummary> findByVideoIdAndLangAndEngine(String videoId, String lang, String engine);
}
