
import { defineConfig, globalIgnores } from "eslint/config";

import js from "@eslint/js";
import tseslint from "typescript-eslint";
import nextPlugin from "@next/eslint-plugin-next";
import reactHooks from "eslint-plugin-react-hooks";
import globals from "globals";

export default defineConfig([

  // =====================================================
  // ARCHIVOS EXCLUIDOS
  // =====================================================

  globalIgnores([
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",

    // Librerías externas
    "public/static/lib/**",

    // Archivos minificados
    "**/*.min.js",
  ]),

  // =====================================================
  // JAVASCRIPT
  // =====================================================

  {
    files: ["**/*.{js,jsx,mjs,cjs}"],

    extends: [
      js.configs.recommended,
    ],
  },

  // =====================================================
  // TYPESCRIPT
  // =====================================================

  {
    files: ["**/*.{ts,tsx,mts,cts}"],

    extends: [
      tseslint.configs.recommended,
    ],
  },

  // =====================================================
  // JAVASCRIPT DEL NAVEGADOR
  // =====================================================

  {
    files: ["public/**/*.js"],

    rules: {
        "no-unused-vars": [
          "error",
          {
          argsIgnorePattern: "^_",
          caughtErrorsIgnorePattern: "^_",
          }
        ]

    }

    languageOptions: {
      globals: {
        ...globals.browser,

        // Dependencias externas
        Chart: "readonly",
        google: "readonly",
        jQuery: "readonly",
      },
    },
  },

  // =====================================================
  // NEXT.JS Y REACT HOOKS
  // =====================================================

  {
    files: ["src/**/*.{js,jsx,ts,tsx}"],

    extends: [
      reactHooks.configs.flat.recommended,
    ],

    plugins: {
      "@next/next": nextPlugin,
    },

    rules: {
      ...nextPlugin.configs.recommended.rules,
      ...nextPlugin.configs["core-web-vitals"].rules,
    },
  },

]);
