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

## Checks

```sh
npm run build
npm run lint
```

Listings and bid history are loaded from the Spring Boot API. Auction creation and bid submission are persisted by the backend to MySQL. Optional JPEG and PNG listing photos (up to 5 MB) are uploaded with the listing and stored by the backend under `${user.home}/.campus-auction/images` by default; set `IMAGE_STORAGE_DIR` to use another persistent directory. The photo key is stored with the listing in MySQL and served from `/api/images/{imageKey}`. Keep the image directory with the database when backing up or moving the application.

The home-page hero uses muted, looping campus footage from [Pexels](https://www.pexels.com/video/entrance-to-high-point-university-13638410/), with a still poster and reduced-motion behavior as fallbacks.
