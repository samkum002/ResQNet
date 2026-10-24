import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/user': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/public': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/disaster': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/admin': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/station': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/dispatch': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/fetch': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/logout': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/resqnet': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
});
