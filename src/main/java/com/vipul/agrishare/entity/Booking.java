package com.vipul.agrishare.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    /** Statuses that hold the equipment's dates — a new booking may not overlap these. */
    public static final Set<Status> BLOCKING_STATUSES =
            EnumSet.of(Status.AWAITING_PAYMENT, Status.REQUESTED, Status.CONFIRMED);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "renter_id", nullable = false)
    private User renter;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Inclusive. */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private int days;

    @Column(name = "price_per_day", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerDay;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Column(name = "gateway_order_id", length = 64)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", length = 64)
    private String gatewayPaymentId;

    @Column(length = 500)
    private String note;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public boolean isRenter(Long userId) {
        return renter.getId().equals(userId);
    }

    public boolean isOwner(Long userId) {
        return equipment.getOwner().getId().equals(userId);
    }

    public enum Status {
        AWAITING_PAYMENT, REQUESTED, CONFIRMED, REJECTED, CANCELLED, COMPLETED, EXPIRED
    }

    public enum PaymentMethod {
        ONLINE, CASH
    }

    public enum PaymentStatus {
        /** Cash booking — settled in person. */
        NOT_REQUIRED,
        /** Gateway order created, renter hasn't paid yet. */
        PENDING,
        /** Money blocked on the renter's account, not yet taken (manual capture). */
        AUTHORIZED,
        /** Taken on owner approval. */
        CAPTURED,
        /** Authorization dropped (rejected/cancelled/expired) — gateway returns it automatically. */
        RELEASED,
        /** Captured money sent back. */
        REFUNDED
    }
}
