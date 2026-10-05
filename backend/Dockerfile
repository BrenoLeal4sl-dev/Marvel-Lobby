FROM node:24-bookworm-slim AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
COPY test ./test
RUN npm run build

FROM node:24-bookworm-slim
ENV NODE_ENV=production HOST=0.0.0.0 PORT=4100
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --from=build /app/dist/src ./dist/src
USER node
EXPOSE 4100
CMD ["node", "dist/src/server.js"]
