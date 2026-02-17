package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);

    Optional<AppUser> findByEmail(String email);

    boolean existsByOauthProviderAndOauthId(String oauthProvider, String oauthId);
}
