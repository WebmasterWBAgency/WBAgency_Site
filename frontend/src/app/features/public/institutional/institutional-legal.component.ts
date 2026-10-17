import { Component, OnInit, inject, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Title } from '@angular/platform-browser';
import { TranslationService } from '../../../core/services/translation.service';
import { TranslatePipe } from '../../../shared/pipes/translate.pipe';
import { environment } from '../../../../environments/environment';

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
  private readonly http = inject(HttpClient);
  public readonly translationService = inject(TranslationService);

  readonly sectionKey = signal<'TERMS' | 'PRIVACY'>('TERMS');

  readonly pageTitle = computed(() => {
    const isEn = this.translationService.currentLang() === 'en';
    const isTerms = this.sectionKey() === 'TERMS';
    return isTerms 
      ? (isEn ? 'Terms of Use' : 'Termos de Uso')
      : (isEn ? 'Privacy Policy & LGPD' : 'Política de Privacidade & LGPD');
  });

  readonly pageContent = signal<string>('');
  readonly pageSubtitle = signal<string>('INSTITUCIONAL • WB AGENCY');
  readonly isLoading = signal<boolean>(false);

  constructor() {
    effect(() => {
      const title = this.pageTitle();
      this.titleService.setTitle(`${title} | WB Agency`);
    });

    effect(() => {
      const key = this.sectionKey();
      const lang = this.translationService.currentLang();
      this.loadContent(key, lang);
    });
  }

  ngOnInit(): void {
    const dataKey = this.route.snapshot.data['sectionKey'] as 'TERMS' | 'PRIVACY';
    if (dataKey) {
      this.sectionKey.set(dataKey);
    }
  }

  loadContent(sectionKey: 'TERMS' | 'PRIVACY', lang: string): void {
    this.isLoading.set(true);
    const resolvedLang = lang?.startsWith('en') ? 'en' : 'pt';
    const isEn = resolvedLang === 'en';

    this.http.get<any>(`${environment.apiUrl}/public/content/${sectionKey}?lang=${resolvedLang}`).subscribe({
      next: (res) => {
        this.isLoading.set(false);
        const extracted = res?.content || res?.payload?.content || '';
        const text = typeof extracted === 'string' ? extracted : (typeof res === 'string' ? res : '');
        if (text && text.trim()) {
          this.pageContent.set(text);
        } else {
          this.pageContent.set(isEn ? 'Content not yet registered.' : 'Conteúdo ainda não cadastrado.');
        }
      },
      error: (err) => {
        this.isLoading.set(false);
        console.warn(`[Legal] Falha ao carregar conteúdo de ${sectionKey}:`, err);
        this.pageContent.set(
          isEn
            ? 'Unable to load content at this moment. The server may be temporarily unavailable.'
            : 'Não foi possível carregar o conteúdo neste momento. O servidor pode estar temporariamente indisponível.'
        );
      }
    });
  }
}
