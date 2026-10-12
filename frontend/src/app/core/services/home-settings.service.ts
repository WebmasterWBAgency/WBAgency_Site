import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { shareReplay, tap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface HomeSettings {
  id?: string;
  heroTitle: string;
  heroSubtitle: string;
  heroDescription?: string;
  bannerImageUrl?: string;
  posterUrl?: string;
  videoUrl?: string;
  aboutPreview?: string;
  metaTitle?: string;
  metaDescription?: string;
  scrollLabel?: string;
  footerDescription?: string;
  footerHubs?: string;
  footerPressBookingUrl?: string;
  footerApplyUrl?: string;

  // Novo Disclaimer Opcional
  disclaimerActive?: boolean;
  disclaimerTitle?: string;
  disclaimerText?: string;
  disclaimerLinkUrl?: string;
  disclaimerLinkLabel?: string;
}

@Injectable({
  providedIn: 'root'
})
export class HomeSettingsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  private adminSettingsCache$: Observable<HomeSettings> | null = null;
  private publicSettingsCache$: Observable<HomeSettings> | null = null;

  /**
   * Obtém as configurações públicas da página principal salvas no banco de dados Supabase (cache de 1 vez).
   */
  getPublicSettings(forceRefresh = false): Observable<HomeSettings> {
    if (!this.publicSettingsCache$ || forceRefresh) {
      this.publicSettingsCache$ = this.http.get<HomeSettings>(`${this.baseUrl}/home-settings`).pipe(
        shareReplay(1)
      );
    }
    return this.publicSettingsCache$;
  }

  /**
   * Obtém as configurações da Home para a área administrativa puxadas do Supabase (apenas 1 vez na inicialização).
   */
  getAdminSettings(forceRefresh = false): Observable<HomeSettings> {
    if (!this.adminSettingsCache$ || forceRefresh) {
      this.adminSettingsCache$ = this.http.get<HomeSettings>(`${this.baseUrl}/admin/institutional/home`).pipe(
        shareReplay(1)
      );
    }
    return this.adminSettingsCache$;
  }

  /**
   * Atualiza as configurações da página principal no banco de dados e invalida o cache.
   */
  updateSettings(settings: Partial<HomeSettings>): Observable<HomeSettings> {
    return this.http.put<HomeSettings>(`${this.baseUrl}/admin/institutional/home`, settings).pipe(
      tap(() => {
        this.adminSettingsCache$ = null;
        this.publicSettingsCache$ = null;
      })
    );
  }

  /**
   * Limpa o cache manualmente se necessário
   */
  clearCache(): void {
    this.adminSettingsCache$ = null;
    this.publicSettingsCache$ = null;
  }
}
