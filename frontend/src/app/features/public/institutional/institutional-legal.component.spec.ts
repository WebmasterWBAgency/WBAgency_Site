import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { InstitutionalLegalComponent } from './institutional-legal.component';
import { TranslationService } from '../../../core/services/translation.service';
import { environment } from '../../../../environments/environment';

describe('InstitutionalLegalComponent', () => {
  let component: InstitutionalLegalComponent;
  let fixture: ComponentFixture<InstitutionalLegalComponent>;
  let translationService: TranslationService;
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InstitutionalLegalComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              data: { sectionKey: 'TERMS' }
            }
          }
        }
      ]
    }).compileComponents();

    translationService = TestBed.inject(TranslationService);
    httpTesting = TestBed.inject(HttpTestingController);
    translationService.setLanguage('pt');

    fixture = TestBed.createComponent(InstitutionalLegalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    const initReq = httpTesting.expectOne(`${environment.apiUrl}/public/content/TERMS?lang=pt`);
    initReq.flush({ content: 'TERMOS E CONDIÇÕES DE USO DO BANCO' });
    fixture.detectChanges();
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should create and render terms content from API', () => {
    expect(component).toBeTruthy();
    expect(component.pageTitle()).toBe('Termos de Uso');
    expect(component.pageContent()).toBe('TERMOS E CONDIÇÕES DE USO DO BANCO');

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.page-title')?.textContent).toContain('Termos de Uso');
    expect(compiled.querySelector('.content-box')?.textContent).toContain('TERMOS E CONDIÇÕES DE USO DO BANCO');
  });

  it('should update content when language switches to english', () => {
    translationService.setLanguage('en');
    fixture.detectChanges();

    const enReq = httpTesting.expectOne(`${environment.apiUrl}/public/content/TERMS?lang=en`);
    enReq.flush({ content: 'TERMS AND CONDITIONS OF USE FROM SUPABASE' });
    fixture.detectChanges();

    expect(component.pageTitle()).toBe('Terms of Use');
    expect(component.pageContent()).toBe('TERMS AND CONDITIONS OF USE FROM SUPABASE');

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.page-title')?.textContent).toContain('Terms of Use');
    expect(compiled.querySelector('.content-box')?.textContent).toContain('TERMS AND CONDITIONS OF USE FROM SUPABASE');
  });
});
