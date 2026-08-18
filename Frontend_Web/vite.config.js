/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    // Component tests need a DOM; jsdom provides one in Node.
    environment: 'jsdom',
    globals: true,
    pool: 'forks',
    poolOptions: {
      forks: {
        singleFork: true
      }
    },
    setupFiles: './src/test/setup.js',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      // Report on source only; exclude entry/config/test files from the denominator.
      include: ['src/**'],
      exclude: [
        'src/main.jsx',
        'src/**/*.test.{js,jsx}',
        'src/test/**',
        'src/pages/MapPage.jsx', // 🟢 排除重型组件防止 OOM
        'src/pages/EditPage.jsx'  // 🟢 排除重型组件防止 OOM
      ],
    },
  },
  server: {
    // Bind both IPv4 (127.0.0.1) and IPv6 (::1) loopback. Without this Vite's
    // default "localhost" host string resolves to a single address and only
    // binds that one — Safari and Chrome don't agree on which they try first
    // for "localhost", so whichever address isn't bound just fails to connect
    // in that browser while working fine in the other.
    host: true,
  },
})
