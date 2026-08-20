import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./app/**/*.{ts,tsx}",
    "./components/**/*.{ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        stone: {
          950: "#1a1714",
        },
        moss: {
          50: "#f4f6f2",
          100: "#e4e9de",
          200: "#c9d4bd",
          300: "#a7b995",
          400: "#849c6d",
          500: "#65804e",
          600: "#4f653d",
          700: "#3f5031",
          800: "#34412a",
          900: "#2c3624",
        },
      },
      fontFamily: {
        serif: ["Georgia", "Cambria", "Times New Roman", "serif"],
      },
    },
  },
  plugins: [],
};

export default config;
