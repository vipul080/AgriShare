package com.vipul.agrishare.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Type type;

    /** Placeholder values for the translated text, e.g. {"equipment": "Mahindra 575"}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> params = new HashMap<>();

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum Type {
        /** To owner: a renter asked for their machine. */
        BOOKING_REQUESTED,
        /** To renter. */
        BOOKING_CONFIRMED,
        BOOKING_REJECTED,
        /** To the other party. */
        BOOKING_CANCELLED,
        /** To renter: prompt for a review. */
        BOOKING_COMPLETED,
        /** To whoever was waiting. */
        BOOKING_EXPIRED
    }
}
