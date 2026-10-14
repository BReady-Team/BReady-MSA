package com.bready.server.plan.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bready.server.plan.domain.Plan;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    Page<Plan> findAllByOwnerIdAndDeletedAtIsNull(Long ownerId, Pageable pageable);

    @Query(
            """
                select p
                from Plan p
                where p.id = :planId
                  and p.ownerId = :ownerId
            """)
    Optional<Plan> findByIdAndOwnerId(@Param("planId") Long planId, @Param("ownerId") Long ownerId);

    @Query(
            """
        select count(p)
            from Plan p
        where p.ownerId = :ownerId
            and p.deletedAt is null
    """)
    long countActiveByOwnerId(@Param("ownerId") Long ownerId);

    // 조회용 - 락 없음
    Optional<Plan> findByIdAndDeletedAtIsNull(Long id);

    Optional<Plan> findByShareTokenAndDeletedAtIsNull(String shareToken);

    // 락 전용 - 카테고리 추가(addCategory)에서만 사용
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select p
    from Plan p
    where p.id = :id
      and p.deletedAt is null
""")
    Optional<Plan> findByIdAndDeletedAtIsNullForUpdate(@Param("id") Long id);
}
