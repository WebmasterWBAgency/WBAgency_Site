import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { InstitutionalLegalComponent } from './institutional-legal.component';
import { TranslationService } from '../../../core/services/translation.service';

describe('InstitutionalLegalComponent', () => {
  let component: InstitutionalLegalComponent;
  let fixture: ComponentFixture<InstitutionalLegalComponent>;
  let translationService: TranslationService;

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
    translationService.setLanguage('pt');

    fixture = TestBed.createComponent(InstitutionalLegalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and render terms content without any HTTP requests', () => {
    expect(component).toBeTruthy();
    expect(component.pageTitle()).toBe('Termos de Uso');
    expect(component.pageContent()).toContain('TERMOS E CONDIÇÕES DE USO');

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.page-title')?.textContent).toContain('Termos de Uso');
    expect(compiled.querySelector('.content-box')?.textContent).toContain('TERMOS E CONDIÇÕES DE USO');
  });

  it('should update content when language switches to english', () => {
    translationService.setLanguage('en');
    fixture.detectChanges();

    expect(component.pageTitle()).toBe('Terms of Use');
    expect(component.pageContent()).toContain('TERMS AND CONDITIONS OF USE');

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.page-title')?.textContent).toContain('Terms of Use');
    expect(compiled.querySelector('.content-box')?.textContent).toContain('TERMS AND CONDITIONS OF USE');
  });
});
