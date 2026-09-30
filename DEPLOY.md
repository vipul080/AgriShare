# Putting AgriShare online

The Android app (and anyone's phone browser) needs the backend on the internet with HTTPS.
This guide uses **Render** because it builds straight from GitHub and gives HTTPS for free.
Everything it needs is already in the repo: `Dockerfile` and `render.yaml`.

## What it costs (check render.com/pricing — prices change)

| Piece | Plan in `render.yaml` | Why not free |
|---|---|---|
| Web app | `starter` | The free plan sleeps after 15 minutes; the next farmer waits ~1 minute for it to wake up. It also can't keep uploaded photos. |
| Database | `basic-256mb` | Free Render databases are deleted after 30 days. |

For a short demo you can switch both to `free` in the Render dashboard.

## Steps

1. **Push the code to GitHub** (the repo can stay private — Render asks for access).
2. Sign up at **render.com** with your GitHub account.
3. **New → Blueprint →** pick the `AgriShare` repo → **Apply**.
   Render creates the database and the app, generates a random login secret, and builds
   the Docker image (the unit tests run during the build; a failing test stops the deploy).
4. Wait for the first deploy (5–10 minutes). The app URL looks like
   `https://agrishare-xxxx.onrender.com`. Open it — you should see the language screen.
5. On first start Flyway creates all tables. The radius search needs the `cube` and
   `earthdistance` Postgres extensions; Render allows them. If the log shows
   `permission denied to create extension`, open the database's **Shell** in Render and run:
   ```sql
   CREATE EXTENSION IF NOT EXISTS cube;
   CREATE EXTENSION IF NOT EXISTS earthdistance;
   ```
   then **Manual Deploy → Deploy latest commit**.

## Settings (Render → agrishare → Environment)

| Variable | Default | When to change |
|---|---|---|
| `PAYMENTS_MODE` | `mock` | `razorpay` once you have a Razorpay account; also set `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET` |
| `APP_DEMO_DATA` | `false` | `true` only on a separate demo instance (adds 6 sample farmers, password `demo1234`) |
| `APP_JWT_SECRET` | generated | never — changing it logs everyone out |

## Your own domain (optional)

Buy a domain (e.g. `agrishare.in`), then Render → Settings → Custom Domains. HTTPS is automatic.
The Play Store listing, privacy policy link and the Android app all use this address, so it is
worth doing before publishing.

## Before real farmers use it

- Keep the persistent disk (in `render.yaml`) or machine photos disappear on every deploy.
- Turn on Render's daily database backups.
- Switch payments from `mock` to `razorpay` (test keys first, live keys after Razorpay approves you).
