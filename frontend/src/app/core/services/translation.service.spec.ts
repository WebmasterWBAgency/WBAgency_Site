import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TranslationService } from './translation.service';

describe('TranslationService', () => {
  let service: TranslationService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        TranslationService
      ]
    });
    service = TestBed.inject(TranslationService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  it('should be created and default to pt if no storage set', () => {
    expect(service).toBeTruthy();
    expect(service.currentLang()).toBe('pt');
  });

  it('should translate nested keys and handle missing keys gracefully with pt fallback', () => {
    service.translations.set({
      nav: {
        home: 'Início',
        welcome: 'Bem-vindo, {name}!'
      }
    });

    expect(service.translate('nav.home')).toBe('Início');
    expect(service.translate('nav.welcome', { name: 'João' })).toBe('Bem-vindo, João!');
    expect(service.translate('nav.nonexistent')).toBe('nav.nonexistent');
  });

  it('should fallback to pt if key is missing in en', () => {
    service.currentLang.set('en');
    service.translations.set({
      nav: {
        home: 'Home'
      }
    });

    // 'footer.terms_of_use' exists in pt translations ('Termos de Uso'), but missing in current en dict
    const translated = service.translate('footer.terms_of_use');
    expect(translated).toBe('Termos de Uso');
  });

  it('should select content based on active language', () => {
    service.currentLang.set('pt');
    expect(service.selectContent('Texto em Português', 'English Text')).toBe('Texto em Português');

    service.currentLang.set('en');
    expect(service.selectContent('Texto em Português', 'English Text')).toBe('English Text');
  });

  it('should update signal and localStorage on setLanguage and load local assets', () => {
    service.setLanguage('en');
    expect(service.currentLang()).toBe('en');
    expect(localStorage.getItem('wb_scouting_lang')).toBe('en');

    const req = httpTesting.expectOne(request => request.url.includes('assets/i18n/en.json'));
    expect(req.request.method).toBe('GET');
    req.flush({ nav: { home: 'Home Page' } });

    // Ensure NO HTTP call was made to /api/v1/translations or /translations
    httpTesting.expectNone(request => request.url.includes('/translations'));

    expect(service.translate('nav.home')).toBe('Home Page');
  });

  it('should use in-memory cache and not make redundant HTTP requests', () => {
    service.setLanguage('en');
    const reqEn = httpTesting.expectOne(request => request.url.includes('assets/i18n/en.json'));
    reqEn.flush({ nav: { home: 'Home Page' } });

    // Switch to pt
    service.setLanguage('pt');
    const reqPt = httpTesting.expectOne(request => request.url.includes('assets/i18n/pt.json'));
    reqPt.flush({ nav: { home: 'Página Inicial' } });

    // Switch back to en: must use in-memory cache without any HTTP request
    service.setLanguage('en');
    httpTesting.expectNone(request => request.url.includes('assets/i18n/en.json'));
    expect(service.translate('nav.home')).toBe('Home Page');
  });

  it('should initialize translations via init() observable', (done) => {
    service.init().subscribe((data) => {
      expect(data).toBeDefined();
      expect(service.translations()).toBeDefined();
      done();
    });

    const req = httpTesting.expectOne(request => request.url.includes('assets/i18n/pt.json'));
    expect(req.request.method).toBe('GET');
    req.flush({ common: { ok: 'OK' } });
  });

  it('should update in-memory cache and active translations on saveTranslations', () => {
    service.setLanguage('pt');
    const reqPt = httpTesting.expectOne(request => request.url.includes('assets/i18n/pt.json'));
    reqPt.flush({ nav: { home: 'Início Antigo' } });

    service.saveTranslations('pt', { 'nav.home': 'Início Atualizado' }).subscribe();
    expect(service.translate('nav.home')).toBe('Início Atualizado');
  });
});
