package com.cottageai.repository;

import com.cottageai.entity.CottageAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Repository
public interface CottageAvailabilityRepository extends JpaRepository<CottageAvailability, Long> {

    @Modifying
    @Transactional
    @Query("DELETE FROM CottageAvailability a WHERE a.cottage.id = :cottageId AND a.date BETWEEN :from AND :to")
    void deleteByDateRange(@Param("cottageId") Long cottageId,
                           @Param("from") LocalDate from,
                           @Param("to") LocalDate to);

    @Modifying
    @Transactional
    @Query("DELETE FROM CottageAvailability a WHERE a.cottage.id = :cottageId")
    void deleteByCottageId(@Param("cottageId") Long cottageId);

    @Query("""
        SELECT COUNT(a) FROM CottageAvailability a
        WHERE a.cottage.id = :cottageId
          AND a.isBlocked = true
          AND a.date >= :checkIn
          AND a.date < :checkOut
    """)
    long countBlockedDates(@Param("cottageId") Long cottageId,
                           @Param("checkIn") LocalDate checkIn,
                           @Param("checkOut") LocalDate checkOut);
}
