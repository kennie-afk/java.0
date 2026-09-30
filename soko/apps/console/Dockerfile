FROM node:22-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:22-slim AS runtime
ENV NODE_ENV=production PORT=3500 HOSTNAME=0.0.0.0
WORKDIR /app
COPY --from=build /app/.next/standalone ./
# Next writes its fetch/image cache under .next/cache at run time; the unprivileged user needs to own it.
RUN mkdir -p /app/.next/cache && chown -R node:node /app/.next
USER node
EXPOSE 3500
CMD ["node", "server.js"]
