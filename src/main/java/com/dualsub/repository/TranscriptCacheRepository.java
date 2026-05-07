package com.dualsub.repository;

import com.dualsub.model.TranscriptCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TranscriptCacheRepository extends JpaRepository<TranscriptCache, String> {
    // PK is videoId (String) — use findById(videoId) / save()
}
