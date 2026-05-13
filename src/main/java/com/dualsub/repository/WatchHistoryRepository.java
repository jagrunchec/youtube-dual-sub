package com.dualsub.repository;

import com.dualsub.model.WatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {

    /** Returns the 20 most recent watch events, newest first. */
    List<WatchHistory> findTop20ByOrderByWatchedAtDesc();

    /** Returns the most recent entry for a given video+user pair (used for per-user upsert). */
    java.util.Optional<WatchHistory> findTopByVideoIdAndUser_IdOrderByWatchedAtDesc(String videoId, Long userId);

    /** Returns the most recent entry for a given video regardless of user (anonymous upsert). */
    java.util.Optional<WatchHistory> findTopByVideoIdAndUserIsNullOrderByWatchedAtDesc(String videoId);

    /** Returns all watch events for a given user, newest first. */
    List<WatchHistory> findByUser_IdOrderByWatchedAtDesc(Long userId);
}
