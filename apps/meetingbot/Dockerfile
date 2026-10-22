# Uncava Notetaker: one container per call, run as a Cloud Run Job execution the API starts (#951).
# Built from the repository root: docker build -f apps/meetingbot/Dockerfile .
FROM node:22-bookworm-slim AS build
WORKDIR /repo
COPY package.json package-lock.json ./
COPY apps/meetingbot/package.json apps/meetingbot/
RUN npm ci --workspace=apps/meetingbot --include-workspace-root=false --ignore-scripts
COPY apps/meetingbot apps/meetingbot
RUN npm run build --workspace=apps/meetingbot \
 && npm prune --omit=dev --workspace=apps/meetingbot --include-workspace-root=false

FROM node:22-bookworm-slim
# Debian's Chromium, and fonts so the page lays out as it would for a person.
RUN apt-get update \
 && apt-get install -y --no-install-recommends chromium fonts-liberation fonts-noto-color-emoji ca-certificates \
 && rm -rf /var/lib/apt/lists/*
ENV CHROME_PATH=/usr/bin/chromium NODE_ENV=production
WORKDIR /bot
COPY --from=build /repo/node_modules ./node_modules
COPY --from=build /repo/apps/meetingbot/dist ./dist
COPY --from=build /repo/apps/meetingbot/assets ./assets
COPY --from=build /repo/apps/meetingbot/package.json ./
USER node
CMD ["node", "dist/main.js"]
