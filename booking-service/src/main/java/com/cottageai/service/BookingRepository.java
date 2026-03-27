package com.cottageai.repository;

import com.cottageai.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Page<Booking> findByUserId(Long userId, Pageable pageable);

    Page<Booking> findByAdminId(Long adminId, Pageable pageable);

    /** Used for stats calculation — returns flat list, not paginated. */
    List<Booking> findAllByAdminId(Long adminId);

    Optional<Booking> findByBookingRef(String ref);

    @Query("""
        SELECT COUNT(b) FROM Booking b
        WHERE b.cottageId = :cottageId
          AND b.status IN ('PENDING', 'CONFIRMED')
          AND NOT (b.checkOut <= :checkIn OR b.checkIn >= :checkOut)
    """)
    long countConflictingBookings(@Param("cottageId") Long cottageId,
                                  @Param("checkIn") LocalDate checkIn,
                                  @Param("checkOut") LocalDate checkOut);
}
