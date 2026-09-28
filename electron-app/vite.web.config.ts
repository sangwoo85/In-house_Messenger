import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'node:path'

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': resolve(__dirname, 'src') } },
  define: { 'import.meta.env.VITE_WEB_SAME_ORIGIN': JSON.stringify('true') },
  build: { outDir: 'out/web', emptyOutDir: true }
})
