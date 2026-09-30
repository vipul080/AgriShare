package com.vipul.agrishare.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Optional: many small farmers don't use email, so phone is the primary login. */
    @Column(unique = true, length = 160)
    private String email;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.BOTH;

    private Double latitude;

    private Double longitude;

    private String address;

    /** ISO code of the UI language the farmer picked (en, hi, pa, mr, gu, bn, ta, te, kn). */
    @Column(name = "preferred_language", nullable = false, length = 8)
    @Builder.Default
    private String preferredLanguage = "en";

    /** Firebase Cloud Messaging device token, registered by the Android app. */
    @Column(name = "fcm_token", length = 512)
    private String fcmToken;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum Role {
        OWNER, RENTER, BOTH
    }
}
