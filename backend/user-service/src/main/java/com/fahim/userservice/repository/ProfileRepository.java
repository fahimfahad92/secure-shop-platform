package com.fahim.userservice.repository;

import com.fahim.userservice.model.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, String> {

    boolean existsByUsername(String username);
}
