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
    const lang = isEn ? 'en' : 'pt';

    // 1. Define o título do Modal
    this.modalTitle.set(
      sectionKey === 'TERMS' 
        ? (isEn ? 'Terms of Use' : 'Termos de Uso')
        : (isEn ? 'Privacy & GDPR' : 'Privacidade & LGPD')
    );

    this.isLoadingContent.set(true);
    this.isModalOpen.set(true);
    this.modalContent.set('');

    // 2. Busca dinâmica diretamente da tabela public.site_contents via API
    this.http.get<any>(`${environment.apiUrl}/public/content/${sectionKey}?lang=${lang}`).subscribe({
      next: (res) => {
        this.isLoadingContent.set(false);
        const extracted = res?.content || res?.payload?.content || '';
        const textContent = typeof extracted === 'string' ? extracted : (typeof res === 'string' ? res : '');

        if (textContent && textContent.trim()) {
          this.modalContent.set(textContent);
          if (sectionKey === 'TERMS') {
            this.termsContent.set(textContent);
          } else {
            this.privacyContent.set(textContent);
          }
        } else {
          this.modalContent.set(isEn ? 'Content not yet registered.' : 'Conteúdo ainda não cadastrado.');
        }
      },
      error: (err) => {
        this.isLoadingContent.set(false);
        console.warn(`[Footer] Falha ao carregar conteúdo de ${sectionKey}:`, err);
        // PROIBIDO mascarar com mock estático! Informa indisponibilidade real.
        this.modalContent.set(
          isEn 
            ? 'Unable to load content at this moment. The server may be temporarily unavailable.' 
            : 'Não foi possível carregar o conteúdo neste momento. O servidor pode estar temporariamente indisponível.'
        );
      }
    });
  }

  closeModal(): void {
    this.isModalOpen.set(false);
  }
}
