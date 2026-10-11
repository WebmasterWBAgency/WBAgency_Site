import { test, expect } from '@playwright/test';

test.describe('Validação de Consumo Real da API e Ausência de Mocks', () => {

  test('Deve requisitar e renderizar dados reais de modelos sem erro de conexão', async ({ page }) => {
    // Monitora as chamadas de rede para a API
    const apiResponsePromise = page.waitForResponse(
      response => response.url().includes('/api/v1/public/models') && response.status() === 200,
      { timeout: 15000 }
    );

    await page.goto('/modelos');

    const apiResponse = await apiResponsePromise;
    expect(apiResponse.ok()).toBeTruthy();

    const responseBody = await apiResponse.json();
    expect(Array.isArray(responseBody) || responseBody.content).toBeTruthy();

    // Valida que a página não exibiu alerta de erro de conexão
    await expect(page.locator('text=Erro ao carregar')).not.toBeVisible();
  });

});
