import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],

  server: {
    host: true,
    port: 5173,
    // Tudo que o cliente chama em /api é encaminhado ao Spring Boot.
    // Usar a mesma origem evita problemas de CORS no navegador.
    // A regra do WebSocket vem primeiro para ter prioridade.
    proxy: {
      "/api/v1/ws": {
        target: "ws://localhost:8080",
        ws: true,
        changeOrigin: true,
      },
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
