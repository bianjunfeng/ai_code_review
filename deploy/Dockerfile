# Build stage
FROM maven:3.9-eclipse-temurin-17 AS backend-build

WORKDIR /app/backend
COPY backend/pom.xml ./
RUN mvn dependency:go-offline -B
COPY backend/src ./src
RUN mvn clean package -DskipTests

# Frontend build stage
FROM node:20-alpine AS frontend-build

WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Production stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install nginx for serving frontend
RUN apk add --no-cache nginx

# Copy backend jar
COPY --from=backend-build /app/backend/target/*.jar app.jar

# Copy frontend build
COPY --from=frontend-build /app/frontend/dist/ /var/www/ai-pr-review/

# Nginx configuration - write to http.d so it's included by default nginx.conf
RUN rm -f /etc/nginx/http.d/default.conf \
    && echo 'server {' > /etc/nginx/http.d/ai-pr-review.conf \
    && echo '    listen 80;' >> /etc/nginx/http.d/ai-pr-review.conf \
    && echo '    root /var/www/ai-pr-review;' >> /etc/nginx/http.d/ai-pr-review.conf \
    && echo '    index index.html;' >> /etc/nginx/http.d/ai-pr-review.conf \
    && echo '    location / {' >> /etc/nginx/http.d/ai-pr-review.conf \
    && echo '        try_files $uri $uri/ /index.html;' >> /etc/nginx/http.d/ai-pr-review.conf \
    && echo '    }' >> /etc/nginx/http.d/ai-pr-review.conf \
    && echo '    location /api {' >> /etc/nginx/http.d/ai-pr-review.conf \
    && echo '        proxy_pass http://127.0.0.1:8080;' >> /etc/nginx/http.d/ai-pr-review.conf \
    && echo '    }' >> /etc/nginx/http.d/ai-pr-review.conf \
    && echo '}' >> /etc/nginx/http.d/ai-pr-review.conf

EXPOSE 80

# Start nginx and java app
CMD nginx && java -jar /app/app.jar --server.port=8080
