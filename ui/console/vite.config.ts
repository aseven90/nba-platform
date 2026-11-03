/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// In development each TMF API is proxied to the service started locally (see README).
// In containers nginx does the same routing (nginx/default.conf.template).
const target = (envName: string, fallback: string) => process.env[envName] ?? fallback;

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/tmf-api/productCatalogManagement': target('CATALOG_URL', 'http://localhost:8081'),
      '/tmf-api/promotionManagement': target('CAMPAIGN_URL', 'http://localhost:8082'),
      '/tmf-api/recommendationManagement': target('DECISION_URL', 'http://localhost:8083'),
      '/tmf-api/partyInteractionManagement': target('INTERACTION_URL', 'http://localhost:8084'),
    },
  },
  test: {
    include: ['src/**/*.test.ts'],
  },
});
