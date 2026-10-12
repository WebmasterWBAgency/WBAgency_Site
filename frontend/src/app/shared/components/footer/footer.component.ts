import { Component, OnInit, inject, effect, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { TranslationService } from '../../../core/services/translation.service';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { environment } from '../../../../environments/environment';

export interface SocialMediaItem {
  id?: string;
  name: string;
  url: string;
}

export interface ContactData {
  primaryEmail: string | null;
  whatsapp: string | null;
  phone: string | null;
  socialMediaList: SocialMediaItem[];
}

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslatePipe],
  templateUrl: './footer.component.html',
  styleUrls: ['./footer.component.scss']
})
export class FooterComponent implements OnInit {
  private http = inject(HttpClient);
  public translate = inject(TranslationService);

  currentYear: number = new Date().getFullYear();

  // Controle de exibição do Modal Harmônico
  isModalOpen = signal<boolean>(false);
  modalTitle = signal<string>('');
  modalContent = signal<string>('');
  termsContent = signal<string>('');
  privacyContent = signal<string>('');
  isLoadingContent = signal<boolean>(false);

  contactData: ContactData = {
    primaryEmail: null,
    whatsapp: null,
    phone: null,
    socialMediaList: []
  };

  constructor() {
    // Recarrega os canais institucionais se houver troca de idioma em tempo de execução
    effect(() => {
      this.translate.currentLang();
      this.loadRealContactData();
    });
  }

  ngOnInit(): void {
    this.loadRealContactData();
  }

  loadRealContactData(): void {
    this.http.get<any>(`${environment.apiUrl}/public/institutional/contact`).subscribe({
      next: (res) => {
        if (res) {
          const socialList: SocialMediaItem[] = Array.isArray(res.socialMediaList) ? [...res.socialMediaList] : [];
          const rawIg = res.instagramUrl || res.instagram || res.instagramHandle;
          const hasIgInList = socialList.some(s => this.isInstagram(s));
          if (rawIg && !hasIgInList) {
            socialList.unshift({
              name: 'Instagram',
              url: rawIg.startsWith('http') ? rawIg : `https://instagram.com/${rawIg.replace('@', '')}`
            });
          }

          this.contactData = {
            primaryEmail: res.primaryEmail || res.email || null,
            whatsapp: res.whatsapp || null,
            phone: res.phone || null,
            socialMediaList: socialList
          };
        }
      },
      error: (err) => {
        console.warn('[Footer] Erro ao carregar contatos da API:', err);
        // PROIBIDO injetar e-mails, telefones ou redes falsas em caso de erro!
      }
    });
  }

  loadContactInfo(lang: string = 'pt'): void {
    this.loadRealContactData();
  }

  applyContactSettings(res: any): void {
    if (!res) return;
    const socialList: SocialMediaItem[] = Array.isArray(res.socialMediaList) ? [...res.socialMediaList] : [];
    const rawIg = res.instagramUrl || res.instagram || res.instagramHandle;
    const hasIgInList = socialList.some(s => this.isInstagram(s));
    if (rawIg && !hasIgInList) {
      socialList.unshift({
        name: 'Instagram',
        url: rawIg.startsWith('http') ? rawIg : `https://instagram.com/${rawIg.replace('@', '')}`
      });
    }

    this.contactData = {
      primaryEmail: res.primaryEmail || res.email || null,
      whatsapp: res.whatsapp || res.whatsappNumber || null,
      phone: res.phone || null,
      socialMediaList: socialList
    };
  }

  isInstagram(social: SocialMediaItem | null | undefined): boolean {
    if (!social) return false;
    const name = (social.name || '').toLowerCase().trim();
    const url = (social.url || '').toLowerCase().trim();
    return name.includes('instagram') || name === 'ig' || url.includes('instagram.com');
  }

  getCleanWhatsAppUrl(phone: string | null | undefined): string {
    if (!phone) return '';
    const digitsOnly = phone.replace(/\D/g, '');
    return `https://wa.me/${digitsOnly}`;
  }

  cleanWhatsAppNumber(num?: string | null): string {
    return num ? num.replace(/\D/g, '') : '';
  }

  isCurrentLangEn(): boolean {
    const currentLang = typeof this.translate.currentLang === 'function' ? this.translate.currentLang() : 'pt';
    return (currentLang || 'pt').startsWith('en');
  }

  openInstitutionalModal(sectionKey: 'TERMS' | 'PRIVACY'): void {
    const isEn = this.isCurrentLangEn();
    const fallbackKey = sectionKey === 'TERMS' ? 'footer.terms_content' : 'footer.privacy_content';

    // 1. Define o título do Modal
    this.modalTitle.set(
      sectionKey === 'TERMS' 
        ? (isEn ? 'Terms of Use' : 'Termos de Uso')
        : (isEn ? 'Privacy & GDPR' : 'Privacidade & LGPD')
    );

    // 2. Abre o modal com loading
    this.modalContent.set('');
    this.isLoadingContent.set(true);
    this.isModalOpen.set(true);

    const lang = isEn ? 'en' : 'pt';
    const cacheBuster = Date.now();

    // 3. Consulta a API com blindagem anti-cache
    this.http.get<any>(`${environment.apiUrl}/public/content/${sectionKey}?lang=${lang}&_t=${cacheBuster}`, {
      headers: {
        'Cache-Control': 'no-cache, no-store, must-revalidate',
        'Pragma': 'no-cache'
      }
    }).subscribe({
      next: (res) => {
        this.isLoadingContent.set(false);

        // 4. Desempacotamento resiliente de qualquer formato retornado pela API
        let extractedText = '';

        if (typeof res === 'string') {
          extractedText = res;
        } else if (res && typeof res === 'object') {
          const raw = 
            res.body ||
            res.text ||
            res.content || 
            res.data?.body || 
            res.data?.content || 
            res.payload?.body ||
            res.payload?.content ||
            res.payload?.text ||
            res.translations?.[lang]?.body || 
            res.translations?.[lang]?.content || 
            res.payloadPt?.body ||
            res.payloadPt?.content || 
            res.payloadEn?.body ||
            res.payloadEn?.content || 
            '';

          if (typeof raw === 'string') {
            extractedText = raw;
          } else if (raw && typeof raw === 'object') {
            extractedText = raw.body || raw.content || raw.text || raw.description || '';
          }
        }

        // 5. Se o texto extraído for válido, exibe. Se vier vazio, usa o fallback i18n:
        if (extractedText && typeof extractedText === 'string' && extractedText.trim().length > 0) {
          const trimmed = extractedText.trim();
          if (sectionKey === 'TERMS') {
            this.termsContent.set(trimmed);
          } else {
            this.privacyContent.set(trimmed);
          }
          this.modalContent.set(trimmed);
        } else {
          const fallback = this.translate.instant(fallbackKey);
          if (sectionKey === 'TERMS') {
            this.termsContent.set(fallback);
          } else {
            this.privacyContent.set(fallback);
          }
          this.modalContent.set(fallback);
        }
      },
      error: (err) => {
        console.warn(`[FOOTER] Erro ao carregar ${sectionKey} da API. Aplicando fallback local:`, err);
        this.isLoadingContent.set(false);
        const fallback = this.translate.instant(fallbackKey);
        if (sectionKey === 'TERMS') {
          this.termsContent.set(fallback);
        } else {
          this.privacyContent.set(fallback);
        }
        this.modalContent.set(fallback);
      }
    });
  }

  closeModal(): void {
    this.isModalOpen.set(false);
  }
}
