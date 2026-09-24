# AgriShare — Peer-to-Peer Farm Equipment Sharing Platform

A backend (Spring Boot) + Android client for small and marginal farmers to
list, discover, and book idle agricultural equipment (rotavators, tillers,
seed drills, etc.) from nearby farmers, rather than relying solely on
government-run Custom Hiring Centres.

Built as the technical project behind a Tata Young Social Innovators
submission (Agriculture sector — equipment sharing).

## Status

🚧 Phase 1 in progress: project skeleton, Postgres + Flyway, `User` entity.

## Tech Stack

- Java 17, Spring Boot 3.3
- PostgreSQL 16 (+ `cube`/`earthdistance` for geo radius search)
- Flyway for schema migrations
- Spring Security + JWT (JJWT)
- Razorpay (manual-capture payment flow)
- Firebase Cloud Messaging (push notifications)
- JUnit 5, Mockito, Testcontainers
- Docker Compose (local Postgres)
- Android (Kotlin) client — separate module

## Local setup

```bash
# 1. Start Postgres
docker compose up -d

# 2. Run the app (Flyway migrations run automatically on startup)
./mvnw spring-boot:run

# 3. API docs
# http://localhost:8080/swagger-ui.html
```

## Architecture

```
Android App (Kotlin)
        │
    REST / JSON
        │
        ▼
 Spring Boot (Controller → Service → Repository)
        │
   ┌────┴─────┐
   ▼          ▼
PostgreSQL   Firebase (FCM)
   │
Users / Equipment / Bookings / Payments / Reviews
```

## Design notes

- **Pessimistic locking** on booking-slot conflict checks (vs. optimistic
  locking used in a separate e-commerce project) — chosen because booking
  conflicts are low-frequency but high-cost if missed.
- **`BigDecimal`** for all monetary fields — never `double`/`float`.
- **Payments**: Razorpay orders are created with manual capture. Razorpay
  auto-refunds any payment left in the `authorized` state for more than
  5 days, so a scheduled job reconciles stale `PENDING` bookings against
  this window.
- **Ratings** are computed from `Review` via aggregate query initially;
  denormalizing onto `User` is deferred until there's a measured reason to.
- Differentiator vs. the Government's CHC-Farm Machinery / FARMS app:
  that platform connects farmers to *institutional* Custom Hiring Centres.
  This project targets *informal, farmer-to-farmer* sharing of privately
  owned equipment, which isn't covered by the existing system.

## Roadmap

- [x] Project skeleton, Docker Compose, Flyway baseline
- [x] `User` entity + repository
- [ ] Spring Security + JWT auth
- [ ] Equipment CRUD
- [ ] Geo radius search (`earthdistance`)
- [ ] Booking system + slot-conflict locking
- [ ] Razorpay integration
- [ ] FCM push notifications
- [ ] Reviews + ratings
- [ ] Testcontainers integration tests
- [ ] Android client

