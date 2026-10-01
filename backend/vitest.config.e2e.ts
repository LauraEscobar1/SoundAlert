import { defineConfig } from 'vitest/config';
import tsconfigPaths from 'vite-tsconfig-paths';
import swc from 'unplugin-swc';

export default defineConfig({
  // SWC emite los metadatos de decoradores que necesita la inyección de Nest.
  plugins: [tsconfigPaths(), swc.vite({ module: { type: 'es6' } })],
  test: {
    globals: true,
    root: './',
    include: ['**/*.e2e-spec.ts'],
    // e2e rápido sin red: base de datos en memoria (contra Supabase: npm run test:supabase).
    env: {
      DB_PROVIDER: 'memory',
      CLASSIFIER_PROVIDER: 'mock',
      ALERT_COOLDOWN_SECONDS: '10',
    },
  },
});
