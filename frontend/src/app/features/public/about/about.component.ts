import { Component, OnInit, inject, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Title, Meta } from '@angular/platform-browser';
import { TranslatePipe } from '../../../shared/pipes/translate.pipe';
import { TranslationService } from '../../../core/services/translation.service';
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
    this.isLoading.set(false);
    this.hasError.set(false);

    const headline = this.translationService.translate('about_page.headline') || 'A Nova Estética do Scouting Global';
    const quote = this.translationService.translate('about_page.quote') || 'Acreditamos na autenticidade, na força da personalidade e na beleza singular de cada indivíduo.';
    const sectionTitle = this.translationService.translate('about_page.section_title') || 'Nossa Filosofia';
    const body = this.translationService.translate('about_page.body') || 'A WB Agency consolidou-se como um núcleo editorial focado no desenvolvimento integral de modelos para os principais mercados da moda internacional. Nossa metodologia rejeita a padronização e prioriza a identidade visual autêntica, conectando talentos a marcas com relevância estética global.\n\nConectamos talentos às principais marcas com curadoria estratégica, visão de vanguarda e compromisso com o desenvolvimento humano e profissional em escala global.';

    this.content = {
      headline,
      quote,
      sectionTitle,
      body
    };

    this.title.set(headline);
    this.subtitle.set('');
    this.description.set(body);
    this.heroQuote.set(quote);
    this.sectionTitle.set(sectionTitle);
    this.manifestoTitle.set(sectionTitle);
    this.manifestoText.set(body);

    const metaTitle = this.translationService.translate('about_page.meta_title') || 'Sobre Nós | WB Agency - Scouting & Model Management';
    const metaDescription = this.translationService.translate('about_page.meta_description') || 'Conheça a WB Agency, agência de modelos e scouting internacional focada na autenticidade, excelência editorial e gestão de carreiras globais.';

    this.titleService.setTitle(metaTitle);
    this.metaService.updateTag({ name: 'description', content: metaDescription });
  }
}
