# CampusAuction

The React and Vite frontend for the CampusAuction marketplace.

## Run locally

1. Start MySQL and create the `campus_auction` database.
2. Configure and run the Spring Boot application from `../backend` (see `src/main/resources/application.properties.example` for environment-based database settings).
3. From this directory, install dependencies and start Vite:

   ```sh
   npm install
   npm run dev
   ```

The frontend reads the API at `http://localhost:8080/api` by default. Set `VITE_API_BASE_URL` in a frontend `.env` file to use another API base URL.

## Accounts and security

Accounts are registered with an email address, display name, and a password of at least 12 characters. Passwords are BCrypt-hashed in MySQL. The API uses server-side HTTP sessions, an HTTP-only session cookie, CSRF tokens, and credentialed CORS restricted to `FRONTEND_ORIGIN`. The frontend never stores account passwords or session identifiers in local storage.

For local HTTP development, set `SESSION_COOKIE_SECURE=false`; use `SESSION_COOKIE_SECURE=true` behind HTTPS in deployment. Configure `FRONTEND_ORIGIN` to the exact deployed frontend origin. Set `VITE_API_BASE_URL` to the corresponding backend API URL. Never commit environment files containing credentials.

## API overview

- `GET /api/health`, `GET /api/auctions`, `GET /api/auctions/{id}`, and `GET /api/auctions/{id}/bids` provide public marketplace data.
- `GET /api/auth/csrf`, `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`, and `POST /api/auth/logout` manage authenticated sessions.
- `GET /api/auctions/mine` returns listings owned by the signed-in account.
- Creating listings and placing bids require an authenticated session and a valid CSRF token. Seller and bidder display names are derived from the account, not accepted from the browser.
- `GET /api/images/{imageKey}` serves stored listing photographs.

## Checks

```sh
npm run build
npm run lint
```

Listings and bid history are loaded from the Spring Boot API. Auction creation and bid submission are persisted by the backend to MySQL. Optional JPEG and PNG listing photos (up to 5 MB) are uploaded with the listing and stored by the backend under `${user.home}/.campus-auction/images` by default; set `IMAGE_STORAGE_DIR` to use another persistent directory. The photo key is stored with the listing in MySQL and served from `/api/images/{imageKey}`. Keep the image directory with the database when backing up or moving the application.

The home-page hero uses muted, looping campus footage from [Pexels](https://www.pexels.com/video/entrance-to-high-point-university-13638410/), with a still poster and reduced-motion behavior as fallbacks.

## Current limitations

Existing listings are retained during the account migration. Because they predate user accounts, their owner association remains empty and they are not returned by the account-scoped seller dashboard. Editing/cancellation, automated auction outcomes, seller decisions and handover, notifications, saved searches, listing questions, and the marketplace assistant are not yet implemented.
