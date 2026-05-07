package com.dualsub.repository;

import com.dualsub.model.TranslationCache;
import com.dualsub.model.TranslationCacheId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TranslationCacheRepository extends JpaRepository<TranslationCache, TranslationCacheId> {
    // findById(new TranslationCacheId(videoId, lang)) / save()
}
