package com.dualsub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Canonical entry for a word the user wants to learn.
 * Unique per (user, word, source_language).
 */
@Entity
@Table(name = "dictionary_words",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_dw_user_word_lang",
           columnNames = {"user_id", "word", "source_language"}))
public class DictionaryWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String word;

    @Column(name = "source_language", length = 10, nullable = false)
    private String sourceLanguage;

    /**
     * Frequency rank in the source language (1 = most common word).
     * Lower values → higher learning priority. Null if not yet computed.
     */
    @Column(name = "frequency_rank")
    private Integer frequencyRank;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, orphanRemoval = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<DictionaryEntry> entries = new ArrayList<>();

    // ── getters / setters ─────────────────────────────────────────────────────

    public Long getId()                          { return id; }
    public void setId(Long id)                   { this.id = id; }

    public User getUser()                        { return user; }
    public void setUser(User user)               { this.user = user; }

    public String getWord()                      { return word; }
    public void setWord(String word)             { this.word = word; }

    public String getSourceLanguage()            { return sourceLanguage; }
    public void setSourceLanguage(String v)      { this.sourceLanguage = v; }

    public Integer getFrequencyRank()            { return frequencyRank; }
    public void setFrequencyRank(Integer v)      { this.frequencyRank = v; }

    public LocalDateTime getCreatedAt()          { return createdAt; }
    public void setCreatedAt(LocalDateTime v)    { this.createdAt = v; }

    public List<DictionaryEntry> getEntries()    { return entries; }
    public void setEntries(List<DictionaryEntry> e) { this.entries = e; }
}
