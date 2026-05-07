package com.dualsub.repository;

import com.dualsub.model.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {
    // Singleton: always use findById(1L) / save()
}
