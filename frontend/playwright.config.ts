import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './playwright',
  timeout: 60000,
  expect: {
    timeout: 10000
  },
  fullyParallel: false,
  workers: 1, // Execução sequencial para evitar concorrência no banco de dados e storage
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }]
  ],
  use: {
    baseURL: process.env['E2E_BASE_URL'] || 'http://localhost:4200',
    locale: 'pt-BR',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 15000,
    navigationTimeout: 30000
  },
  projects: [
    {
      name: 'Google Chrome Desktop',
      use: { ...devices['Desktop Chrome'] },
    }
  ],
  webServer: {
    command: 'npx serve -s dist/frontend/browser -l 4200',
    url: 'http://localhost:4200',
    reuseExistingServer: true,
    timeout: 60000,
  }
});

