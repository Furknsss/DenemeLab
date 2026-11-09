# DenemeLab Web (Vercel)

Browser-only responsive desktop/tablet/mobile website. This is **not** a downloadable desktop app.

Production: https://denemelab.vercel.app

## Architecture

React + Vite on Vercel, Firebase Authentication, Firestore read-only cloud backup.

The Android app currently stores account-owned ZIP snapshots in `users/{uid}/backups/{backupId}/parts/{index}`, with the current backup ID in `users/{uid}/metadata/current`. The browser signs in as the **same Firebase uid**, reads that account's current pointer, downloads parts from Firestore, checks the total byte length and SHA-256 digest, then parses `data.json` from the ZIP. It does **not** write to Android's backup documents and cannot delete/overwrite existing data.

The Android app's sync is **snapshot-based**, not a real-time two-way merge. Users must make their first upload from Android settings. Auto-backup is opt-in and may run with a delay.

## Required Firebase web setup (not yet activated)

In Firebase Console for project **denemelab-yks-2026**:

1. Project Settings → Your Apps → Add **Web app**. Copy its public `apiKey`, `authDomain`, `projectId` configuration values.
2. Authentication → Settings → Authorized domains: add **denemelab.vercel.app**.
3. Verify Email/Password and Google sign-in are enabled if required. Google provider may need OAuth consent configuration.
4. In Vercel project **denemelab** → Settings → Environment Variables, configure:
   - `VITE_FIREBASE_API_KEY` = web app apiKey
   - `VITE_FIREBASE_PROJECT_ID` = denemelab-yks-2026
   - `VITE_FIREBASE_AUTH_DOMAIN` = denemelab-yks-2026.firebaseapp.com
5. Redeploy the production site because Vite reads these variables **at build time**.
6. Log in with the existing Android account and check both current backup date and deneme count against the Android backup summary.

No service-account JSON, secret admin key or token belongs in the browser code. Firebase web API keys identify a public client; **Firestore Security Rules** must always protect per-user access and should be deployed from the repo.

## Pages

- Genel Bakış: latest exam net + D/Y/B, recent average, trend and top loss cards.
- Denemelerim: cloud exam list and accessible exam details.
- Analiz Merkezi: uninterrupted wrong streaks; loss/gain lists; verified subject outcomes.
- İlerlemem: user target and comparable exam trends.
- Ayarlar: profile and cloud status.

## Safety guarantees

- Read-only Firestore document access; no users/backups writes.
- Only currently signed-in account's `uid` is queried.
- Every backup chunk is fetched from server, checked for size/order and verified against SHA-256.
- No invented exam data or rankings. Without topic evidence, displays a proper empty state.
- No unverified ÖSYM five-year distributions are shown.
- No personal user data is hard-coded into the site or repository.
- Mobile responsive even though desktop browser is the primary target.

## Development

```sh
cd web
npm install
cp .env.example .env
npm run dev
```

`web/.env` stays untracked. Vercel project uses `web/` as source folder in the repository, or deploy its contents as standalone files.
