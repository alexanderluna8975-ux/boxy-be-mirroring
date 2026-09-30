package com.boxy.boxy.modules.auth.repository;

import com.boxy.boxy.modules.auth.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    Optional<UserDevice> findByUserIdAndDeviceIdHash(Long userId, String deviceIdHash);

    /** Every device of this user first seen after {@code since}, excluding {@code excludeDeviceId}
     *  itself (the device making the current request never needs to alert about itself) — used to
     *  tell a known device "here's what's logged in since you were last seen". */
    @Query("SELECT d FROM UserDevice d WHERE d.user.id = :userId AND d.firstSeenAt > :since "
            + "AND d.id <> :excludeDeviceId ORDER BY d.firstSeenAt ASC")
    List<UserDevice> findNewSince(@Param("userId") Long userId, @Param("since") Instant since,
                                   @Param("excludeDeviceId") Long excludeDeviceId);
}
