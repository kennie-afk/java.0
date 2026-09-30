FROM node:22-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:22-slim AS runtime
ENV NODE_ENV=production \
    PORT=3100 \
    HOSTNAME=0.0.0.0
WORKDIR /app
COPY --from=build /app/.next/standalone ./
USER node
EXPOSE 3100
# IDENTITY_BASE_URL is read at request time by the enrolment proxy, so one image serves
# any deployment. The terminal does not need identity-service to start, or to sell.
CMD ["node", "server.js"]
