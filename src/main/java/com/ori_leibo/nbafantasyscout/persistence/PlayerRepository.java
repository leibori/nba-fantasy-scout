package com.ori_leibo.nbafantasyscout.persistence;

import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PlayerRepository extends JpaRepository<PlayerEntity, Long> {

    /**
     * Players missing from today's rosters (waived, retired) lose their team; a null team means "not on a roster".
     * JPQL bulk UPDATE over entity names. {@code @Transactional} because repository query methods
     * run read-only by default, and Postgres refuses an UPDATE in a read-only transaction.
     */
    @Transactional
    @Modifying
    @Query("UPDATE PlayerEntity p SET p.team = null WHERE p.team IS NOT NULL AND p.id NOT IN :rosterIds")
    int clearTeamExcept(@Param("rosterIds") Collection<Long> rosterIds);
}
