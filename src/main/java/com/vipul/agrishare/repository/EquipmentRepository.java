package com.vipul.agrishare.repository;

import com.vipul.agrishare.entity.Equipment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long>, JpaSpecificationExecutor<Equipment> {

    /**
     * Row-level lock (SELECT ... FOR UPDATE) on the equipment being booked.
     * Every booking attempt for the same machine serialises on this lock, so
     * two farmers can never both pass the "is this slot free?" check at once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Equipment e where e.id = :id")
    Optional<Equipment> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "owner")
    @Query("select e from Equipment e where e.id = :id and e.active = true")
    Optional<Equipment> findActiveById(@Param("id") Long id);

    @EntityGraph(attributePaths = "owner")
    List<Equipment> findByOwnerIdAndActiveTrueOrderByCreatedAtDesc(Long ownerId);

    @EntityGraph(attributePaths = "owner")
    List<Equipment> findByIdIn(Collection<Long> ids);

    long countByActiveTrue();

    List<Equipment> findByOwnerIdAndActiveTrue(Long ownerId);

    /**
     * Radius search using Postgres cube + earthdistance.
     * earth_box(...) @> ll_to_earth(...) is a fast, index-assisted bounding-box
     * pre-filter (GiST index idx_equipment_location); earth_distance(...) then
     * trims the box corners to a true circle.
     */
    @Query(value = """
            SELECT e.id AS id,
                   earth_distance(ll_to_earth(:lat, :lng), ll_to_earth(e.latitude, e.longitude)) / 1000.0 AS distanceKm
            FROM equipment e
            WHERE e.active = TRUE
              AND earth_box(ll_to_earth(:lat, :lng), :radius) @> ll_to_earth(e.latitude, e.longitude)
              AND earth_distance(ll_to_earth(:lat, :lng), ll_to_earth(e.latitude, e.longitude)) <= :radius
              AND (CAST(:category AS VARCHAR) IS NULL OR e.category = CAST(:category AS VARCHAR))
              AND (CAST(:q AS VARCHAR) IS NULL
                   OR e.name ILIKE CONCAT('%', CAST(:q AS VARCHAR), '%')
                   OR e.description ILIKE CONCAT('%', CAST(:q AS VARCHAR), '%'))
            ORDER BY distanceKm
            LIMIT :limit
            """, nativeQuery = true)
    List<EquipmentDistance> searchNearby(@Param("lat") double lat,
                                         @Param("lng") double lng,
                                         @Param("radius") double radiusMeters,
                                         @Param("category") String category,
                                         @Param("q") String query,
                                         @Param("limit") int limit);

    interface EquipmentDistance {
        Long getId();

        Double getDistanceKm();
    }
}
