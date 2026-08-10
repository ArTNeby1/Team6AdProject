import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Bind both IPv4 (127.0.0.1) and IPv6 (::1) loopback. Without this Vite's
    // default "localhost" host string resolves to a single address and only
    // binds that one — Safari and Chrome don't agree on which they try first
    // for "localhost", so whichever address isn't bound just fails to connect
    // in that browser while working fine in the other.
    host: true,
  },
})
