import type { Config } from 'tailwindcss'

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: '#1D6FE8',
        'primary-dark': '#1558C0',
        sidebar: '#1E2A3B',
        'chat-bg': '#F0F4F8'
      }
    }
  },
  plugins: []
} satisfies Config

