import type { Config } from 'tailwindcss'

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: '#3266DC',
        'primary-dark': '#2854BA',
        sidebar: '#17283F',
        'chat-bg': '#FFFFFF'
      }
    }
  },
  plugins: []
} satisfies Config

