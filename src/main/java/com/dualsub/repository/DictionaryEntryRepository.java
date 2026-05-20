package com.dualsub.repository;

import com.dualsub.model.DictionaryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DictionaryEntryRepository extends JpaRepository<DictionaryEntry, Long> {

    Optional<DictionaryEntry> findByUser_IdAndWord_IdAndVideoId(
            Long userId, Long wordId, String videoId);

    /** All entries for a user, newest first. */
    List<DictionaryEntry> findByUser_IdOrderByCreatedAtDesc(Long userId);

    /** Entries for a specific video. */
    List<DictionaryEntry> findByUser_IdAndVideoIdOrderByCreatedAtDesc(
            Long userId, String videoId);

    /** Entries created on or after a given date. */
    List<DictionaryEntry> findByUser_IdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            Long userId, LocalDateTime from);

    /** Entries for a specific word (all videos). */
    List<DictionaryEntry> findByWord_IdAndUser_Id(Long wordId, Long userId);

    /** Entries whose word carries a given tag. */
    @Query("SELECT e FROM DictionaryEntry e WHERE e.user.id = :userId " +
           "AND :tag MEMBER OF e.word.tags ORDER BY e.createdAt DESC")
    List<DictionaryEntry> findByUserIdAndTag(@Param("userId") Long userId,
                                             @Param("tag") String tag);

    @Modifying
    @Query("DELETE FROM DictionaryEntry de WHERE de.user.id = :userId AND de.videoId = :videoId")
    void deleteByUserIdAndVideoId(@Param("userId") Long userId, @Param("videoId") String videoId);

    @Modifying
    @Query("DELETE FROM DictionaryEntry de WHERE de.user.id = :userId AND de.createdAt < :before")
    void deleteByUserIdAndCreatedAtBefore(@Param("userId") Long userId,
                                          @Param("before") LocalDateTime before);

    @Modifying
    @Query("DELETE FROM DictionaryEntry de WHERE de.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
