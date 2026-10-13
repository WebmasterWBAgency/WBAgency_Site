import { Component, OnInit, inject, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Title, Meta } from '@angular/platform-browser';
import { TranslatePipe } from '../../../shared/pipes/translate.pipe';
import { TranslationService } from '../../../core/services/translation.service';
import { AboutPageService } from '../../../core/services/about-page.service';
import { AboutPillar } from '../../../shared/models/about-page.interface';

export interface AboutContent {
  headline: string;
  quote: string;
  sectionTitle: string;
  body: string;
}

@Component({
  selector: 'app-about',
  standalone: true,
  imports: [CommonModule, RouterModule, TranslatePipe],
  templateUrl: './about.component.html',
  styleUrls: ['./about.component.scss']
})
export class AboutComponent implements OnInit {
  private readonly titleService = inject(Title);
  private readonly metaService = inject(Meta);
  private readonly translationService = inject(TranslationService);
  private readonly aboutPageService = inject(AboutPageService);

  // Variável vinculada diretamente aos 4 campos da Seção Editorial
  content: AboutContent | null = null;

  // Alias retrocompatível com aboutData
  get aboutData(): any {
    return this.content;
  }

  // Sinais preservados para reatividade complementar e testes existentes
  readonly title = signal<string>('');
  readonly subtitle = signal<string>('');
  readonly description = signal<string>('');
  readonly heroQuote = signal<string>('');
  readonly sectionTitle = signal<string>('');
  readonly manifestoTitle = signal<string>('');
  readonly manifestoText = signal<string>('');
  readonly pillarsTitle = signal<string>('');
  readonly pillars = signal<AboutPillar[]>([]);

  readonly isLoading = signal<boolean>(false);
  readonly hasError = signal<boolean>(false);

  constructor() {
    effect(() => {
      const currentLang = this.translationService.currentLang();
      this.loadAboutData(currentLang);
    }, { allowSignalWrites: true });
  }

  ngOnInit(): void {
    this.loadAboutData(this.translationService.currentLang());
  }

  loadAboutData(lang: string = 'pt'): void {
    this.isLoading.set(true);
    this.hasError.set(false);

    this.aboutPageService.getAboutContent(lang).subscribe({
      next: (data) => {
        if (data) {
          const headline = data.headline || '';
          const quote = data.quote || '';
          const sectionTitle = data.sectionTitle || '';
          const body = data.body || '';

          this.content = {
            headline,
            quote,
            sectionTitle,
            body
          };

          this.title.set(headline);
          this.heroQuote.set(quote);
          this.sectionTitle.set(sectionTitle);
          this.manifestoTitle.set(sectionTitle);
          this.manifestoText.set(body);
          this.description.set(body);
        } else {
          this.content = {
            headline: '',
            quote: '',
            sectionTitle: '',
            body: ''
          };
          this.title.set('');
          this.heroQuote.set('');
          this.sectionTitle.set('');
          this.manifestoTitle.set('');
          this.manifestoText.set('');
          this.description.set('');
        }
        this.isLoading.set(false);
      },
      error: (err) => {
        console.warn('[AboutComponent] Erro ao carregar conteúdo institucional:', err);
        this.hasError.set(true);
        this.isLoading.set(false);
      }
    });

    const metaTitle = this.translationService.translate('about_page.meta_title') || 'Sobre Nós | WB Agency - Scouting & Model Management';
    const metaDescription = this.translationService.translate('about_page.meta_description') || 'Conheça a WB Agency, agência de modelos e scouting internacional focada na autenticidade, excelência editorial e gestão de carreiras globais.';

    this.titleService.setTitle(metaTitle);
    this.metaService.updateTag({ name: 'description', content: metaDescription });
  }
}
