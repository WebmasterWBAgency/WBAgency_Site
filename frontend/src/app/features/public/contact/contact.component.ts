import { Component, OnInit, inject, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { SecureContactService, NormalizedSocialMediaItem } from '../../../core/services/secure-contact.service';
import { TranslationService } from '../../../core/services/translation.service';
import { TranslatePipe } from '../../../shared/pipes/translate.pipe';

@Component({
  selector: 'app-contact',
  standalone: true,
  imports: [CommonModule, RouterModule, TranslatePipe],
  templateUrl: './contact.component.html',
  styleUrls: ['./contact.component.scss']
})
export class ContactComponent implements OnInit {
  private readonly router = inject(Router);
  readonly secureContact = inject(SecureContactService);
  private readonly translationService = inject(TranslationService);

  constructor() {
    effect(() => {
      const currentLang = this.translationService.currentLang();
      this.secureContact.loadContactChannels(currentLang);
    });
  }

  ngOnInit(): void {
    this.secureContact.loadContactChannels(this.translationService.currentLang());
  }

  /**
   * Retorna os canais de redes sociais oficiais dinâmicos devidamente deduplicados
   * e normalizados, evitando repetição do Instagram que já possui botão de ação direta.
   */
  get socialMediaList(): NormalizedSocialMediaItem[] {
    return this.deduplicateSocialChannels(this.secureContact.socialMediaList);
  }

  /**
   * Lógica canônica de deduplicação recomendada para canais e redes sociais.
   * Filtra registros equivalentes por rede ('instagram', 'linkedin', 'tiktok', 'facebook').
   */
  deduplicateSocialChannels(channels: any[]): NormalizedSocialMediaItem[] {
    if (!channels || !Array.isArray(channels)) return [];
    const seen = new Set<string>();

    return channels.filter(channel => {
      const rawName = (channel.name || channel.label || channel.type || '').toLowerCase();
      const rawUrl = (channel.url || channel.value || '').toLowerCase();

      const normalizedKey = (rawName.includes('instagram') || rawUrl.includes('instagram.com') || rawName === 'ig') ? 'instagram'
                          : (rawName.includes('linkedin')  || rawUrl.includes('linkedin.com'))  ? 'linkedin'
                          : (rawName.includes('tiktok')    || rawUrl.includes('tiktok.com'))    ? 'tiktok'
                          : (rawName.includes('facebook')  || rawUrl.includes('facebook.com'))  ? 'facebook'
                          : (channel.url || channel.value || rawName).toLowerCase();

      if (seen.has(normalizedKey)) {
        return false;
      }
      seen.add(normalizedKey);
      return true;
    });
  }

  onWhatsAppClick(): void {
    this.secureContact.openWhatsApp();
  }

  onPhoneClick(): void {
    this.secureContact.openPhone();
  }

  onMailClick(): void {
    this.secureContact.openMail();
  }

  onInstagramClick(): void {
    this.secureContact.openInstagram();
  }

  navigateToCasting(): void {
    this.router.navigate(['/seja-modelo']);
  }
}
