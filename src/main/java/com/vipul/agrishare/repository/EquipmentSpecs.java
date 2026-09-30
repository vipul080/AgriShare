package com.vipul.agrishare.repository;

import com.vipul.agrishare.entity.Equipment;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/** Composable filters for browsing equipment when the user hasn't shared a location. */
public final class EquipmentSpecs {

    private EquipmentSpecs() {
    }

    public static Specification<Equipment> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Equipment> hasCategory(Equipment.Category category) {
        return (root, query, cb) -> category == null ? null : cb.equal(root.get("category"), category);
    }

    public static Specification<Equipment> matches(String text) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(text)) {
                return null;
            }
            String pattern = "%" + text.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("address")), pattern)
            );
        };
    }
}
