# AgriShare — notes for Claude Code

Peer-to-peer farm equipment sharing (farmer-to-farmer rentals of tractors,
rotavators, seed drills…) for small/marginal farmers in India. Built for a
Tata Young Social Innovators submission. Spring Boot backend + web UI served
by Spring Boot now; Android (Kotlin) client later, calling the same REST API.

## Working agreements
- Never add a `Co-Authored-By: Claude` (or any AI attribution) trailer to commits.
- Commit + push to GitHub (`origin` = https://github.com/vipul080/AgriShare, branch `main`)
  after each completed feature, one feature per commit. Author: Vipul <vipulshukla191@gmail.com>.
- Schema is owned by Flyway (`ddl-auto: validate`). Never edit an applied migration; add a new `V{n}__*.sql`.
- Money is always `BigDecimal` / `NUMERIC`. Never double/float.
- Secrets (JWT, Razorpay) only via env vars — never commit them.
- Error messages are i18n keys (`ApiException.badRequest("error.x")`), resolved in
  `GlobalExceptionHandler` via `MessageSource` + `Accept-Language`. Every new key must be
  added to `messages.properties` AND all 8 regional files.

## Status
Done (committed + pushed):
- JWT auth. Login by phone OR email (`identifier`); JWT subject = user id.
- V2: `users.role` changed from Postgres ENUM to VARCHAR (Hibernate validate fix).
- V3 equipment + radius search, image upload. i18n bundles (9 languages) + validator wired to MessageSource;
  `MessagesConsistencyTest` fails if any regional bundle drifts from `messages.properties`.
- Roadmap 2+3: V4 bookings, `BookingService`, `PaymentGateway` (mock default / Razorpay manual capture).
- Roadmap 4: `BookingExpiryJob` (every 5 min).
- Roadmap 5: V5 notifications (type + JSONB params), `NotificationText` renders named placeholders,
  push sent AFTER_COMMIT via `PushSender` (`LoggingPushSender` unless `app.push.mode=fcm`).
- Roadmap 7: GET/PUT /api/users/me, PUT /me/password, DELETE /me (password-confirmed, blocked by open
  bookings, anonymises the row via V7 `deleted_at`, takes listings down), GET /api/stats/public.
- Roadmap 8: GET /api/i18n (languages + native names), GET /api/i18n/{lang} (English base overlaid
  with the language, cached 1 h). UI strings go in the same bundles under `ui.*`.
- Roadmap 6: V6 two-way reviews (renter->owner+machine, owner->renter); `RatingService` aggregates on read.

Play Store target: in-app account deletion is done (web deletion page still needed with the UI),
a public privacy policy page (/privacy, roadmap 9), an FCM `PushSender`, production deploy notes.

Build: no `mvnw` in repo; Maven lives at `~/.m2/wrapper/dists/apache-maven-3.9.16/*/bin/mvn`.
Local run: Postgres 18 (role/db `agrishare`, extensions created as superuser), `PORT=8081` because
Oracle's TNSLSNR holds 8080. V1–V5 applied and a 23-step end-to-end smoke test passed (2026-10-01).

## Remaining roadmap (in order, commit each)
1. Compile + fix; add `messages.properties` with all `error.*` and `validation.*` keys used in code.
   Configure `LocalValidatorFactoryBean.setValidationMessageSource(messageSource)` so `{validation.x}` resolve.
2. Bookings: statuses AWAITING_PAYMENT → REQUESTED → CONFIRMED / REJECTED / CANCELLED / COMPLETED / EXPIRED.
   Day-based `start_date`/`end_date` inclusive. On create: `equipmentRepository.findByIdForUpdate`
   (PESSIMISTIC_WRITE) then overlap check against AWAITING_PAYMENT/REQUESTED/CONFIRMED.
   Payment method ONLINE (Razorpay) or CASH (skips payment → REQUESTED).
   Endpoints: POST /api/bookings, POST /{id}/payment/verify, /approve, /reject, /cancel, /complete,
   GET /mine, GET /incoming, GET /api/equipment/{id}/booked-dates. Show phone numbers to both parties once CONFIRMED.
3. Payments: `PaymentGateway` interface; `MockPaymentGateway` (default) and `RazorpayGateway`
   (RestClient, basic auth). Order body: `{"amount": paise, "currency":"INR", "receipt":..., "payment":{"capture":"manual"}}`.
   Verify signature = HMAC-SHA256(order_id|payment_id, key_secret). Capture on owner approval:
   POST /v1/payments/{id}/capture. Rejected/expired authorized payments are auto-refunded by Razorpay.
   `GET /api/payments/config` returns mode + key id for the UI.
4. Scheduler (@EnableScheduling): expire AWAITING_PAYMENT after 30 min, REQUESTED after 96 h.
5. In-app notifications table (type + JSON params, translated in UI); `PushSender` interface with a
   logging impl (FCM later); PUT /api/users/me/fcm-token.
6. Reviews: 1–5 stars + comment after COMPLETED, one per booking per reviewer; wire `RatingService`
   to an aggregate query. GET /api/equipment/{id}/reviews, /api/users/{id}/reviews.
7. Users: GET/PUT /api/users/me (incl. preferredLanguage). GET /api/stats/public for the landing page.
8. i18n: en, hi, pa, mr, gu, bn, ta, te, kn in `messages_*.properties`; GET /api/i18n/{lang}
   returns merged key→text JSON for the UI.
9. Web UI (static SPA in `src/main/resources/static`, vanilla JS, no build step): landing, browse with
   Leaflet map + radius, equipment detail + booking, dashboard (my bookings / requests / my equipment /
   notifications), add-equipment with location picker, profile, language switcher with native script
   names, Noto fonts, big touch targets, earthy green/amber palette, mobile-first.
10. Demo data seeder (`app.demo-data`) around Paonta Sahib (30.4384, 77.6245) when DB empty.
11. Integration tests with Testcontainers Postgres (booking overlap/locking, radius search), README update.
