# syntax=docker/dockerfile:1.7
#   docker build -t nba/console ui/console
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Runs as a non-root user on port 8080, with no fixed UID requirement (works under OpenShift's restricted SCC).
FROM nginxinc/nginx-unprivileged:1.27-alpine
ENV CATALOG_URL=http://action-catalog-service \
    CAMPAIGN_URL=http://campaign-service \
    DECISION_URL=http://decision-service \
    INTERACTION_URL=http://interaction-service \
    NGINX_ENVSUBST_FILTER=_URL$
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
