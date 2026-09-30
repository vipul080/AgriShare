package com.vipul.agrishare.repository;

import com.vipul.agrishare.entity.Boost;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BoostRepository extends JpaRepository<Boost, Long> {

    @EntityGraph(attributePaths = {"equipment", "equipment.owner", "owner"})
    @Query("select b from Boost b where b.id = :id")
    Optional<Boost> findDetailedById(@Param("id") Long id);
}
