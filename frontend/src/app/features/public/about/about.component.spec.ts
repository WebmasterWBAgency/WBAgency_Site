import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AboutComponent } from './about.component';
import { TranslationService } from '../../../core/services/translation.service';

describe('AboutComponent', () => {
  let component: AboutComponent;
  let fixture: ComponentFixture<AboutComponent>;
  let translationService: TranslationService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AboutComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();

    translationService = TestBed.inject(TranslationService);
    translationService.setLanguage('pt');

    fixture = TestBed.createComponent(AboutComponent);
    component = fixture.componentInstance;
  });

  it('should create and load data synchronously from TranslationService', () => {
    fixture.detectChanges();

    expect(component).toBeTruthy();
    expect(component.title()).toBe('A Nova Estética do Scouting Global');
    expect(component.heroQuote()).toContain('Acreditamos na autenticidade');
    expect(component.isLoading()).toBeFalse();
  });

  it('should not contain video elements nor candidate cta cards', () => {
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('video')).toBeNull();
    expect(compiled.querySelector('.cta-card')).toBeNull();
    expect(compiled.textContent).not.toContain('Seu talento merece a visibilidade certa');
  });

  it('should render title, quote, and manifesto from TranslationService and exclude obsolete static texts', () => {
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.page-title')?.textContent).toContain('A Nova Estética do Scouting Global');
    expect(compiled.querySelector('.hero-quote-box')?.textContent).toContain('Acreditamos na autenticidade');
    expect(compiled.querySelector('.manifesto-section h2')?.textContent).toContain('Nossa Filosofia');
    expect(compiled.querySelector('.manifesto-section')?.textContent).toContain('A WB Agency consolidou-se');
    expect(compiled.querySelector('.pillars-section')).toBeNull();

    // Critérios de Aceite: Não deve conter chapéu "MANIFESTO INSTITUCIONAL"
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
    expect(compiled.querySelector('.page-title')?.textContent).toContain('The New Aesthetic of Global Scouting');
    expect(compiled.querySelector('.hero-quote-box')?.textContent).toContain('We believe in authenticity');
    expect(compiled.querySelector('.manifesto-section h2')?.textContent).toContain('Our Philosophy');
    expect(compiled.querySelector('.breadcrumb-current')?.textContent).toContain('About Us');
  });
});
