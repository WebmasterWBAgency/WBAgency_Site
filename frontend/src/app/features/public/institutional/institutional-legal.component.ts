import { Component, OnInit, inject, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { TranslationService } from '../../../core/services/translation.service';
import { TranslatePipe } from '../../../shared/pipes/translate.pipe';

@Component({
  selector: 'app-institutional-legal',
  standalone: true,
  imports: [CommonModule, RouterModule, TranslatePipe],
  templateUrl: './institutional-legal.component.html',
  styleUrls: ['./institutional-legal.component.scss']
})
export class InstitutionalLegalComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly titleService = inject(Title);
  public readonly translationService = inject(TranslationService);

  readonly sectionKey = signal<'TERMS' | 'PRIVACY'>('TERMS');

  readonly pageTitle = computed(() => {
    const isEn = this.translationService.currentLang() === 'en';
    const isTerms = this.sectionKey() === 'TERMS';
    return isTerms 
      ? (isEn ? 'Terms of Use' : 'Termos de Uso')
      : (isEn ? 'Privacy Policy & LGPD' : 'Política de Privacidade & LGPD');
  });

  readonly pageContent = computed(() => {
    // Assina a mudança de idioma para recalcular automaticamente
    this.translationService.currentLang();
    const isTerms = this.sectionKey() === 'TERMS';
    const key = isTerms ? 'footer.terms_content' : 'footer.privacy_content';
    return this.translationService.instant(key);
  });

  readonly pageSubtitle = signal<string>('INSTITUCIONAL • WB AGENCY');
  readonly isLoading = signal<boolean>(false);

  constructor() {
    effect(() => {
      const title = this.pageTitle();
      this.titleService.setTitle(`${title} | WB Agency`);
    });
  }

  ngOnInit(): void {
    const dataKey = this.route.snapshot.data['sectionKey'] as 'TERMS' | 'PRIVACY';
    if (dataKey) {
      this.sectionKey.set(dataKey);
    }
  }
}
