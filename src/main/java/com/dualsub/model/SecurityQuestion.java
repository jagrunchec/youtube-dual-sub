package com.dualsub.model;

import jakarta.persistence.*;

@Entity
@Table(name = "security_questions")
public class SecurityQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** One of the predefined question keys (see AuthController.QUESTIONS). */
    @Column(name = "question_key", nullable = false, length = 50)
    private String questionKey;

    /** BCrypt hash of the normalised answer (lowercase + trimmed). */
    @Column(name = "answer_hash", nullable = false)
    private String answerHash;

    /** 1-based display order. */
    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    public Long getId()                              { return id; }
    public void setId(Long id)                       { this.id = id; }

    public User getUser()                            { return user; }
    public void setUser(User user)                   { this.user = user; }

    public String getQuestionKey()                   { return questionKey; }
    public void setQuestionKey(String questionKey)   { this.questionKey = questionKey; }

    public String getAnswerHash()                    { return answerHash; }
    public void setAnswerHash(String answerHash)     { this.answerHash = answerHash; }

    public int getQuestionOrder()                    { return questionOrder; }
    public void setQuestionOrder(int questionOrder)  { this.questionOrder = questionOrder; }
}
