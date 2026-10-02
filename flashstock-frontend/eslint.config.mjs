import { defineConfig, globalIgnores } from "eslint/config";
import js from "@eslint/js";
import tseslint from "typescript-eslint";
import nextPlugin from "@next/eslint-plugin-next";
import reactHooks from "eslint-plugin-react-hooks";

export default defineConfig([
  globalIgnores([
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    "public/static/lib/**",
    "**/*.min.js",
  ]),
    {
    files: ["public/**/*.js"],

    languageOptions: {
      globals: {
        ...globals.browser,

        Chart: "readonly",
        google: "readonly",
        jQuery: "readonly",
      },
    },
  },
  {
    files: ["**/*.{js,jsx,mjs,cjs,ts,tsx,mts,cts}"],
    extends: [js.configs.recommended],
  },

  {
    files: ["**/*.{ts,tsx,mts,cts}"],
    extends: [tseslint.configs.recommended],
  },

  {
    files: ["**/*.{js,jsx,ts,tsx}"],
    extends: [reactHooks.configs.flat.recommended],

    plugins: {
      "@next/next": nextPlugin,
    },

    rules: {
      ...nextPlugin.configs.recommended.rules,
      ...nextPlugin.configs["core-web-vitals"].rules,
    },
  },


]);