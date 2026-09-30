# Putting KisanShare online

The Android app needs the backend on the internet with HTTPS. Two ways:

| | **A. Oracle Cloud Always Free** (recommended) | B. Render |
|---|---|---|
| Cost | ₹0, permanently | about $14/month (app + database) |
| Always on | yes | only on paid plans |
| Effort | ~1 hour, copy-paste commands | ~15 minutes, all clicks |
| Files used | `docker-compose.prod.yml`, `deploy/`, `.env.example` | `render.yaml` |

---

## A. Oracle Cloud Always Free

What you end up with: one small Linux server running the database, the app and Caddy
(which gets the HTTPS certificate automatically), a free `yourname.duckdns.org` address,
and a nightly database backup.

### 1. Create the Oracle Cloud account
1. Sign up at **cloud.oracle.com** → "Start for free".
2. A debit/credit card is needed **only to verify you** — Always Free resources are never charged.
3. **Home region: pick India West (Mumbai) or India South (Hyderabad).** It cannot be changed later.

### 2. Create the server
Menu → Compute → Instances → **Create instance**
- Image: **Ubuntu 22.04** (or 24.04)
- Shape: **Ampere → VM.Standard.A1.Flex**, **2 OCPU, 12 GB memory** (inside the free limit)
  - If it says *out of capacity*, try again later or another "availability domain".
    Fallback: **VM.Standard.E2.1.Micro** (also free, 1 GB memory — set `JAVA_OPTS=-Xmx300m` in step 6).
- SSH keys: **Save private key** (keep this file safe — it is the only way into the server).
- Create, then copy the **Public IP address**.

### 3. Open the web ports (80 and 443)
1. On the instance page → Subnet → Security List → **Add Ingress Rules**:
   Source `0.0.0.0/0`, TCP, destination port `80`; add another for `443`.
2. Connect to the server (Windows Terminal / PowerShell):
   ```
   ssh -i path\to\private.key ubuntu@YOUR_PUBLIC_IP
   ```
3. Oracle's Ubuntu also has its own firewall. Run on the server:
   ```
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```

### 4. Get a free web address
1. Go to **duckdns.org**, sign in with Google/GitHub.
2. Create a subdomain, e.g. `agrishare` → you get `agrishare.duckdns.org`.
3. Put your server's **public IP** in the "current ip" box → **update ip**.

### 5. Install Docker and get the code
On the server:
```
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit
```
Log in again (`ssh ...`), then download the code (the repo is public, so no key is needed):
```
git clone https://github.com/vipul080/AgriShare.git
cd AgriShare
```
If you ever make the repo private: create a key on the server with
`ssh-keygen -t ed25519 -N "" -f ~/.ssh/github`, add `~/.ssh/github.pub` under GitHub → repo →
Settings → **Deploy keys** (read-only), and clone with `git@github.com:vipul080/AgriShare.git`.

### 6. Settings
```
cp .env.example .env
openssl rand -base64 36     # run twice: one value for DB_PASSWORD, one for APP_JWT_SECRET
nano .env                   # set DOMAIN and paste the two values; Ctrl+O, Enter, Ctrl+X to save
```

### 7. Start everything
```
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```
The first build takes 5–10 minutes (it also runs the tests). Then open
**https://agrishare.duckdns.org** — the language screen should appear with a padlock.
Logs if something is wrong: `docker compose -f docker-compose.prod.yml logs -f app`

### 8. Nightly backups
```
chmod +x deploy/backup.sh
crontab -e      # choose nano, add the line below, save
30 2 * * * /home/ubuntu/AgriShare/deploy/backup.sh >> /home/ubuntu/backup.log 2>&1
```
Backups land in `~/AgriShare/backups/` (14 days kept). Now and then copy one to your PC:
`scp -i path\to\private.key ubuntu@YOUR_PUBLIC_IP:AgriShare/backups/*.gz .`

### Updating after new code is pushed
```
cd ~/AgriShare && git pull
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```
Farmers' data stays — it lives in Docker volumes, not in the code folder.

### Keeping it reliable
- **Idle reclaim:** Oracle may reclaim Always Free servers that sit almost unused for a week.
  To be safe, upgrade the account to *Pay As You Go* (Billing → Upgrade). Always Free resources
  stay free; set a **budget alert of ₹1** (Billing → Budgets) so you'd hear about any charge.
- **Restarts:** every container has `restart: unless-stopped`, so after a server reboot
  everything comes back by itself.
- The database port is never exposed to the internet — only 80/443 through Caddy.

---

## B. Render (paid, simplest)

1. Push the code; sign up at **render.com** with GitHub.
2. **New → Blueprint →** pick the repo → **Apply**. It reads `render.yaml`, creates the
   database and the app, and generates the login secret.
3. The free plan sleeps after 15 minutes (first visitor waits ~1 minute) and free databases
   are deleted after 30 days — keep the `starter` / `basic-256mb` plans for real use.

---

## Settings reference

| Variable | Default | Meaning |
|---|---|---|
| `DOMAIN` | — | web address Caddy gets a certificate for (Oracle path) |
| `DB_PASSWORD` | — | database password (random) |
| `APP_JWT_SECRET` | — | signs logins; changing it logs everyone out |
| `PAYMENTS_MODE` | `mock` | `razorpay` for real payments + `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` |
| `APP_DEMO_DATA` | `false` | `true` only on a demo server (6 sample farmers, password `demo1234`) |
| `JAVA_OPTS` | `-Xmx512m` | Java memory; `-Xmx300m` on a 1 GB server |
