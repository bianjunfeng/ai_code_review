# Build backend
FROM maven:3.9-eclipse-temurin-17 AS backend-build

WORKDIR /app/backend
COPY backend/pom.xml ./
COPY backend/src ./src
RUN mvn -B -DskipTests package

# Build frontend
FROM node:20-alpine AS frontend-build

WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/index.html frontend/vite.config.js ./
COPY frontend/src ./src
RUN npm run build

# Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=prod

RUN apk add --no-cache nginx curl \
    && mkdir -p /run/nginx /var/www/ai-pr-review

COPY --from=backend-build /app/backend/target/*.jar /app/app.jar
COPY --from=frontend-build /app/frontend/dist/ /var/www/ai-pr-review/

RUN rm -f /etc/nginx/http.d/default.conf \
    && printf '%s\n' \
    'server {' \
    '    listen 80;' \
    '    server_name _;' \
    '    root /var/www/ai-pr-review;' \
    '    index index.html;' \
    '    client_max_body_size 10m;' \
    '' \
    '    location /api {' \
    '        proxy_pass http://127.0.0.1:8080;' \
    '        proxy_http_version 1.1;' \
    '        proxy_set_header Host $host;' \
    '        proxy_set_header X-Real-IP $remote_addr;' \
    '        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;' \
    '        proxy_set_header X-Forwarded-Proto $scheme;' \
    '        proxy_connect_timeout 30s;' \
    '        proxy_read_timeout 180s;' \
    '    }' \
    '' \
    '    location / {' \
    '        try_files $uri $uri/ /index.html;' \
    '    }' \
    '}' \
    > /etc/nginx/http.d/ai-pr-review.conf \
    && nginx -t

RUN printf '%s\n' \
    '#!/bin/sh' \
    'set -eu' \
    '' \
    'java ${JAVA_OPTS:-} -jar /app/app.jar --server.port=8080 &' \
    'APP_PID=$!' \
    '' \
    'nginx -g "daemon off;" &' \
    'NGINX_PID=$!' \
    '' \
    'term_handler() {' \
    '    kill -TERM "$APP_PID" "$NGINX_PID" 2>/dev/null || true' \
    '    wait "$APP_PID" "$NGINX_PID" 2>/dev/null || true' \
    '    exit 143' \
    '}' \
    '' \
    'trap term_handler INT TERM' \
    '' \
    'while true; do' \
    '    if ! kill -0 "$APP_PID" 2>/dev/null; then' \
    '        wait "$APP_PID"' \
    '        exit $?' \
    '    fi' \
    '    if ! kill -0 "$NGINX_PID" 2>/dev/null; then' \
    '        wait "$NGINX_PID"' \
    '        exit $?' \
    '    fi' \
    '    sleep 2' \
    'done' \
    > /app/start.sh \
    && chmod +x /app/start.sh

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1/api/health >/dev/null || exit 1

CMD ["/app/start.sh"]
