package com.dualsub.repository;

import com.dualsub.model.DictionaryWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DictionaryWordRepository extends JpaRepository<DictionaryWord, Long> {

    Optional<DictionaryWord> findByUser_IdAndWordAndSourceLanguage(
            Long userId, String word, String sourceLanguage);

    List<DictionaryWord> findByUser_IdOrderByWordAsc(Long userId);

    @Query("DELETE FROM DictionaryWord dw WHERE dw.user.id = :userId")
    @org.springframework.data.jpa.repository.Modifying
    void deleteAllByUserId(@Param("userId") Long userId);
}
