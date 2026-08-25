package com.boxy.boxy.modules.administration.repository;

import com.boxy.boxy.modules.administration.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsernameAndDeletedAtIsNull(String username);
    Optional<User> findByEmailAndDeletedAtIsNull(String email);
    List<User> findByCompanyIdAndDeletedAtIsNull(String companyId);
    Optional<User> findByIdAndDeletedAtIsNull(String id);

    @Query("SELECT u FROM User u WHERE (u.username = :identifier OR u.email = :identifier) AND u.deletedAt IS NULL")
    Optional<User> findByUsernameOrEmail(@Param("identifier") String identifier);
}