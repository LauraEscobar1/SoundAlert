import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';
import tsconfigPaths from 'vite-tsconfig-paths';
import swc from 'unplugin-swc';

/**
 * Tests de integración contra el proyecto REAL de Supabase.
 * Lee SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY de backend/.env.
 *   npm run test:supabase
 */
export default defineConfig({
  plugins: [tsconfigPaths(), swc.vite({ module: { type: 'es6' } })],
  test: {
    globals: true,
    root: './',
    include: ['test/**/*.int-spec.ts'],
    env: {
      ...loadEnv('', process.cwd(), ''),
      DB_PROVIDER: 'supabase',
      CLASSIFIER_PROVIDER: 'mock',
    },
    testTimeout: 30_000,
    hookTimeout: 30_000,
    fileParallelism: false,
  },
});
