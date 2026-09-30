package com.vipul.agrishare.repository;

import com.vipul.agrishare.entity.Booking;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Inclusive day ranges overlap when each starts on/before the other ends. */
    @Query("""
            select count(b) > 0 from Booking b
            where b.equipment.id = :equipmentId
              and b.status in :statuses
              and b.startDate <= :endDate
              and b.endDate >= :startDate
            """)
    boolean existsOverlap(@Param("equipmentId") Long equipmentId,
                          @Param("startDate") LocalDate startDate,
                          @Param("endDate") LocalDate endDate,
                          @Param("statuses") Collection<Booking.Status> statuses);

    @Query("""
            select b from Booking b
            where b.equipment.id = :equipmentId
              and b.status in :statuses
              and b.endDate >= :from
            order by b.startDate
            """)
    List<Booking> findBlocking(@Param("equipmentId") Long equipmentId,
                               @Param("from") LocalDate from,
                               @Param("statuses") Collection<Booking.Status> statuses);

    @EntityGraph(attributePaths = {"equipment", "equipment.owner", "renter"})
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"equipment", "equipment.owner", "renter"})
    List<Booking> findByRenterIdOrderByCreatedAtDesc(Long renterId);

    @EntityGraph(attributePaths = {"equipment", "equipment.owner", "renter"})
    List<Booking> findByEquipmentOwnerIdOrderByCreatedAtDesc(Long ownerId);

    /** Expiry job: renter never finished paying. */
    List<Booking> findByStatusAndCreatedAtBefore(Booking.Status status, Instant cutoff);

    /** Expiry job: owner never answered. */
    List<Booking> findByStatusAndRequestedAtBefore(Booking.Status status, Instant cutoff);

    long countByStatus(Booking.Status status);

    /** Open bookings on either side block account deletion. */
    @Query("""
            select count(b) > 0 from Booking b
            where b.status in :statuses
              and (b.renter.id = :userId or b.equipment.owner.id = :userId)
            """)
    boolean existsOpenForUser(@Param("userId") Long userId, @Param("statuses") Collection<Booking.Status> statuses);
}
