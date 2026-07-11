/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      boxShadow: {
        glow: '0 0 45px rgba(249, 115, 22, 0.18)',
        'glow-red': '0 18px 60px rgba(239, 68, 68, 0.24)',
      },
      animation: {
        'cta-pulse': 'cta-pulse 2.1s ease-in-out infinite',
        'toast-in': 'toast-in 280ms cubic-bezier(.2,.8,.2,1) both',
        'spin-slow': 'spin 1.6s linear infinite',
        'fade-up': 'fade-up 500ms cubic-bezier(.2,.8,.2,1) both',
      },
      keyframes: {
        'cta-pulse': {
          '0%, 100%': { boxShadow: '0 10px 30px rgba(239, 68, 68, .2)' },
          '50%': { boxShadow: '0 12px 42px rgba(249, 115, 22, .42)' },
        },
        'toast-in': {
          from: { opacity: '0', transform: 'translate(-50%, -14px)' },
          to: { opacity: '1', transform: 'translate(-50%, 0)' },
        },
        'fade-up': {
          from: { opacity: '0', transform: 'translateY(10px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
      },
    },
  },
  plugins: [],
}
