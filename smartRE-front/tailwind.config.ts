import type { Config } from 'tailwindcss'
const config: Config = {
  content: ['./app/**/*.{ts,tsx}','./components/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        gold: {
          DEFAULT:'#C9A227','50':'#FCFAF2','100':'#F8F0DC','200':'#F0E3BE',
          '300':'#E6D08F','400':'#D9BC66','500':'#C9A227','600':'#AD8620',
          '700':'#8C6B1A','800':'#6B5114','900':'#4A380D',
        },
      },
      // A tighter type scale than Tailwind's default.
      //
      // The app already sets most text explicitly at 11-13px, but anything using the
      // named scale jumped to 14/16/18/20px and read as shouting next to it. Rather
      // than rewrite several hundred call sites, the scale itself is pulled in so the
      // two systems agree. Every step also carries an explicit line-height, because
      // Tailwind's defaults are generous and loose leading is half of why dense UI
      // reads as untidy.
      fontSize: {
        '2xs':  ['10px',   { lineHeight: '14px' }],
        xs:     ['11px',   { lineHeight: '15px' }],
        sm:     ['12px',   { lineHeight: '17px' }],
        base:   ['13px',   { lineHeight: '19px' }],
        lg:     ['14.5px', { lineHeight: '20px' }],
        xl:     ['16px',   { lineHeight: '22px' }],
        '2xl':  ['19px',   { lineHeight: '25px' }],
        '3xl':  ['23px',   { lineHeight: '29px' }],
        '4xl':  ['28px',   { lineHeight: '34px' }],
      },
      fontFamily: {
        display: ['var(--font-fraunces)','serif'],
        sans:    ['var(--font-manrope)','sans-serif'],
      },
      boxShadow: {
        gold: '0 8px 24px -8px rgba(201,162,39,0.45)',
      },
      animation: {
        'fade-in':'fadeIn .25s ease forwards',
        'slide-up':'slideUp .3s ease forwards',
        'shimmer':'shimmer 1.5s infinite',
      },
      keyframes: {
        fadeIn:  {from:{opacity:'0',transform:'translateY(6px)'},to:{opacity:'1',transform:'translateY(0)'}},
        slideUp: {from:{opacity:'0',transform:'translateY(16px)'},to:{opacity:'1',transform:'translateY(0)'}},
        shimmer: {'0%':{backgroundPosition:'-200% 0'},'100%':{backgroundPosition:'200% 0'}},
      },
    },
  },
  plugins:[],
}
export default config
