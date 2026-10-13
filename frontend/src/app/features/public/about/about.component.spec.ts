import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AboutComponent } from './about.component';
import { TranslationService } from '../../../core/services/translation.service';
import { AboutPageService } from '../../../core/services/about-page.service';

describe('AboutComponent', () => {
  let component: AboutComponent;
  let fixture: ComponentFixture<AboutComponent>;
  let translationService: TranslationService;
  let aboutPageServiceSpy: jasmine.SpyObj<AboutPageService>;

  const mockAboutPt = {
    headline: 'WB Agency - Visão Global',
    quote: 'Autenticidade e relevância no mercado internacional.',
    sectionTitle: 'Nossa Trajetória',
    body: 'Desenvolvimento editorial de talentos para as passarelas mundiais.'
  };

  const mockAboutEn = {
    headline: 'WB Agency - Global Vision',
    quote: 'Authenticity and relevance in the international market.',
    sectionTitle: 'Our Journey',
    body: 'Editorial talent development for international runways.'
  };

  beforeEach(async () => {
    aboutPageServiceSpy = jasmine.createSpyObj('AboutPageService', ['getAboutContent']);
    aboutPageServiceSpy.getAboutContent.and.callFake((lang?: string) => {
      const isEn = lang?.toLowerCase().startsWith('en');
      return of(isEn ? mockAboutEn : mockAboutPt);
    });

    await TestBed.configureTestingModule({
      imports: [AboutComponent],
      providers: [
        provideRouter([]),
        { provide: AboutPageService, useValue: aboutPageServiceSpy }
      ]
    }).compileComponents();

    translationService = TestBed.inject(TranslationService);
    translationService.setLanguage('pt');

    fixture = TestBed.createComponent(AboutComponent);
    component = fixture.componentInstance;
  });

  it('should create and load data dynamically from AboutPageService', () => {
    fixture.detectChanges();

    expect(component).toBeTruthy();
    expect(component.title()).toBe(mockAboutPt.headline);
    expect(component.heroQuote()).toBe(mockAboutPt.quote);
    expect(component.sectionTitle()).toBe(mockAboutPt.sectionTitle);
    expect(component.isLoading()).toBeFalse();
  });

  it('should not contain video elements nor candidate cta cards', () => {
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('video')).toBeNull();
    expect(compiled.querySelector('.cta-card')).toBeNull();
    expect(compiled.textContent).not.toContain('Seu talento merece a visibilidade certa');
  });

  it('should render title, quote, and manifesto from dynamic backend service', () => {
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.page-title')?.textContent).toContain(mockAboutPt.headline);
    expect(compiled.querySelector('.hero-quote-box')?.textContent).toContain(mockAboutPt.quote);
    expect(compiled.querySelector('.manifesto-section h2')?.textContent).toContain(mockAboutPt.sectionTitle);
    expect(compiled.querySelector('.manifesto-section')?.textContent).toContain(mockAboutPt.body);
    expect(compiled.querySelector('.pillars-section')).toBeNull();

    // Não deve conter chapéu obsoleto
    expect(compiled.textContent).not.toContain('MANIFESTO INSTITUCIONAL');
  });

  it('should render standardized breadcrumbs with Home link and Sobre Nós current item', () => {
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const breadcrumb = compiled.querySelector('.header-breadcrumbs');
    expect(breadcrumb).toBeTruthy();

    const homeLink = compiled.querySelector('.breadcrumb-link') as HTMLAnchorElement;
    expect(homeLink).toBeTruthy();
    expect(homeLink.getAttribute('routerLink')).toBe('/');

    const currentItem = compiled.querySelector('.breadcrumb-current');
    expect(currentItem).toBeTruthy();
    expect(currentItem?.textContent).toContain('Sobre Nós');
  });

  it('should dynamically update texts when switching to english', () => {
    translationService.setLanguage('en');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.page-title')?.textContent).toContain(mockAboutEn.headline);
    expect(compiled.querySelector('.hero-quote-box')?.textContent).toContain(mockAboutEn.quote);
    expect(compiled.querySelector('.manifesto-section h2')?.textContent).toContain(mockAboutEn.sectionTitle);
    expect(compiled.querySelector('.breadcrumb-current')?.textContent).toContain('About Us');
  });
});
