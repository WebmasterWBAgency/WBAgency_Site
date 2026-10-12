import { Injectable, inject, signal } from '@angular/core';
import { PublicContentService, ContactChannelsPublicDto } from './public-content.service';

export function formatPhoneNumber(phone?: string): string {
  if (!phone) return '';
  const digits = phone.replace(/\D/g, '');
  if (digits.length === 13 && digits.startsWith('55')) {
    return `+${digits.slice(0, 2)} (${digits.slice(2, 4)}) ${digits.slice(4, 9)}-${digits.slice(9)}`;
  }
  if (digits.length === 12 && digits.startsWith('55')) {
    return `+${digits.slice(0, 2)} (${digits.slice(2, 4)}) ${digits.slice(4, 8)}-${digits.slice(8)}`;
  }
  if (digits.length === 11) {
    return `+55 (${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}`;
  }
  if (digits.length === 10) {
    return `+55 (${digits.slice(0, 2)}) ${digits.slice(2, 6)}-${digits.slice(6)}`;
  }
  return phone;
}

export function formatInstagramHandle(handle?: string): string {
  if (!handle) return '';
  let clean = handle.trim();
  // Remove query params e fragmentos se existirem
  clean = clean.split('?')[0].split('#')[0];
  clean = clean.replace(/^https?:\/\/(www\.)?instagram\.com\//i, '').replace(/\/$/, '');
  if (!clean.startsWith('@') && clean.length > 0) {
    clean = '@' + clean;
  }
  return clean;
}

export function formatInstagramUrl(handle?: string): string {
  if (!handle) return '';
  let clean = handle.trim();
  clean = clean.split('?')[0].split('#')[0];
  if (clean.startsWith('http://') || clean.startsWith('https://')) {
    return clean.endsWith('/') ? clean : clean + '/';
  }
  const username = clean.replace(/^@/, '').replace(/\/$/, '');
  return `https://www.instagram.com/${username}/`;
}

export interface NormalizedSocialMediaItem {
  id?: string;
  name: string;
  url: string;
  displayValue: string;
  normalizedKey: 'instagram' | 'linkedin' | 'tiktok' | 'facebook' | 'web';
}

@Injectable({
  providedIn: 'root'
})
export class SecureContactService {
  private readonly publicContentService = inject(PublicContentService);

  private readonly _email = signal<string>('');
  private readonly _phone = signal<string>('');
  private readonly _whatsappUrl = signal<string>('');
  private readonly _instagramHandle = signal<string>('');
  private readonly _instagramUrl = signal<string>('');
  private readonly _socialMediaList = signal<NormalizedSocialMediaItem[]>([]);

  constructor() {
    this.loadContactChannels();
  }

  loadContactChannels(lang: string = 'pt'): void {
    this.publicContentService.getContactChannels(lang).subscribe({
      next: (channels: ContactChannelsPublicDto) => {
        if (channels) {
          if (channels.email) {
            this._email.set(channels.email);
          }
          if (channels.whatsappNumber) {
            this._phone.set(formatPhoneNumber(channels.whatsappNumber));
          }
          if (channels.whatsappUrl) {
            this._whatsappUrl.set(channels.whatsappUrl);
          } else if (channels.whatsappNumber) {
            const raw = channels.whatsappNumber.replace(/\D/g, '');
            this._whatsappUrl.set(`https://wa.me/${raw}`);
          }
          if (channels.instagramHandle) {
            this._instagramHandle.set(formatInstagramHandle(channels.instagramHandle));
            this._instagramUrl.set(formatInstagramUrl(channels.instagramHandle));
          }

          // Se a lista de redes vier preenchida, aplica higienização e deduplicação
          if (channels.socialMediaList && channels.socialMediaList.length > 0) {
            // Se instagramHandle não estava configurado no canal dedicado, tenta extrair da lista
            if (!this._instagramHandle()) {
              const igFromList = channels.socialMediaList.find(s => {
                const n = (s.name || '').toLowerCase();
                const u = (s.url || '').toLowerCase();
                return n.includes('instagram') || n === 'ig' || u.includes('instagram.com');
              });
              if (igFromList) {
                const rawHandle = igFromList.url || igFromList.name;
                this._instagramHandle.set(formatInstagramHandle(rawHandle));
                this._instagramUrl.set(formatInstagramUrl(rawHandle));
              }
            }

            const deduplicated = this.deduplicateSocialChannels(channels.socialMediaList);
            this._socialMediaList.set(deduplicated);
          } else {
            this._socialMediaList.set([]);
          }
        }
      },
      error: (err) => {
        console.warn('Erro ao carregar canais institucionais no SecureContactService:', err);
      }
    });
  }

  /**
   * Deduplica e higieniza redes sociais recebidas da API ou painel institucional.
   * Elimina duplicatas de Instagram (já coberto pelo botão oficial de contato direto)
   * e padroniza handles e URLs para exibição editorial.
   */
  deduplicateSocialChannels(channels: any[]): NormalizedSocialMediaItem[] {
    if (!channels || !Array.isArray(channels)) return [];
    const seen = new Set<string>();
    const result: NormalizedSocialMediaItem[] = [];

    // O canal do Instagram oficial da agência já é renderizado com prioridade
    // como botão protegido dedicado. Marcamos 'instagram' para evitar card duplicado genérico.
    const hasOfficialInstagram = !!this._instagramHandle();
    if (hasOfficialInstagram) {
      seen.add('instagram');
    }

    for (const channel of channels) {
      const rawName = (channel.name || channel.label || channel.type || '').toLowerCase();
      const rawUrl = (channel.url || channel.value || '').toLowerCase();

      const normalizedKey: 'instagram' | 'linkedin' | 'tiktok' | 'facebook' | 'web' =
        (rawName.includes('instagram') || rawUrl.includes('instagram.com') || rawName === 'ig') ? 'instagram'
        : (rawName.includes('linkedin')  || rawUrl.includes('linkedin.com'))  ? 'linkedin'
        : (rawName.includes('tiktok')    || rawUrl.includes('tiktok.com'))    ? 'tiktok'
        : (rawName.includes('facebook')  || rawUrl.includes('facebook.com'))  ? 'facebook'
        : 'web';

      const dedupeKey = normalizedKey === 'web'
        ? (channel.url || channel.value || rawName).toLowerCase()
        : normalizedKey;

      if (seen.has(dedupeKey)) {
        continue;
      }
      seen.add(dedupeKey);

      let url = (channel.url || channel.value || '').trim();
      let displayValue = url;

      if (normalizedKey === 'instagram') {
        displayValue = formatInstagramHandle(url || channel.name);
        url = formatInstagramUrl(url || channel.name);
      } else if (normalizedKey === 'tiktok') {
        const handle = this.cleanSocialHandle(url, 'tiktok.com/@', 'tiktok.com/');
        displayValue = handle ? (handle.startsWith('@') ? handle : '@' + handle) : '@wbagency';
      } else if (normalizedKey === 'linkedin') {
        displayValue = this.cleanSocialHandle(url, 'linkedin.com/company/', 'linkedin.com/in/') || 'LinkedIn';
      } else if (normalizedKey === 'facebook') {
        displayValue = this.cleanSocialHandle(url, 'facebook.com/') || 'Facebook';
      }

      result.push({
        id: channel.id,
        name: channel.name || channel.label || this.capitalize(normalizedKey),
        url: url,
        displayValue: displayValue || url,
        normalizedKey: normalizedKey
      });
    }

    return result;
  }

  private cleanSocialHandle(url: string, ...prefixes: string[]): string {
    if (!url) return '';
    let clean = url.trim().split('?')[0].split('#')[0].replace(/\/$/, '');
    for (const prefix of prefixes) {
      const idx = clean.toLowerCase().indexOf(prefix);
      if (idx !== -1) {
        return clean.substring(idx + prefix.length).replace(/\/$/, '');
      }
    }
    return clean.replace(/^https?:\/\/(www\.)?/i, '');
  }

  private capitalize(text: string): string {
    if (!text) return '';
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  get socialMediaList(): NormalizedSocialMediaItem[] {
    return this._socialMediaList();
  }

  get emailDisplay(): string {
    return this._email();
  }

  get phoneDisplay(): string {
    return this._phone();
  }

  get whatsappUrl(): string {
    return this._whatsappUrl();
  }

  get instagramDisplay(): string {
    return this._instagramHandle();
  }

  get instagramUrl(): string {
    return this._instagramUrl();
  }

  getContactChannels() {
    return {
      email: this.emailDisplay,
      whatsappNumber: this.phoneDisplay,
      whatsappUrl: this.whatsappUrl,
      instagramHandle: this.instagramDisplay
    };
  }

  openPhone(): void {
    const raw = this._phone().replace(/[^\d+]/g, '');
    window.location.href = `tel:${raw}`;
  }

  openMail(): void {
    const target = this._email();
    window.location.href = `mailto:${target}?subject=Contato%20Comercial%20-%20WB%20Agency`;
  }

  openWhatsApp(): void {
    window.open(this._whatsappUrl(), '_blank', 'noopener,noreferrer');
  }

  openInstagram(): void {
    window.open(this._instagramUrl(), '_blank', 'noopener,noreferrer');
  }
}
