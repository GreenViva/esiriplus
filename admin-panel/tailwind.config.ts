import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          teal: "#2A9D8F",
          "teal-dark": "#238278",
          "teal-light": "#3AB8A8",
        },
      },
    },
  },
  plugins: [],
};

export default config;
