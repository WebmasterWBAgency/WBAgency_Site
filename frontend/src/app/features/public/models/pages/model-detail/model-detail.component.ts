import { Component, OnInit, OnDestroy, inject, signal, computed, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterModule, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { PublicModelService, ModelDetailPublicDto } from '../../../../../core/services/public-model.service';
import { ModelMeasurementsComponent } from './components/model-measurements/model-measurements.component';
import { ModelLightboxComponent } from './components/model-lightbox/model-lightbox.component';
import { ModelCompositeModalComponent } from './components/model-composite-modal/model-composite-modal.component';
import { TranslatePipe } from '../../../../../shared/pipes/translate.pipe';
import { InstagramUrlPipe, sanitizeInstagramUrl, extractInstagramHandle } from '../../../../../shared/pipes/instagram-url.pipe';

@Component({
  selector: 'app-model-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    ModelMeasurementsComponent,
    ModelLightboxComponent,
    ModelCompositeModalComponent,
    TranslatePipe,
    InstagramUrlPipe
  ],
  templateUrl: './model-detail.component.html',
  styleUrls: ['./model-detail.component.scss']
})
export class ModelDetailComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  private readonly publicModelService = inject(PublicModelService);
  private readonly destroyRef = inject(DestroyRef);

  // Estados Reativos do Perfil
  readonly isLoading = signal<boolean>(true);
  readonly isNotFound = signal<boolean>(false);
  readonly model = signal<ModelDetailPublicDto | null>(null);

  // Modal de Composite Oficial e Download
  readonly isCompositeModalOpen = signal<boolean>(false);
  readonly isDownloadingComposite = signal<boolean>(false);

  // 🆕 ORDEM CORRETA FALLBACK: composite.fileUrl primeiro (resolvido, projeto stmytwsd CERTO). compositeUrl legado só de backup.
  readonly compositeUrl = computed<string | null>(() => {
    const m = this.model();
    if (!m) return null;
    const viaObjetoResolvido = m.composite?.fileUrl?.trim();
    const viaCampoLegado = m.compositeUrl?.trim();
    return viaObjetoResolvido || viaCampoLegado || null;
  });

  // Higienização segura do Instagram (suporte a @handle e URL completa)
  readonly sanitizedInstagram = computed<{ url: string; handle: string } | null>(() => {
    const m = this.model();
    const raw = m?.instagramHandle || m?.instagramUrl;
    if (!raw || !raw.trim()) return null;

    const url = sanitizeInstagramUrl(raw);
    const handle = extractInstagramHandle(raw);
    if (!url) return null;

    return { url, handle: handle || '@instagram' };
  });

  // Abas de Galeria: Book vs Polaroids
  readonly activeTab = signal<'BOOK' | 'POLAROIDS'>('BOOK');

  // Estado do Modal Lightbox
  readonly lightboxIndex = signal<number>(-1);
  readonly isLightboxOpen = computed(() => this.lightboxIndex() >= 0);

  // Fotografias da Galeria Ativa
  readonly currentGalleryPhotos = computed(() => {
    const m = this.model();
    if (!m) return [];
    if (this.activeTab() === 'BOOK') {
      return m.bookPhotos || [];
    }
    return m.polaroids || [];
  });

  // Lista de URLs de imagem para o Lightbox
  readonly lightboxImages = computed<string[]>(() => {
    return this.currentGalleryPhotos().map(p => p.fileUrl);
  });

  readonly hasError = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  // Imagem de capa principal com fallback robusto
  readonly primaryCoverImage = computed<string>(() => {
    const m = this.model();
    if (!m) return 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=800&auto=format&fit=crop';
    
    if (m.primaryPhotoUrl && m.primaryPhotoUrl.trim()) {
      return m.primaryPhotoUrl.trim();
    }

    // Procura foto com isCover true no book ou primeira foto disponível
    const coverPhoto = m.bookPhotos?.find(p => p.isCover) 
      || m.bookPhotos?.[0] 
      || m.polaroids?.find(p => p.isCover) 
      || m.polaroids?.[0];

    return coverPhoto?.fileUrl || 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=800&auto=format&fit=crop';
  });

  private routeSub?: Subscription;

  ngOnInit(): void {
    try {
      window.scrollTo({ top: 0, behavior: 'instant' });
    } catch {}

    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(params => {
        const modelId = params.get('id');
        if (modelId) {
          this.fetchModelDetail(modelId);
        } else {
          this.errorMessage.set('Identificador de modelo não informado na rota.');
          this.isNotFound.set(true);
          this.hasError.set(true);
          this.isLoading.set(false);
        }
      });
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
  }

  fetchModelDetail(id: string): void {
    const cleanId = (id || '').trim();
    if (!cleanId) {
      this.errorMessage.set('Identificador de modelo não informado na rota.');
      this.isNotFound.set(true);
      this.hasError.set(true);
      this.isLoading.set(false);
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.hasError.set(false);
    this.isNotFound.set(false);

    this.routeSub?.unsubscribe();
    this.routeSub = this.publicModelService.getModelById(cleanId).subscribe({
      next: (res: any) => {
        const data = res?.data ?? res;
        this.model.set(data);
        this.isLoading.set(false);
        this.isNotFound.set(!data);
        this.hasError.set(!data);
      },
      error: (err) => {
        console.error('Erro ao carregar detalhes do modelo:', err);
        this.errorMessage.set('Modelo público não encontrado ou erro na requisição.');
        this.hasError.set(true);
        this.isNotFound.set(true);
        this.model.set(null);
        this.isLoading.set(false);
      }
    });
  }

  loadModel(id: string): void {
    this.fetchModelDetail(id);
  }

  setTab(tab: 'BOOK' | 'POLAROIDS'): void {
    this.activeTab.set(tab);
  }

  openLightbox(index: number): void {
    this.lightboxIndex.set(index);
  }

  closeLightbox(): void {
    this.lightboxIndex.set(-1);
  }

  onLightboxIndexChange(newIndex: number): void {
    this.lightboxIndex.set(newIndex);
  }

  getCompositeUrl(): string | null {
    return this.compositeUrl();
  }

  openCompositeModal(): void {
    if (this.compositeUrl()) {
      this.isCompositeModalOpen.set(true);
    }
  }

  closeCompositeModal(): void {
    this.isCompositeModalOpen.set(false);
  }

  downloadComposite(): void {
    const url = this.compositeUrl();
    const stageName = this.model()?.stageName || 'Model';
    if (!url || this.isDownloadingComposite()) return;

    this.isDownloadingComposite.set(true);

    const safeName = stageName.trim().replace(/[^a-zA-Z0-9_-]/g, '_');
    const extMatch = url.match(/\.(pdf|webp|jpe?g|png)(?:\?|$)/i);
    const ext = extMatch ? extMatch[1].toLowerCase() : (url.toLowerCase().includes('.pdf') ? 'pdf' : 'jpg');
    // 🆕 Nome arquivo alinhado com texto do botao: DOWNLOAD SEDCARD
    const filename = `Sedcard_${safeName}.${ext}`;

    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        // 🆕 Detecta BLOB INVALIDO: 0 bytes OU application/json = Supabase respondeu JSON de erro 404!
        // Nao baixa arquivo quebrado, vai para fallback abrir nova aba Salvar como
        if (!blob || blob.size === 0 || blob.type === 'application/json') {
          console.warn(`[downloadComposite] Blob invalido detectado: size=${blob?.size ?? 'null'}, type=${blob?.type ?? 'null'}. Fallback abrir nova aba Salvar como`);
          this._fallbackAbrirNovaAba(url, filename);
        } else {
          try {
            const objectUrl = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = objectUrl;
            link.download = filename;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(objectUrl);
          } catch (e) {
            console.warn(`[downloadComposite] Falha createObjectURL. Fallback abrir nova aba:`, e);
            this._fallbackAbrirNovaAba(url, filename);
          }
        }
        this.isDownloadingComposite.set(false);
      },
      error: (err) => {
        console.warn(`[downloadComposite] HTTP GET blob falhou (CORS Cloudflare? ${err?.status} ${err?.statusText}). Fallback abrir nova aba`);
        // 🆕 Fallback INFALIVEL: Cloudflare __cf_bm as vezes bloqueia XHR blob GET (Cross-Origin), mas GET direto por link nova aba funciona SEMPRE
        this._fallbackAbrirNovaAba(url, filename);
        this.isDownloadingComposite.set(false);
      }
    });
  }

  /**
   * Fallback final download. Correcao para o warning __cf_bm cookie rejeitado (Cloudflare Bot Management):
   *   - Bloqueia XHR de cross-origin para blob, mas GET DIRETO via <a target="_blank"> funciona (o navegador baixa direto)
   *   - Usuario clica com o botao direito -> Salvar como, ou o proprio Chrome baixa PDF automaticamente
   */
  private _fallbackAbrirNovaAba(url: string, filename: string): void {
    try {
      const link = document.createElement('a');
      link.href = url;
      link.target = '_blank';
      link.rel = 'noopener noreferrer nofollow';
      link.setAttribute('download', filename);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (e) {
      // Ultimo recurso: window.open (qualquer navegador)
      window.open(url, '_blank', 'noopener,noreferrer');
    }
  }

  getBookingRouterLink(): string[] {
    return ['/contact'];
  }

  getBookingQueryParams(): Record<string, string> {
    const m = this.model();
    if (!m) return {};
    return {
      modelId: m.id,
      modelName: m.stageName
    };
  }
}
