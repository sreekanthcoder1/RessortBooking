package com.cottageai.repository;

import com.cottageai.entity.Cottage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CottageRepository extends JpaRepository<Cottage, Long> {

    Optional<Cottage> findByAdminId(Long adminId);

    boolean existsByAdminId(Long adminId);

    Page<Cottage> findByIsActiveTrueAndIsActivatedTrue(Pageable pageable);

    @Query("""
        SELECT c FROM Cottage c
        WHERE c.isActive = true AND c.isActivated = true
        AND (LOWER(c.name)     LIKE LOWER(CONCAT('%', :query, '%'))
          OR LOWER(c.location) LIKE LOWER(CONCAT('%', :query, '%')))
    """)
    Page<Cottage> searchCottages(@Param("query") String query, Pageable pageable);
}
