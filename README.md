# KisanShare — farmer-to-farmer farm machine sharing

KisanShare lets small and marginal farmers rent tractors, rotavators, seed drills and other
machines from farmers in nearby villages, and earn from their own machines when they would
otherwise stand idle.

Built as the technical project behind a Tata Young Social Innovators submission
(Agriculture sector — equipment sharing).

## What it does

- **Find machines nearby** — radius search (5–50 km) on a map or list, picture tiles per machine type.
- **Book by the day** — Today / Tomorrow / other day plus a day counter; already-booked days are
  blocked, and two farmers can never book the same days (row lock on the machine).
- **Pay cash or online** — online money is only *held* until the owner accepts and released if
  they decline (Razorpay manual capture). Cash bookings never pay any fee.
- **Talk directly** — both phone numbers are revealed once the owner confirms.
- **Trust** — two-way ratings after every completed rental.
- **In the farmer's language** — English, हिन्दी, ਪੰਜਾਬੀ, मराठी, ગુજરાતી, বাংলা, தமிழ், తెలుగు, ಕನ್ನಡ;
  errors, notifications and push messages included.
- **For people who read little** — icon-first screens, big touch targets, colour-coded statuses
  with one plain sentence, and a 🔊 button that reads screens aloud.
- **Play Store ready** — in-app and web account deletion, public privacy policy.

## How it compares

Farm machine rental apps already exist — the government's CHC Farm Machinery app (institutional
Custom Hiring Centres), and private platforms such as KisanKraft and Kisanwala. KisanShare's focus is
narrower: privately owned machines shared between neighbouring small farmers, a cash-first flow
with no fee on cash, and an interface built for low literacy in nine Indian languages.

## Tech stack

- Java 17, Spring Boot 3.3, Spring Security + JWT
- PostgreSQL 16 with `cube`/`earthdistance` for radius search, Flyway migrations
- Razorpay (orders with manual capture) behind a `PaymentGateway` interface with a mock for demos
- Web UI: vanilla JavaScript modules, no build step, Leaflet + OpenStreetMap, Noto fonts
- JUnit 5 + Mockito
- Docker, Caddy (automatic HTTPS) for self-hosting

## Run it locally

Either Docker:

```bash
docker compose up -d          # Postgres 16; its default user is a superuser, so extensions just work
```

or an existing local Postgres (run once as the `postgres` superuser in psql):

```sql
CREATE ROLE agrishare LOGIN PASSWORD 'agrishare';
CREATE DATABASE agrishare OWNER agrishare;
\c agrishare
-- earthdistance can only be created by a superuser; the app user then reuses it
CREATE EXTENSION IF NOT EXISTS cube;
CREATE EXTENSION IF NOT EXISTS earthdistance;
```

Then run the app (Flyway applies all migrations on startup):

```bash
mvn spring-boot:run                          # http://localhost:8080
PORT=8081 mvn spring-boot:run                # if 8080 is taken
APP_DEMO_DATA=true mvn spring-boot:run       # empty DB: adds 6 sample farmers, password demo1234
```

API docs: `/swagger-ui.html`. Payments run in `mock` mode (no real money) unless
`PAYMENTS_MODE=razorpay` with `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` set.

Putting it online: see [DEPLOY.md](DEPLOY.md) (free Oracle Cloud server, or Render).

## Earning from the platform

Both levers are **off by default** and switched on with settings (see `application.yml`):

| Setting | What it does |
|---|---|
| `BOOST_ENABLED=true`, `BOOST_PRICE`, `BOOST_DAYS` | Owners may pay (e.g. ₹49 for 7 days) to show a machine first in nearby searches with a ⭐ Featured badge. Optional; renters are never charged. |
| `ONLINE_FEE_PERCENT`, `ONLINE_FEE_CAP` | Small service fee on **online** payments only (e.g. 2%, max ₹20), shown before paying. Cash bookings stay free; owners receive their full rent. |

## Architecture

```
Phone browser / Android app (Trusted Web Activity)
        │  REST / JSON, JWT
        ▼
Spring Boot  (Controller → Service → Repository)
        │
        ├── PostgreSQL  (users, equipment, bookings, notifications, reviews, boosts)
        ├── Razorpay    (payments)
        └── PushSender  (logs today; Firebase Cloud Messaging with the Android app)
```

## Design notes

- **Pessimistic locking** on the machine row before the overlap check — booking clashes are
  rare but costly, so every booking attempt for one machine is serialised.
- **Optimistic `@Version`** on bookings, because farmers and the expiry job can act on the same
  booking at the same moment; the loser gets a friendly "someone changed this" message.
- **`BigDecimal`** for all money — never `double`/`float`; payments are sent to the gateway in paise.
- **Manual capture**: money is authorised at checkout and captured on owner approval. Razorpay
  auto-refunds uncaptured payments after 5 days, so unanswered requests expire at 96 h.
- **Notifications store type + parameters, not text**, so they are shown in whatever language
  the farmer uses today; push text is rendered in the recipient's saved language after commit.
- **Account deletion anonymises** the user row instead of deleting it, so the other farmer's
  booking and review history still makes sense.
- **Ratings** are aggregated from reviews on read; denormalising is deferred until needed.
- **Translations** live in `messages_*.properties`; tests fail if any language misses a key or
  the UI uses a key that doesn't exist.
