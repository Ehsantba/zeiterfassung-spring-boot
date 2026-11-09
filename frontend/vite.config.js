import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Entwicklung: Vite-Server auf :5173, API-Aufrufe gehen an Spring Boot auf :8080.
// Produktion: Das Build landet in src/main/resources/static und wird von Spring Boot ausgeliefert.
export default defineConfig({
  plugins: [react()],
  server: { proxy: { '/api': 'http://localhost:8080' } },
  build: { outDir: '../src/main/resources/static', emptyOutDir: true }
});
