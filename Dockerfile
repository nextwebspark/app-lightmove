# One image, both halves of the application.
#
# The SPA is built and then copied into the API's classpath, so Spring serves it from `static/`. That is
# not packaging convenience — it is the auth model. The refresh cookie is SameSite=Strict and host-only,
# so a browser only ever sends it back to the host that served the page: the SPA and the API have to be
# one origin, and shipping them as one container is what guarantees they are.
#
# Built and pushed by .github/workflows/deploy.yml. Flyway does NOT run here or at boot — migrations are
# a deploy step (FLYWAY_ENABLED=false on the service).

# The build's one input for identity. APP_VERSION is the release tag, and it is baked in rather than
# set on the service: an image must not be able to disagree with itself about which release it is.
# A global ARG is visible to no stage until that stage re-declares it — hence the repeats below.
ARG APP_VERSION=dev
ARG GIT_SHA=
ARG EXTENSION_ID=

# ── 1. The SPA ────────────────────────────────────────────────────────────────
FROM node:26-slim AS web
WORKDIR /src

# Manifests first, so a source-only change reuses the cached npm install rather than refetching the
# dependency tree on every commit.
COPY package.json package-lock.json ./
COPY apps/web/package.json ./apps/web/
RUN npm ci

COPY apps/web ./apps/web

# Build parameters, read by vite.config.ts through loadEnv and frozen into the bundle. EXTENSION_ID
# decides the chrome-extension:// origin the pairing page looks for; without it the page falls back to
# the development id and pairing reports "extension not detected" forever.
ARG APP_VERSION
ARG EXTENSION_ID
ENV APP_VERSION=$APP_VERSION
ENV EXTENSION_ID=$EXTENSION_ID

# `tsc -b && vite build` — a type error fails the image, not just the editor.
RUN npm run build --workspace=apps/web

# ── 2. The API ────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS api
WORKDIR /src

COPY apps/api/.mvn ./.mvn
COPY apps/api/mvnw apps/api/pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY apps/api/src ./src
# The bundle lands on the classpath, which is where SpaResourceConfig looks for it.
COPY --from=web /src/apps/web/dist ./src/main/resources/static

# Tests are skipped here and that is not laziness: the suite is Testcontainers and needs a Docker daemon,
# which does not exist inside a Docker build. CI runs `./mvnw verify` in its own job and this image is
# only built once that job is green.
RUN ./mvnw -B -q clean package -DskipTests

# ── 3. Runtime ────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Not root. A container that never needs to write anywhere has no reason to be able to.
RUN addgroup -S app && adduser -S app -G app
COPY --from=api /src/target/*.jar app.jar
USER app

# Cloud Run overrides this with its own PORT; the default keeps `docker run` honest.
ENV PORT=8080
EXPOSE 8080

# The same value the bundle was built with, one stage earlier. Spring reads it into /actuator/info.
ARG APP_VERSION
ARG GIT_SHA
ENV APP_VERSION=$APP_VERSION
LABEL org.opencontainers.image.version="$APP_VERSION"
LABEL org.opencontainers.image.revision="$GIT_SHA"
LABEL org.opencontainers.image.source="https://github.com/nextwebspark/app-lightmove"

# MaxRAMPercentage, not -Xmx: the JVM should size itself from the memory Cloud Run actually gave the
# container, which changes with --memory and is not knowable here.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
