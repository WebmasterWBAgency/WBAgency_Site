import { test, expect } from '@playwright/test';

test.describe('Modal Cadastrar Modelo - Rolagem e Fixação do Rodapé', () => {
  const ADMIN_EMAIL = process.env['E2E_ADMIN_EMAIL'] || 'admin@wbscouting.com';
  const ADMIN_PASSWORD = process.env['E2E_ADMIN_PASSWORD'] || 'Admin@WbScouting2026!';

  test('deve manter os botões CANCELAR e CADASTRAR MODELO NO ELENCO visíveis durante a rolagem', async ({ page }) => {
    // 1. Configuração de autenticação prévia via storage e navegação
    await page.addInitScript(() => {
      window.localStorage.setItem('wb_auth_token', 'mock-admin-token-e2e-valid');
      window.localStorage.setItem('wb_auth_user', JSON.stringify({
        id: 'admin-e2e',
        email: 'admin@wbscouting.com',
        name: 'Administrador E2E',
        role: 'ADMIN'
      }));
    });

    await page.goto('/admin/models');

    // Se redirecionar para login caso não autenticado por backend rígido, efetua login
    if (page.url().includes('/admin/login')) {
      await page.fill('input[type="email"]', ADMIN_EMAIL);
      await page.fill('input[type="password"]', ADMIN_PASSWORD);
      await page.click('button[type="submit"]');
      await page.waitForURL('**/admin/models');
    }
    
    // 2. Abertura do modal
    await page.getByRole('button', { name: /cadastrar modelo|novo modelo/i }).click();
    
    const modal = page.locator('.modal-dialog-container').first();
    await expect(modal).toBeVisible();

    const scrollBody = modal.locator('.modal-body-scrollable');
    const footer = modal.locator('.modal-footer-fixed');
    const btnSubmit = footer.getByRole('button', { name: 'CADASTRAR MODELO NO ELENCO' });
    const btnCancel = footer.getByRole('button', { name: 'CANCELAR' });

    // 3. Verifica visibilidade inicial dos botões
    await expect(btnSubmit).toBeVisible();
    await expect(btnCancel).toBeVisible();

    // 4. Executa rolagem máxima no corpo do formulário
    await scrollBody.evaluate((el) => {
      el.scrollTop = el.scrollHeight;
    });

    // 5. Garante que os botões permanecem na área visível da tela/modal após o scroll
    await expect(btnSubmit).toBeInViewport();
    await expect(btnCancel).toBeInViewport();

    // 6. Confirma que o container possui scroll ativo quando o conteúdo ultrapassa a altura
    const hasScroll = await scrollBody.evaluate((el) => el.scrollHeight > el.clientHeight);
    expect(hasScroll).toBe(true);
  });

  test('deve cadastrar modelo com 3 fotos e renderizar a foto de capa imediatamente no grid após fechar o modal', async ({ page }) => {
    const createdModelId = 'e2e-created-model-888';
    const coverUrl = 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=800';
    let modelList: any[] = [];

    // Intercepta rotas da API com regex para cobrir query strings
    await page.route(/\/api\/v1\/admin\/models($|\?)/, async (route) => {
      if (route.request().method() === 'POST') {
        const body = route.request().postDataJSON();
        const newModel = {
          id: createdModelId,
          stageName: body.stageName || 'Camila Playwright',
          gender: body.gender || 'FEMALE',
          isStar: false,
          isFeaturedHome: false,
          isActive: true,
          primaryPhotoUrl: coverUrl,
          status: 'CAST_PUBLIC',
          heightCm: body.heightCm || 178,
          city: body.city || 'São Paulo'
        };
        modelList = [newModel, ...modelList];
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(newModel)
        });
      } else if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            content: modelList,
            totalElements: modelList.length,
            totalPages: 1,
            size: 20,
            number: 0
          })
        });
      } else {
        await route.continue();
      }
    });

    // Intercepta uploads de mídia
    await page.route(new RegExp(`/api/v1/admin/models/${createdModelId}/media`), async (route) => {
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'media-888',
          fileUrl: coverUrl,
          isCover: true,
          mediaType: 'BOOK'
        })
      });
    });

    await page.addInitScript(() => {
      window.localStorage.setItem('wb_auth_token', 'mock-admin-token-e2e-valid');
      window.localStorage.setItem('wb_auth_user', JSON.stringify({
        id: 'admin-e2e',
        email: 'admin@wbscouting.com',
        name: 'Administrador E2E',
        role: 'ADMIN'
      }));
    });

    await page.goto('/admin/models');
    if (page.url().includes('/admin/login')) {
      await page.fill('input[type="email"]', ADMIN_EMAIL);
      await page.fill('input[type="password"]', ADMIN_PASSWORD);
      await page.click('button[type="submit"]');
      await page.waitForURL('**/admin/models');
    }

    // 1. Abre o modal
    await page.getByRole('button', { name: /cadastrar modelo|novo modelo/i }).click();
    const modal = page.locator('.modal-dialog-container').first();
    await expect(modal).toBeVisible();

    // 2. Preenche dados obrigatórios do modelo
    await modal.locator('input[formControlName="stageName"]').fill('Camila Playwright');

    // 3. Seleciona 3 arquivos de imagem para upload
    const fileInput = modal.locator('input[type="file"]').first();
    await fileInput.setInputFiles([
      { name: 'book1.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('fake-image-1') },
      { name: 'book2.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('fake-image-2') },
      { name: 'book3.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('fake-image-3') }
    ]);

    // 4. Aciona o botão de cadastro no rodapé
    const footer = modal.locator('.modal-footer-fixed');
    const submitBtn = footer.getByRole('button', { name: 'CADASTRAR MODELO NO ELENCO' });
    await expect(submitBtn).toBeEnabled();
    await submitBtn.click();

    // 5. Valida que o modal é fechado após conclusão
    await expect(modal).not.toBeVisible({ timeout: 10000 });

    // 6. Valida renderização imediata do modelo e da foto de capa no grid
    const modelCard = page.locator('.grid > div, table tbody tr').filter({ hasText: 'Camila Playwright' }).first();
    await expect(modelCard).toBeVisible();
    const coverImg = modelCard.locator('img').first();
    await expect(coverImg).toBeVisible();
    await expect(coverImg).toHaveAttribute('src', new RegExp(coverUrl.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  });
});
