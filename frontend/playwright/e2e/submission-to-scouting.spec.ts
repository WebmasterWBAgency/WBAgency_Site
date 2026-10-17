import { test, expect } from '@playwright/test';
import { generateValidCPF, createValidImageBuffer } from '../helpers/test-data';

test.describe('Ciclo de Vida E2E: Submissão Pública → Scouting Desk → Promoção', () => {

  const BASE_URL = process.env['E2E_BASE_URL'] || 'http://localhost:4200';
  const ADMIN_EMAIL = process.env['E2E_ADMIN_EMAIL'] || 'admin@wbscouting.com';
  const ADMIN_PASSWORD = process.env['E2E_ADMIN_PASSWORD'] || 'Admin@WbScouting2026!';
  
  const testCandidateName = `Modelo Teste E2E ${Date.now()}`;
  const validCpf = generateValidCPF();

  test('1. Fluxo Público: Validação de Menor de Idade, Restrição de CPF e Envio de 8 Polaroids', async ({ page }) => {
    await page.goto(`${BASE_URL}/seja-modelo`);
    await expect(page.locator('h1')).toContainText(/Seja um Modelo/i);

    // Preenche identificação básica
    await page.fill('input[formControlName="fullName"]', testCandidateName);
    await page.fill('input[formControlName="email"]', `candidate_${Date.now()}@teste.com`);
    await page.fill('input[formControlName="phone"]', '+55 (11) 98888-7777');
    await page.fill('input[formControlName="city"]', 'São Paulo');
    await page.fill('input[formControlName="state"]', 'SP');

    // Define data de nascimento para candidato menor de idade (16 anos)
    const birthDate = new Date();
    birthDate.setFullYear(birthDate.getFullYear() - 16);
    const dateFormatted = birthDate.toISOString().split('T')[0];
    await page.fill('input[formControlName="birthDate"]', dateFormatted);

    // Asserção: O container de autorização do responsável legal deve ser renderizado reativamente
    const guardianContainer = page.locator('text=Autorização Legal — Candidato Menor de Idade');
    await expect(guardianContainer).toBeVisible();

    // Validação negativa: Inserção de CPF inválido
    const cpfInput = page.locator('input[formControlName="guardianCpf"]');
    await cpfInput.fill('111.111.111-11');
    await cpfInput.blur();
    await expect(page.locator('text=CPF inválido')).toBeVisible();

    // Validação positiva: Inserção de CPF válido e dados do responsável
    await cpfInput.fill(validCpf);
    await page.fill('input[formControlName="guardianName"]', 'Nome do Responsável Legal');
    await page.fill('input[formControlName="guardianPhone"]', '+55 (11) 97777-6666');
    await page.fill('input[formControlName="guardianEmail"]', 'responsavel@teste.com');

    // Medidas Métricas
    await page.fill('input[formControlName="height"]', '178');
    await page.fill('input[formControlName="bust"]', '86');
    await page.fill('input[formControlName="waist"]', '61');
    await page.fill('input[formControlName="hips"]', '91');
    await page.fill('input[formControlName="shoes"]', '38');
    await page.fill('input[formControlName="eyes"]', 'Castanhos');
    await page.fill('input[formControlName="hair"]', 'Castanho Claro');

    // Upload das 8 Polaroids com binários válidos
    const fileInputs = page.locator('input[type="file"]');
    const count = await fileInputs.count();
    expect(count).toBe(8);

    const jpgBuffer = createValidImageBuffer('jpg');
    for (let i = 0; i < 8; i++) {
      await fileInputs.nth(i).setInputFiles({
        name: `polaroid_${i + 1}.jpg`,
        mimeType: 'image/jpeg',
        buffer: jpgBuffer
      });
    }

    // Asserção do contador da grade
    await expect(page.locator('text=8/8 Carregadas')).toBeVisible();

    // Aceite do termo LGPD
    await page.check('input[formControlName="lgpdConsent"]');

    // Intercepta a requisição de API pública canônica de submissão
    const submissionPromise = page.waitForResponse(response => 
      (response.url().includes('/public/candidates/apply') || response.url().includes('/candidates/apply')) &&
      (response.status() === 200 || response.status() === 201)
    );

    // Submissão do formulário
    await page.click('button[type="submit"]:has-text("Submeter Candidatura")');
    const submitResponse = await submissionPromise;
    expect(submitResponse.status()).toBeLessThanOrEqual(201);

    // Confirmação de interface
    await expect(page.locator('text=Candidatura enviada com sucesso')).toBeVisible({ timeout: 10000 });
  });

  test('2. Fluxo Público: Candidato Maior de Idade com 3 Fotos Obrigatórias (Cenário B)', async ({ page }) => {
    const adultName = `Candidata Adulta E2E ${Date.now()}`;
    await page.goto(`${BASE_URL}/seja-modelo`);
    await expect(page.locator('h1')).toContainText(/Seja um Modelo/i);

    // Preenche identificação básica
    await page.fill('input[formControlName="fullName"]', adultName);
    await page.fill('input[formControlName="email"]', `adult_${Date.now()}@teste.com`);
    await page.fill('input[formControlName="phone"]', '+55 (11) 97777-8888');
    await page.fill('input[formControlName="city"]', 'Rio de Janeiro');
    await page.fill('input[formControlName="state"]', 'RJ');

    // Data de nascimento para maior de idade (22 anos)
    const birthDate = new Date();
    birthDate.setFullYear(birthDate.getFullYear() - 22);
    await page.fill('input[formControlName="birthDate"]', birthDate.toISOString().split('T')[0]);

    // Container de responsável NÃO deve estar visível
    const guardianContainer = page.locator('text=Autorização Legal — Candidato Menor de Idade');
    await expect(guardianContainer).not.toBeVisible();

    // Medidas Métricas
    await page.fill('input[formControlName="height"]', '179');
    await page.fill('input[formControlName="bust"]', '85');
    await page.fill('input[formControlName="waist"]', '60');
    await page.fill('input[formControlName="hips"]', '89');
    await page.fill('input[formControlName="shoes"]', '37');
    await page.fill('input[formControlName="eyes"]', 'Verdes');
    await page.fill('input[formControlName="hair"]', 'Loiro');

    // Envio estrito das 3 fotos obrigatórias
    const fileInputs = page.locator('input[type="file"]');
    const pngBuffer = createValidImageBuffer('png');
    for (let i = 0; i < 3; i++) {
      await fileInputs.nth(i).setInputFiles({
        name: `polaroid_${i + 1}.png`,
        mimeType: 'image/png',
        buffer: pngBuffer
      });
    }

    await expect(page.locator('text=3/8 Carregadas')).toBeVisible();
    await page.check('input[formControlName="lgpdConsent"]');

    const submissionPromise = page.waitForResponse(response => 
      (response.url().includes('/public/candidates/apply') || response.url().includes('/candidates/apply')) &&
      (response.status() === 200 || response.status() === 201)
    );

    await page.click('button[type="submit"]:has-text("Submeter Candidatura")');
    const submitResponse = await submissionPromise;
    expect(submitResponse.status()).toBeLessThanOrEqual(201);

    await expect(page.locator('text=Candidatura enviada com sucesso')).toBeVisible({ timeout: 10000 });
  });

  test('3. Fluxo Administrativo: Autenticação, Scouting Desk, Signed URLs e Promoção', async ({ browser }) => {
    // Cria contexto limpo simulando a sessão do gestor de casting
    const adminContext = await browser.newContext();
    const adminPage = await adminContext.newPage();

    // Tentativa de acesso direto a /admin deve redirecionar para login caso não autenticado
    await adminPage.goto(`${BASE_URL}/admin`);
    await adminPage.waitForURL('**/admin/login');

    // Executa autenticação
    await adminPage.fill('input[type="email"]', ADMIN_EMAIL);
    await adminPage.fill('input[type="password"]', ADMIN_PASSWORD);
    
    const loginResponsePromise = adminPage.waitForResponse(response => 
      (response.url().includes('/api/v1/auth/login') || response.url().includes('/auth/login')) && 
      response.status() === 200
    );
    await adminPage.click('button[type="submit"]');
    await loginResponsePromise;

    // Asserção de Segurança: Checagem de Cookies de sessão
    const cookies = await adminContext.cookies();
    const jwtCookie = cookies.find(c => c.name === 'jwt_token' || c.name === 'token');
    if (jwtCookie) {
      expect(jwtCookie.httpOnly).toBeTruthy();
      expect(jwtCookie.sameSite).toBe('Strict');
    }

    // Validação da rota raiz: /admin deve resolver para /admin/dashboard (Hub de Módulos)
    await adminPage.goto(`${BASE_URL}/admin`);
    await expect(adminPage).toHaveURL(`${BASE_URL}/admin/dashboard`);

    // Acessa o módulo Scouting Desk
    await adminPage.click('a[routerLink="/admin/candidaturas"]');
    await adminPage.waitForURL('**/admin/candidaturas');

    // Localiza a linha ou card do candidato recém-submetido
    const candidateRow = adminPage.locator(`tr:has-text("${testCandidateName}"), div:has-text("${testCandidateName}")`).first();
    await expect(candidateRow).toBeVisible({ timeout: 10000 });

    // Acessa a visualização detalhada
    await candidateRow.click();
    await adminPage.waitForURL(/\/admin\/candidaturas\/[a-f0-9-]+/);

    // Validação das Signed URLs das polaroids privadas
    const polaroidImages = adminPage.locator('img.polaroid-preview');
    await expect(polaroidImages.first()).toBeVisible({ timeout: 10000 });
    
    const imageUrl = await polaroidImages.first().getAttribute('src');
    expect(imageUrl).toBeTruthy();

    // Executa a Promoção do Candidato para o Catálogo de Modelos
    const promoteResponsePromise = adminPage.waitForResponse(response =>
      response.url().includes('/promote') && (response.status() === 200 || response.status() === 201)
    );
    
    // Confirmação modal de promoção
    await adminPage.click('button:has-text("Aprovar e Promover para Casting")');
    await adminPage.click('button:has-text("Confirmar Promoção")');
    await promoteResponsePromise;

    // Validação Final: Verifica a presença da nova modelo ativa no catálogo
    await adminPage.goto(`${BASE_URL}/admin/models`);
    await expect(adminPage.locator(`text=${testCandidateName}`)).toBeVisible({ timeout: 10000 });

    await adminContext.close();
  });

  test('4. Ciclo Canônico Direto: APIRequestContext /apply → Admin Scouting → Decisão de Status', async ({ request }) => {
    const apiCandidateName = `Modelo Canônico ${Date.now()}`;
    const apiCandidateEmail = `canonical_${Date.now()}@teste.com`;
    const jpgBuffer = createValidImageBuffer('jpg');

    // Etapa 1: Inscrição Pública via rota canônica /api/v1/public/candidates/apply
    const candidateData = {
      fullName: apiCandidateName,
      email: apiCandidateEmail,
      phone: '+55 (11) 98888-9999',
      birthDate: '2001-08-20',
      gender: 'FEMALE',
      city: 'Curitiba',
      state: 'PR',
      heightCm: 178,
      bustChestCm: 84.0,
      waistCm: 60.0,
      hipsCm: 90.0,
      shoeSize: '38',
      eyesColor: 'Castanhos',
      hairColor: 'Castanho',
      instagramHandle: '@canonical.model',
      lgpdConsent: true
    };

    const applyResp = await request.post(`http://localhost:8080/api/v1/public/candidates/apply`, {
      multipart: {
        data: {
          name: 'data.json',
          mimeType: 'application/json',
          buffer: Buffer.from(JSON.stringify(candidateData))
        },
        facePhoto: {
          name: 'face.jpg',
          mimeType: 'image/jpeg',
          buffer: jpgBuffer
        },
        profilePhoto: {
          name: 'profile.jpg',
          mimeType: 'image/jpeg',
          buffer: jpgBuffer
        },
        fullBodyPhoto: {
          name: 'body.jpg',
          mimeType: 'image/jpeg',
          buffer: jpgBuffer
        }
      }
    });

    expect([200, 201]).toContain(applyResp.status());
    const applyJson = await applyResp.json();
    const protocol = applyJson.data?.protocol || applyJson.protocol;
    expect(protocol).toBeTruthy();

    // Etapa 2: Triagem Administrativa - Login e listagem
    const loginResp = await request.post(`http://localhost:8080/api/v1/auth/login`, {
      data: {
        email: ADMIN_EMAIL,
        password: ADMIN_PASSWORD
      }
    });
    expect(loginResp.ok()).toBeTruthy();
    const loginJson = await loginResp.json();
    const token = loginJson.accessToken;

    const listResp = await request.get(`http://localhost:8080/api/v1/admin/applications?status=PENDING`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    expect(listResp.ok()).toBeTruthy();
    const listJson = await listResp.json();
    const found = listJson.content?.find((c: any) => (protocol && c.protocol === protocol) || c.email === apiCandidateEmail || c.fullName === apiCandidateName);
    expect(found).toBeTruthy();
    const candidateId = found.id;

    // Etapa 3: Decisão do Scout via status update canônico
    const statusResp = await request.patch(`http://localhost:8080/api/v1/admin/applications/${candidateId}/status`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        status: 'APPROVED',
        notes: 'Aprovada no fluxo canônico E2E'
      }
    });
    expect(statusResp.ok()).toBeTruthy();
    const updatedCandidate = await statusResp.json();
    expect(['APPROVED', 'PROMOTED', 'CAST_PUBLIC']).toContain(updatedCandidate.status);
  });

});
