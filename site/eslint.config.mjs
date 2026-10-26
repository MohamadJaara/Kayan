import js from '@eslint/js';
import markdown from '@eslint/markdown';
import { defineConfig, globalIgnores } from 'eslint/config';
import astro from 'eslint-plugin-astro';
import globals from 'globals';
import tseslint from 'typescript-eslint';
import noEmDashes from './eslint-rules/no-em-dashes.mjs';

export default defineConfig([
  globalIgnores(['node_modules/**', 'dist/**', '.astro/**', 'public/api/**']),
  {
    files: ['**/*.{js,mjs,cjs,jsx,ts,tsx,mts,cts}'],
    extends: [js.configs.recommended],
    languageOptions: { globals: { ...globals.node, ...globals.browser } },
  },
  {
    files: ['**/*.{ts,tsx,mts,cts}'],
    extends: [tseslint.configs.recommended],
  },
  astro.configs.recommended,
  {
    files: ['**/*.astro'],
    languageOptions: { parserOptions: { parser: tseslint.parser } },
  },
  {
    files: ['**/*.{md,mdx}'],
    plugins: { markdown },
    language: 'markdown/gfm',
  },
  {
    files: ['**/*.{js,mjs,cjs,jsx,ts,tsx,mts,cts,astro,md,mdx}'],
    ignores: ['**/*.astro/*'],
    plugins: { kayan: { rules: { 'no-em-dashes': noEmDashes } } },
    rules: { 'kayan/no-em-dashes': 'error' },
  },
]);
