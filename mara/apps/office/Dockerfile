FROM node:22-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:22-slim AS runtime
ENV NODE_ENV=production \
    PORT=3200 \
    HOSTNAME=0.0.0.0
WORKDIR /app
COPY --from=build /app/.next/standalone ./
USER 1000:1000
EXPOSE 3200
# IDENTITY_BASE_URL, CORE_BASE_URL and SYNC_BASE_URL are read at request time, so one image serves any deployment.
# OFFICE_SESSION_SECRET (32+ characters) seals the sign-in cookie; the process refuses sign-ins without it.
CMD ["node", "server.js"]
