FROM node:20-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-slim AS runtime
ENV NODE_ENV=production PORT=3600 HOSTNAME=0.0.0.0
WORKDIR /app
COPY --from=build /app/.next/standalone ./
# Next writes its cache under .next/cache at run time; the unprivileged user must own it.
RUN mkdir -p /app/.next/cache && chown -R node:node /app/.next
USER 1000:1000
EXPOSE 3600
CMD ["node", "server.js"]
