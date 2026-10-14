import {
  Component,
  OnInit,
  Input,
  Output,
  EventEmitter,
  inject,
  signal,
  computed
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { HttpClient, HttpEventType } from '@angular/common/http';
import { Observable, forkJoin, firstValueFrom, of } from 'rxjs';
import { switchMap, map, tap, catchError, finalize } from 'rxjs/operators';
import { environment } from '../../../../../environments/environment';
import { AdminModelService } from '../../../../core/services/admin-model.service';
import {
  ModelAdminItem,
  ModelFormData,
  ModelGender
} from '../../../../shared/models/admin-model.interface';
import { PhotoUploaderGridComponent } from '../components/photo-uploader-grid/photo-uploader-grid.component';
import { ModelCompositeManagerComponent } from '../components/model-composite-manager/model-composite-manager.component';
import { GalleryPhoto } from '../../../../shared/models/gallery.model';

export type PhotoFilterType = 'ALL' | 'BOOK' | 'POLAROID';

@Component({
  selector: 'app-model-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, PhotoUploaderGridComponent, ModelCompositeManagerComponent],
  templateUrl: './model-form.component.html',
  styleUrls: ['./model-form.component.scss']
})
export class ModelFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly adminModelService = inject(AdminModelService);
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  // Arrays de mídias por categoria
  bookPhotos: any[] = [];
  polaroidPhotos: any[] = [];
  compositeUrl: string | null = null;

  photoFilter: PhotoFilterType = 'ALL';

  setPhotoFilter(filter: PhotoFilterType): void {
    this.photoFilter = filter;
  }

  get allPhotos(): any[] {
    return this.galleryPhotos();
  }

  get filteredPhotos(): any[] {
    if (this.photoFilter === 'BOOK') {
      return this.allPhotos.filter(p => ((p as any).mediaType || (p as any).type || p.category)?.toUpperCase() === 'BOOK');
    }
    if (this.photoFilter === 'POLAROID') {
      return this.allPhotos.filter(p => ((p as any).mediaType || (p as any).type || p.category)?.toUpperCase() === 'POLAROID');
    }
    return this.allPhotos;
  }

  @Input() set modelIdInput(val: string | null | undefined) {
    if (val) {
      this.modelId.set(val);
      this.loadModel(val);
    } else {
      this.modelId.set(null);
      this.modelForm.reset({
        stageName: '',
        gender: 'FEMALE',
        isStar: false,
        isFeaturedHome: false,
        featuredOrder: null,
        isActive: true,
        primaryPhotoUrl: '',
        nationality: 'Brasileira'
      });
      this.bookPhotos = [];
      this.polaroidPhotos = [];
      this.galleryPhotos.set([]);
      this.selectedFiles = [];
      this.selectedCompositeFile = null;
      this.compositeUrl = null;
    }
  }

  @Output() saved = new EventEmitter<ModelAdminItem>();
  @Output() cancelled = new EventEmitter<void>();

  readonly modelId = signal<string | null>(null);
  readonly isEditMode = computed(() => !!this.modelId());
  readonly isLoading = signal<boolean>(false);
  readonly isSaving = signal<boolean>(false);
  readonly toast = signal<{ message: string; type: 'success' | 'error' } | null>(null);

  selectedFiles: File[] = [];
  selectedCompositeFile: File | null = null;

  // Predefinições de fotos editoriais para facilidade de demonstração
  readonly samplePhotos: string[] = [
    'https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=800&auto=format&fit=crop',
    'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?q=80&w=800&auto=format&fit=crop',
    'https://images.unsplash.com/photo-1517841905240-472988babdf9?q=80&w=800&auto=format&fit=crop',
    'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?q=80&w=800&auto=format&fit=crop',
    'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?q=80&w=800&auto=format&fit=crop',
    'https://images.unsplash.com/photo-1494790108377-be9c29b29330?q=80&w=800&auto=format&fit=crop'
  ];

  readonly eyeColors = [
    { value: 'CASTANHO', label: 'Castanho' },
    { value: 'VERDE', label: 'Verde' },
    { value: 'AZUL', label: 'Azul' },
    { value: 'PRETO', label: 'Preto' },
    { value: 'MEL', label: 'Mel' }
  ];

  readonly hairColors = [
    { value: 'CASTANHO', label: 'Castanho' },
    { value: 'LOIRO', label: 'Loiro' },
    { value: 'PRETO', label: 'Preto' },
    { value: 'RUIVO', label: 'Ruivo' },
    { value: 'GRISALHO', label: 'Grisalho' }
  ];

  normalizeColor(val?: string | null): string {
    if (!val) return '';
    const upper = val.trim().toUpperCase();
    return upper;
  }

  readonly modelForm: FormGroup = this.fb.group({
    stageName: ['', [Validators.required, Validators.maxLength(150)]],
    gender: ['FEMALE' as ModelGender, [Validators.required]],
    isStar: [false],
    isFeaturedHome: [false],
    featuredOrder: [null, [Validators.min(1), Validators.max(99)]],
    isActive: [true],
    primaryPhotoUrl: [''],
    instagramUrl: ['', [Validators.maxLength(255)]],
    birthDate: [''],
    heightCm: [null, [Validators.min(50), Validators.max(250)]],
    city: ['', [Validators.maxLength(100)]],
    nationality: ['Brasileira', [Validators.maxLength(100)]],
    dressSize: ['', [Validators.maxLength(20)]],
    shoeSize: ['', [Validators.maxLength(20)]],
    bustChestCm: [null, [Validators.min(20), Validators.max(200)]],
    waistCm: [null, [Validators.min(20), Validators.max(200)]],
    hipsCm: [null, [Validators.min(20), Validators.max(200)]],
    hairColor: ['', [Validators.maxLength(50)]],
    eyesColor: ['', [Validators.maxLength(50)]]
  });

  // Idade calculada dinamicamente
  readonly computedAge = signal<number | null>(null);

  // Sinal reativo aos valores do formulário
  readonly formValueSignal = signal(this.modelForm.value);

  // Resumo das medidas em tempo real para o card preview
  readonly computedMeasurements = computed(() => {
    const val = this.formValueSignal();
    const h = val?.heightCm;
    const b = val?.bustChestCm;
    const w = val?.waistCm;
    const hip = val?.hipsCm;
    const shoe = val?.shoeSize;
    const dress = val?.dressSize;

    const parts: string[] = [];
    if (h) parts.push(`${h} cm`);
    if (b || w || hip) parts.push(`${b || '--'} / ${w || '--'} / ${hip || '--'}`);
    if (dress) parts.push(`Man. ${dress}`);
    if (shoe) parts.push(`Sap. ${shoe}`);

    return parts.length > 0 ? parts.join(' • ') : 'Medidas não informadas';
  });

  // ============================================================
  // 🟢 CORREÇÃO: FOTO DE CAPA APARECER NO CARD PREVIEW EDITORIAL (TOPO)
  // Se primaryPhotoUrl estiver incompleta / vazia / HTTP 400, cai
  // para a PRIMEIRA FOTO MARCADA COMO CAPA NA GALERIA REAL (ou a primeira
  // da lista, se nenhuma marcada). Nunca mais placeholder WB sozinho.
  // ============================================================
  private readonly _forcarRerenderCapa = signal<number>(0);

  readonly fotoCapaPreviewSafe = computed<string | null>(() => {
    this._forcarRerenderCapa(); // dependencia de trigger
    return this._resolveCapaParaPreview(
      this.modelForm.get('primaryPhotoUrl')?.value,
      this.galleryPhotos() || []
    );
  });

  onErroCarregarCapaPreview(): void {
    // Se navegador retornou erro ao carregar (HTTP 400 bucket, URL incompleta)
    // força nova computação e escreve fallback explícito para 1a foto da galeria
    console.warn('[Capa Preview] Falha ao carregar primaryPhotoUrl. Usando fallback da galeria REAL.');
    const galeria = this.galleryPhotos() || [];
    if (galeria.length > 0) {
      const capaGaleria = galeria.find(p => Boolean(p.isCover)) || galeria[0];
      if (capaGaleria?.url) {
        // Patch direto e força rerender
        this.modelForm.patchValue({ primaryPhotoUrl: capaGaleria.url }, { emitEvent: true });
        this._forcarRerenderCapa.update(n => n + 1);
      }
    }
  }

  /**
   * Helper monta URL FINAL segura para o avatar-preview do topo do admin.
   * 1) Se primaryPhotoUrl parecer completa, usa.
   * 2) Se incompleta ou vazia, usa 1a foto marcada isCover=true na galeria REAL.
   * 3) Senao, usa a 1a foto da galeria.
   * 4) Usa filePath para remontar se necessário (igual helpers de capa em outros services).
   */
  private _resolveCapaParaPreview(primaryRaw: any, galeria: GalleryPhoto[]): string | null {
    const SUPABASE_BASE = 'https://stmytwsdlonpnirqiufq.supabase.co/storage/v1/object/public/models-media';
    const bust = '?v=' + Math.floor(Date.now() / 3_600_000);

    // Helper interno valida se uma URL parece OK
    const pareceOK = (u: string) => {
      if (!u) return false;
      const s = String(u).trim();
      if (s.startsWith('blob:') || s.startsWith('data:')) return true;
      if (!s.startsWith('http')) return false;
      if (s.length < 40) return false;
      if (s.endsWith('/models-media/') || s.endsWith('/models-media')) return false;
      if (s.endsWith('/')) return false;
      return true;
    };

    // Helper normaliza URLs com projeto Supabase correto
    const normalizarHost = (u: string): string => {
      if (!u) return '';
      let s = String(u).trim();
      if (s.startsWith('blob:') || s.startsWith('data:')) return s;
      if (s.includes('zmpqmdizqgnpnirqiufq.supabase.co')) {
        s = s.replace('zmpqmdizqgnpnirqiufq.supabase.co', 'stmytwsdlonpnirqiufq.supabase.co');
      }
      return s;
    };

    // Helper interno: aplica fallback filePath se URL vier incompleta
    const remontarSePrecisar = (urlIn: string, fp?: string): string => {
      if (!urlIn && !fp) return '';
      let u = normalizarHost(urlIn || '');
      if (u.startsWith('blob:') || u.startsWith('data:')) return u;
      let caminho = (fp || '').trim().replace(/^\//, '');

      // Caso 1: parece completa
      if (pareceOK(u)) {
        return u + (u.includes('?v=') ? '' : bust);
      }
      // Caso 2: URL vazia, mas temos filePath
      if (caminho) {
        return `${SUPABASE_BASE}/${caminho}${bust}`;
      }
      // Caso 3: URL veio tipo /public/models-media/ sem path
      if (u.includes('/models-media') && caminho) {
        return `${SUPABASE_BASE}/${caminho}${bust}`;
      }
      return u ? (u + (u.includes('?v=') ? '' : bust)) : '';
    };

    // 1) Tenta primaryPhotoUrl primeiro
    const primNorm = normalizarHost(String(primaryRaw || '').trim());
    if (pareceOK(primNorm)) {
      if (primNorm.startsWith('blob:') || primNorm.startsWith('data:')) {
        return primNorm;
      }
      return primNorm + (primNorm.includes('?v=') ? '' : bust);
    }
    // 2) Se chegou aqui, a primary nao serve. Usa galeria REAL.
    if (galeria && galeria.length > 0) {
      const capa = galeria.find(p => Boolean(p.isCover)) || galeria[0];
      if (capa) {
        const capaUrlNorm = normalizarHost(String(capa.url || ''));
        if (pareceOK(capaUrlNorm)) {
          if (capaUrlNorm.startsWith('blob:') || capaUrlNorm.startsWith('data:')) {
            return capaUrlNorm;
          }
          return capaUrlNorm + (capaUrlNorm.includes('?v=') ? '' : bust);
        }
        // Tenta remontar usando filePath da propria foto da galeria
        const remontada = remontarSePrecisar(capaUrlNorm, String(capa.filePath || (capa as any).storagePath || ''));
        if (remontada && pareceOK(remontada.split('?')[0])) return remontada;
        if (capa.url) return normalizarHost(String(capa.url));
      }
    }
    // 3) Ultima tentativa: remontar primary com filePath do modelo se existir em algum lugar
    if (primNorm) return remontarSePrecisar(primNorm) || null;
    return null;
  }

  private _atualizaFotoCapaPreviewDisparo(): void {
    this._forcarRerenderCapa.update(n => n + 1);
  }

  ngOnInit(): void {
    // Sincroniza sinal reativo e cálculo de idade a cada mudança no formulário
    this.modelForm.valueChanges.subscribe((val) => {
      this.formValueSignal.set(val);
      this.updateComputedAge(val.birthDate);
    });

    // Se estiver em rota direta (ex: /admin/models/:id/edit ou /admin/models/:id/editar)
    if (this.route.paramMap) {
      this.route.paramMap.subscribe(params => {
        const id = params?.get('id');
        if (id && id !== 'new') {
          this.modelId.set(id);
          this.loadModel(id);
        }
      });
    }

    // Fallback de snapshot se paramMap ainda não disparou
    const routeId = this.route.snapshot?.paramMap?.get('id');
    if (routeId && routeId !== 'new' && !this.modelId()) {
      this.modelId.set(routeId);
      this.loadModel(routeId);
    }
  }

  /**
   * Método padrão para carregar modelo no modo de edição (/admin/models/:id/editar).
   */
  loadModel(id: string): void {
    this.loadModelData(id);
  }

  /**
   * Popula os arrays de fotos locais (bookPhotos, polaroidPhotos) e sincroniza a galeria unificada.
   */
  populatePhotos(mediaList: any[]): void {
    const list = mediaList || [];
    this.bookPhotos = list.filter((m: any) => (m.mediaType || m.type)?.toUpperCase() === 'BOOK');
    this.polaroidPhotos = list.filter((m: any) => (m.mediaType || m.type)?.toUpperCase() === 'POLAROID');
    this.compositeUrl = list.find((m: any) => (m.mediaType || m.type)?.toUpperCase() === 'COMPOSITE')?.fileUrl || null;

    // Alimenta também a lista unificada para o photo-uploader-grid
    const galleryItems = this._mapMediaDtoToGalleryPhotos(list);
    if (galleryItems.length > 0) {
      if (!galleryItems.some(p => Boolean(p.isCover))) {
        galleryItems[0].isCover = true;
      }
      this.galleryPhotos.set(galleryItems);

      const reais = new Set<string>();
      for (const photo of galleryItems) {
        if (photo?.id && !String(photo.id).startsWith('temp-') && !String(photo.id).startsWith('media-') && photo.id !== 'fallback-cover') {
          reais.add(String(photo.id));
        }
      }
      this.originalMediaIds.set(reais);
      this.pendingDeleteIds.set(new Set());

      const realCover = galleryItems.find(p => Boolean(p.isCover)) || galleryItems[0];
      if (realCover?.url) {
        this.modelForm.patchValue({ primaryPhotoUrl: realCover.url });
      }
      this._atualizaFotoCapaPreviewDisparo();
    } else {
      const capa = this.modelForm.get('primaryPhotoUrl')?.value;
      if (capa) {
        const resolvedCapa = this._resolveCapaParaPreview(capa, []) || capa;
        this.galleryPhotos.set([{
          id: 'fallback-cover',
          url: resolvedCapa,
          fileUrl: resolvedCapa,
          mediaUrl: resolvedCapa,
          previewUrl: resolvedCapa,
          title: 'Foto de capa',
          category: 'BOOK',
          orderIndex: 0,
          isCover: true
        }]);
      } else {
        this.galleryPhotos.set([]);
      }
      this.originalMediaIds.set(new Set());
      this.pendingDeleteIds.set(new Set());
      this._atualizaFotoCapaPreviewDisparo();
    }
  }

  /**
   * Busca as mídias vinculadas ao modelo diretamente na rota administrativa
   * e distribui entre as categorias Book, Polaroids e Composite.
   */
  loadModelMedia(modelId: string): void {
    this.adminModelService.getModelMedia(modelId).subscribe({
      next: (mediaList) => {
        const list = mediaList || [];
        this.bookPhotos = list.filter((m: any) => (m.mediaType || m.type)?.toUpperCase() === 'BOOK');
        this.polaroidPhotos = list.filter((m: any) => (m.mediaType || m.type)?.toUpperCase() === 'POLAROID');
        this.populatePhotos(list);
      },
      error: (err) => {
        console.error('Erro ao carregar mídias do modelo:', err);
      }
    });
  }

  setCoverPhoto(photoId: string): void {
    const currentId = this.modelId();
    if (!currentId || !photoId) return;
    this.adminModelService.setModelCover(currentId, photoId).subscribe({
      next: () => {
        this.showToast('Capa principal atualizada com sucesso.', 'success');
        this.loadModelMedia(currentId);
      },
      error: () => this.showToast('Falha ao definir nova foto de capa.', 'error')
    });
  }

  deletePhoto(photoId: string): void {
    const currentId = this.modelId();
    if (!currentId || !photoId) return;
    if (confirm('Deseja excluir esta fotografia?')) {
      this.adminModelService.deleteModelMedia(currentId, photoId).subscribe({
        next: () => {
          this.showToast('Fotografia removida com sucesso.', 'success');
          this.loadModelMedia(currentId);
        },
        error: () => this.showToast('Falha ao remover fotografia.', 'error')
      });
    }
  }

  toggleMediaType(item: any, newType: 'BOOK' | 'POLAROID'): void {
    const currentId = this.modelId();
    if (!item) return;

    if (!currentId || !item.id || String(item.id).startsWith('temp-') || item.id === 'fallback-cover') {
      // Alternância em memória (modo novo modelo ou foto recém-adicionada)
      item.mediaType = newType;
      item.category = newType;
      item.type = newType;
      if (newType === 'POLAROID') {
        item.isCover = false;
        this.bookPhotos = this.bookPhotos.filter(p => p.id !== item.id);
        this.polaroidPhotos = [...this.polaroidPhotos, item];
      } else {
        this.polaroidPhotos = this.polaroidPhotos.filter(p => p.id !== item.id);
        this.bookPhotos = [...this.bookPhotos, item];
      }
      this.galleryPhotos.set(
        this.galleryPhotos().map(p => p.id === item.id ? { ...p, category: newType, mediaType: newType, type: newType, isCover: newType === 'POLAROID' ? false : p.isCover } : p)
      );
      this.showToast(`Fotografia movida para ${newType === 'POLAROID' ? 'Polaroids' : 'Book'}.`, 'success');
      return;
    }

    this.adminModelService.updateMediaType(currentId, String(item.id), newType).subscribe({
      next: (updated) => {
        if (newType === 'POLAROID') {
          this.bookPhotos = this.bookPhotos.filter(p => p.id !== item.id);
          item.mediaType = 'POLAROID';
          item.category = 'POLAROID';
          item.type = 'POLAROID';
          item.isCover = false;
          this.polaroidPhotos = [...this.polaroidPhotos, item];
        } else {
          this.polaroidPhotos = this.polaroidPhotos.filter(p => p.id !== item.id);
          item.mediaType = 'BOOK';
          item.category = 'BOOK';
          item.type = 'BOOK';
          this.bookPhotos = [...this.bookPhotos, item];
        }

        // Sincroniza lista geral galleryPhotos
        const updatedGal = this.galleryPhotos().map(p => {
          if (p.id === item.id) {
            return {
              ...p,
              category: newType,
              mediaType: newType,
              type: newType,
              isCover: updated.isCover ?? (newType === 'POLAROID' ? false : p.isCover),
              orderIndex: updated.displayOrder ?? p.orderIndex
            };
          }
          return p;
        });
        this.galleryPhotos.set(updatedGal);

        // Se a foto movida era capa, atualiza capa preview
        if (newType === 'POLAROID') {
          const firstBook = updatedGal.find(p => p.category === 'BOOK');
          if (firstBook?.url) {
            this.modelForm.patchValue({ primaryPhotoUrl: firstBook.url });
          }
        }
        this._atualizaFotoCapaPreviewDisparo();

        this.showToast(`✅ Fotografia movida para ${newType === 'POLAROID' ? 'Polaroids Técnicas' : 'Book Editorial'}!`, 'success');
      },
      error: (err) => {
        console.error('Erro ao alternar tipo de foto:', err);
        const msg = err?.error?.detail || err?.error?.message || 'Erro ao alternar categoria da fotografia.';
        this.showToast(msg, 'error');
      }
    });
  }

  readonly galleryPhotos = signal<GalleryPhoto[]>([]);
  /**
   * IDs de midias com ID REAL (nao temp-) existentes no banco de dados NO MOMENTO
   * de carregar a pagina. Usado para detectar o que foi REMOVIDO da grid
   * (por QUALQUER motivo: botao X, drag-drop, selecao em massa, etc) e adicionar
   * em pendingDeleteIds.
   * Funciona para apagar 1 ou 50 fotos, QUANTAS QUISER.
   */
  readonly originalMediaIds = signal<Set<string>>(new Set());
  /**
   * IDs de midias com id REAL (nao temp-) que o usuario apagou da grid.
   * Serao EXCLUIDOS DEFINITIVAMENTE do banco model_media + bucket Supabase
   * SOMENTE ao clicar em SALVAR / ATUALIZAR MODELO.
   */
  readonly pendingDeleteIds = signal<Set<string>>(new Set());
  readonly pendingDeleteCount = computed(() => Array.from(this.pendingDeleteIds()).filter(id => !!id && !String(id).startsWith('temp-')).length);

  /**
   * Compara a galeria ATUAL com os IDs ORIGINAIS do banco.
   * TODO ID REAL que existia no original e NAO EXISTE mais na galeria atual
   * ENTRA em pendingDeleteIds (para exclusao definitiva ao salvar).
   * Funciona apagar 1, 10 ou 50 fotos ao mesmo tempo.
   */
  private detectIdsRemovedFromDatabase(galeriaAtual: GalleryPhoto[]): void {
    const idsAtuais = new Set(
      (galeriaAtual || []).filter(p => p?.id && !String(p.id).startsWith('temp-')).map(p => String(p.id))
    );
    const novos = new Set(this.pendingDeleteIds());
    // Todos os IDs ORIGINAIS do banco que FORAM REMOVIDOS da lista atual:
    for (const originalId of Array.from(this.originalMediaIds())) {
      if (!idsAtuais.has(originalId)) {
        novos.add(String(originalId));
      }
    }
    // Se o usuario DEFEZ (volta a foto com ID na lista), remove de pendingDeleteIds:
    for (const idAtual of Array.from(idsAtuais)) {
      if (novos.has(idAtual)) {
        novos.delete(idAtual);
      }
    }
    this.pendingDeleteIds.set(novos);
  }

  onPhotoRemoved(excluida: GalleryPhoto): void {
    if (!excluida) return;
    if (excluida.file) {
      this.selectedFiles = this.selectedFiles.filter(f => f !== excluida.file);
    }
    if (!excluida.id || String(excluida.id).startsWith('temp-')) return;
    // Evento direto: marca de qualquer jeito, e depois detectIdsRemovedFromDatabase valida.
    const nova = new Set(this.pendingDeleteIds());
    nova.add(String(excluida.id));
    this.pendingDeleteIds.set(nova);
  }

  onPhotosChanged(updatedPhotos: GalleryPhoto[]): void {
    this.galleryPhotos.set(updatedPhotos || []);
    this.bookPhotos = (updatedPhotos || []).filter(m => (m.category || m.mediaType || m.type)?.toUpperCase() === 'BOOK');
    this.polaroidPhotos = (updatedPhotos || []).filter(m => (m.category || m.mediaType || m.type)?.toUpperCase() === 'POLAROID');
    // 🔴 DETECCAO UNIVERSAL: apagar 1, 10, 50 fotos, QUALQUER METODO. Tudo entra no pending.
    this.detectIdsRemovedFromDatabase(updatedPhotos || []);
    if (!updatedPhotos || updatedPhotos.length === 0) {
      this._atualizaFotoCapaPreviewDisparo(); // Forca atualizar mesmo que galeria ficou vazia (placeholder WB)
      return;
    }

    // 🔴 Dispara atualizacao da FOTO DE CAPA no card preview topo
    this._atualizaFotoCapaPreviewDisparo();

    const coverPhoto = updatedPhotos.find((p) => Boolean(p.isCover)) || updatedPhotos[0];
    if (coverPhoto?.url) {
      this.modelForm.patchValue({ primaryPhotoUrl: coverPhoto.url });
    }

    // Se a foto marcada como capa tem ID REAL (nao eh previa temp-), chamar endpoint setModelCover
    // para PERSISTIR isCover=true na tabela model_media + sincronizar primary_photo_url models.
    const idAtual = this.modelId();
    const coverComId = updatedPhotos.find((p) => Boolean(p.isCover) && Boolean(p.id) && !String(p.id).startsWith('temp-'));
    if (idAtual && coverComId && coverComId.id) {
      this.adminModelService.setModelCover(idAtual, coverComId.id).subscribe({
        next: (saved) => {
          console.info('[onPhotosChanged] Capa persistida no banco. mediaId=' + saved.id);
          // Apos sucesso, recarrega galeria e sincroniza primaryPhotoUrl com valor salvo
          this._refreshGalleryFromDatabase(idAtual).subscribe(() => {
            const galAtual = this.galleryPhotos();
            const capaAtualizada = galAtual.find(x => Boolean(x.isCover)) || galAtual[0];
            if (capaAtualizada?.url) {
              this.modelForm.patchValue({ primaryPhotoUrl: capaAtualizada.url });
            }
          });
        },
        error: (err) => {
          const msg = err?.error?.detail || err?.error?.message || 'Falha ao salvar nova capa no banco.';
          console.warn('[onPhotosChanged] setModelCover falhou:', err);
          this.showToast(msg, 'error');
        }
      });
    }
  }

  /**
   * Busca a GALERIA REAL do modelo no Backend (tabela model_media),
   * substitui placeholders hardcoded (samplePhotos) que faziam as 3 fotos
   * "erradas" aparecerem no Admin enquanto no Publico apareciam 41 corretas.
   *
   * Usado SEMPRE:
   *  - no loadModelData (1a vez abrindo a tela)
   *  - depois de upload, exclusao, reorder, marcacao de capa.
   * Garante que Admin tenha a MESMA lista que Publico (1:1).
   */
  /**
   * Resolve URL segura do Supabase Storage utilizando filePath se rawFileUrl vier incompleta.
   */
  private _resolveSafeMediaUrl(filePath?: string | null, rawFileUrl?: string | null): string {
    const SUPABASE_PUBLIC_BASE = 'https://stmytwsdlonpnirqiufq.supabase.co/storage/v1/object/public/models-media';

    if (!rawFileUrl && !filePath) return 'assets/images/placeholder-polaroid.jpg';
    let url = (rawFileUrl || '').trim();
    const pathClean = (filePath || '').trim().replace(/^\//, '');

    if (url.includes('zmpqmdizqgnpnirqiufq.supabase.co')) {
      url = url.replace('zmpqmdizqgnpnirqiufq.supabase.co', 'stmytwsdlonpnirqiufq.supabase.co');
    }

    if (url.startsWith('blob:') || url.startsWith('data:')) {
      return url;
    }

    if (url.startsWith('http') && !url.endsWith('/') && !url.endsWith('/models-media') && !url.endsWith('/candidates-uploads')) {
      return url;
    }

    if (pathClean && !pathClean.startsWith('models/')) {
      return `${SUPABASE_PUBLIC_BASE}/${pathClean}`;
    }

    return url || 'assets/images/placeholder-polaroid.jpg';
  }

  /**
   * Converte mídias do DTO do modelo (model.media) em GalleryPhoto[],
   * garantindo URLs completas e seguras.
   */
  private _mapMediaDtoToGalleryPhotos(mediaDtos: any[]): GalleryPhoto[] {
    if (!mediaDtos || mediaDtos.length === 0) return [];
    return mediaDtos
      .filter(item => {
        const type = String(item.mediaType || item.type || item.category || '').toUpperCase();
        return type !== 'COMPOSITE';
      })
      .map((item, idx) => {
        const rawFileUrl = typeof item.fileUrl === 'string' && item.fileUrl.trim()
          ? item.fileUrl.trim()
          : (typeof item.url === 'string' && item.url.trim()
            ? item.url.trim()
            : (typeof item.mediaUrl === 'string' && item.mediaUrl.trim()
              ? item.mediaUrl.trim()
              : (typeof item.previewUrl === 'string' && item.previewUrl.trim() ? item.previewUrl.trim() : '')));
        const filePath = typeof item.filePath === 'string' ? item.filePath.trim() : (typeof item.storagePath === 'string' ? item.storagePath.trim() : '');
        let safeUrl = this._resolveSafeMediaUrl(filePath, rawFileUrl);
        if (safeUrl && !safeUrl.includes('?v=')) {
          safeUrl = safeUrl + '?v=' + Math.floor(Date.now() / 3_600_000);
        }

        const mediaType = String(item.mediaType || item.type || item.category || 'BOOK').toUpperCase();
        const isPolaroid = mediaType.includes('POLAROID')
          || mediaType === 'POLAROID_FRONT'
          || mediaType === 'POLAROID_SIDE'
          || mediaType === 'POLAROID_BODY';

        const isCandidateUpload = (filePath && filePath.includes('candidates-uploads'))
          || (rawFileUrl && rawFileUrl.includes('candidates-uploads'))
          || (safeUrl && safeUrl.includes('candidates-uploads'));
        const isFromApply = Boolean(item.isFromApply || isCandidateUpload);
        const isLowResolution = Boolean(item.isLowResolution || isFromApply);

        return {
          id: String(item.id || 'media-' + idx),
          url: safeUrl,
          fileUrl: safeUrl,
          mediaUrl: safeUrl,
          previewUrl: safeUrl,
          title: item.title || item.originalFilename || item.filename || 'Foto do modelo',
          filePath: filePath,
          category: (isPolaroid ? 'POLAROID' : 'BOOK') as 'BOOK' | 'POLAROID',
          mediaType: (isPolaroid ? 'POLAROID' : 'BOOK') as any,
          type: (isPolaroid ? 'POLAROID' : 'BOOK') as any,
          orderIndex: Number(item.displayOrder ?? item.orderIndex ?? idx),
          isCover: Boolean(item.isCover),
          isActive: Boolean(item.isActive ?? true),
          isUploaded: true,
          isFromApply,
          isLowResolution
        };
      })
      .sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0));
  }

  /**
   * Busca a GALERIA REAL do modelo no Backend (tabela model_media).
   * Se a chamada individual retornar vazia, utiliza as mídias de modelFallback.media.
   * Se nenhuma mídia existir, utiliza a foto de capa como fallback para evitar galeria vazia.
   */
  _refreshGalleryFromDatabase(forModelId: string, modelFallback?: ModelAdminItem): Observable<GalleryPhoto[]> {
    return this.adminModelService.getModelMedia(forModelId).pipe(
      tap((listaRealOrdenada) => {
        const media = (listaRealOrdenada && listaRealOrdenada.length > 0)
          ? listaRealOrdenada
          : (modelFallback?.media || []);
        this.populatePhotos(media);
      })
    );
  }

  loadModelData(id: string): void {
    this.isLoading.set(true);
    this.adminModelService.getModelById(id).subscribe({
      next: (model) => {
        this.modelForm.patchValue({
          stageName: model.stageName,
          gender: model.gender,
          isStar: model.isStar,
          isFeaturedHome: model.isFeaturedHome,
          featuredOrder: model.featuredOrder,
          isActive: model.isActive,
          primaryPhotoUrl: model.primaryPhotoUrl || model.coverPhotoUrl || '',
          instagramUrl: model.instagramUrl || '',
          birthDate: model.birthDate || '',
          heightCm: model.heightCm,
          city: model.city || '',
          nationality: model.nationality || 'Brasileira',
          dressSize: model.dressSize || '',
          shoeSize: model.shoeSize || '',
          bustChestCm: model.bustChestCm,
          waistCm: model.waistCm,
          hipsCm: model.hipsCm,
          hairColor: this.normalizeColor(model.hairColor),
          eyesColor: this.normalizeColor(model.eyesColor)
        });
        this.updateComputedAge(model.birthDate);

        // Se o DTO do modelo já trouxer model.media, popule imediatamente os arrays de fotos locais:
        const mediaList = model.media || [];
        if (mediaList && mediaList.length > 0) {
          this.bookPhotos = mediaList.filter((m: any) => (m.mediaType || m.type)?.toUpperCase() === 'BOOK');
          this.polaroidPhotos = mediaList.filter((m: any) => (m.mediaType || m.type)?.toUpperCase() === 'POLAROID');
          this.populatePhotos(mediaList);
          this.isLoading.set(false);
          const total = this.galleryPhotos().length;
          if (total > 0) {
            this.showToast(`Galeria carregada: ${total} foto(s) reais do banco.`, 'success');
          }
        } else {
          // Se o endpoint de detalhes do modelo não trouxer mídias, faça a chamada explícita para o endpoint de mídias:
          this.adminModelService.getModelMedia(id).subscribe({
            next: (fetchedMediaList) => {
              const list = fetchedMediaList || [];
              this.bookPhotos = list.filter((m: any) => (m.mediaType || m.type)?.toUpperCase() === 'BOOK');
              this.polaroidPhotos = list.filter((m: any) => (m.mediaType || m.type)?.toUpperCase() === 'POLAROID');
              this.populatePhotos(list);
              this.isLoading.set(false);
              const total = this.galleryPhotos().length;
              if (total > 0) {
                this.showToast(`Galeria carregada: ${total} foto(s) reais do banco.`, 'success');
              } else {
                this.showToast('Modelo carregado. Envie as fotos do book / polaroids.', 'success');
              }
            },
            error: (err) => {
              this.isLoading.set(false);
              console.error('Erro ao carregar fotos:', err);
              this.showToast('Erro ao carregar fotos do modelo.', 'error');
            }
          });
        }
      },
      error: (err) => {
        this.isLoading.set(false);
        const msg = err?.error?.detail || err?.error?.message || 'Erro ao carregar dados do modelo.';
        this.showToast(msg, 'error');
        console.error('[loadModelData] ERRO:', err);
      }
    });
  }

  onCompositeFileSelected(file: File | null): void {
    if (file && file.size > 25 * 1024 * 1024) {
      this.showToast(`O arquivo "${file.name}" excede o limite máximo permitido de 25 MB para o composite.`, 'error');
      this.selectedCompositeFile = null;
      return;
    }
    this.selectedCompositeFile = file;
    if (file) {
      this.showToast(`Composite "${file.name}" selecionado com sucesso. Será salvo com o modelo.`, 'success');
    }
  }

  onFilesUploaded(files: File[]): void {
    if (!files || files.length === 0) return;
    const current = this.modelId();
    if (!current) {
      this.selectedFiles = [...this.selectedFiles, ...files];
      this.showToast(`${files.length} foto(s) selecionada(s) (até 15 MB). Serão salvas ao cadastrar o modelo.`, 'success');
      return;
    }

    this.showToast(`${files.length} arquivo(s) selecionado(s). Fazendo upload via Backend para o Supabase...`, 'success');

    let enviadosOk = 0;
    let falhas = 0;

    const uploadEmSequencia = files.reduce((accPromessa, file, idx) => {
      // 1) Procura previa temp- correspondente MARCA como UPLOADING (IMUTAVEL)
      let previews = [...this.galleryPhotos()];
      let tempIdx = previews.findIndex(p => p?.id && String(p.id).startsWith('temp-') && !(p as any).__uploadStarted);
      const tempPhoto = tempIdx >= 0 ? { ...previews[tempIdx], _index: tempIdx } : null;
      if (tempIdx >= 0) {
        previews[tempIdx] = { ...previews[tempIdx], isUploading: true, uploadProgress: 1 };
        (previews[tempIdx] as any).__uploadStarted = true;
        this.galleryPhotos.set([...previews]);
      }

      const fileCategory: 'BOOK' | 'POLAROID' = (
        (tempPhoto?.category || (file as any).category || (file as any).mediaType || (file as any).type || 'BOOK') + ''
      ).toUpperCase() === 'POLAROID' ? 'POLAROID' : 'BOOK';

      // 2) Upload SEQUENCIAL (evita flood HTTP no bucket Supabase)
      return accPromessa.then(() => new Promise<void>((resolve) => {
        this.adminModelService.uploadModelMedia(current, file, fileCategory, false).subscribe({
          next: (ev) => {
            if (ev.type === HttpEventType.UploadProgress && ev.total && tempPhoto) {
              const p = Math.round((100 * ev.loaded) / ev.total);
              previews = [...this.galleryPhotos()];
              if (previews[tempPhoto._index]?.id === tempPhoto.id) {
                previews[tempPhoto._index] = { ...previews[tempPhoto._index], uploadProgress: p };
                this.galleryPhotos.set([...previews]);
              }
            } else if (ev.type === HttpEventType.Response && ev.body) {
              enviadosOk++;
              const saved = ev.body;
              // Remove previa temp- (imutavel) e insere FOTO REAL do banco
              let galeriaNova = [...this.galleryPhotos()].filter(p => !(p?.id && String(p.id).startsWith('temp-')));
              const isSavedPolaroid = String(saved.mediaType || (saved as any).type || fileCategory).toUpperCase() === 'POLAROID';
              const fotoReal: GalleryPhoto = {
                id: saved.id,
                url: saved.fileUrl + (saved.fileUrl.includes('?v=') ? '' : ('?v=' + Math.floor(Date.now() / 3_600_000))),
                filePath: saved.filePath,
                category: isSavedPolaroid ? 'POLAROID' : 'BOOK',
                mediaType: isSavedPolaroid ? 'POLAROID' : 'BOOK',
                type: isSavedPolaroid ? 'POLAROID' : 'BOOK',
                orderIndex: galeriaNova.length,
                isCover: saved.isCover || (galeriaNova.length === 0 && !isSavedPolaroid),
                isActive: true,
                isUploaded: true,
                isUploading: false,
                uploadProgress: 100
              };
              galeriaNova = [...galeriaNova, fotoReal];
              if (galeriaNova.length === 1 && !isSavedPolaroid) galeriaNova[0].isCover = true;
              else if (!galeriaNova.some(p => Boolean(p.isCover))) {
                const firstBook = galeriaNova.find(p => p.category === 'BOOK');
                if (firstBook) firstBook.isCover = true;
              }
              galeriaNova = galeriaNova.map((p, i) => ({ ...p, orderIndex: i }));

              this.galleryPhotos.set(galeriaNova);
              this.bookPhotos = galeriaNova.filter(m => (m.category || m.mediaType || m.type)?.toUpperCase() === 'BOOK');
              this.polaroidPhotos = galeriaNova.filter(m => (m.category || m.mediaType || m.type)?.toUpperCase() === 'POLAROID');

              const cover = galeriaNova.find(p => Boolean(p.isCover)) || galeriaNova[0];
              if (cover?.url) this.modelForm.patchValue({ primaryPhotoUrl: cover.url });

              this.showToast(`✅ Foto ${idx + 1}/${files.length} enviada com sucesso (já está salva no banco)`, 'success');
              resolve();
            }
          },
          error: (err) => {
            falhas++;
            if (tempPhoto) {
              previews = [...this.galleryPhotos()];
              if (previews[tempPhoto._index]?.id === tempPhoto.id) {
                previews[tempPhoto._index] = { ...previews[tempPhoto._index], isUploading: false, uploadProgress: 0 };
                this.galleryPhotos.set([...previews]);
              }
            }
            const msg = err?.error?.detail || err?.error?.message || `Falha ao enviar foto ${idx + 1}. Verifique tamanho e tipo.`;
            this.showToast(msg, 'error');
            console.error('Erro upload foto idx=' + idx, err);
            resolve();
          }
        });
      }));
    }, Promise.resolve());

    // AO FINAL DE TODOS -> recarrega galeria REAL do banco (sincronia 100% Admin = Perfil Publico)
    uploadEmSequencia.finally(() => {
      this._refreshGalleryFromDatabase(current).subscribe(() => {
        if (falhas === 0) {
          this.showToast(`📚 Upload concluído (${enviadosOk}/${files.length}). Galeria sincronizada com o banco (idêntica ao perfil público).`, 'success');
        } else {
          this.showToast(`${enviadosOk} OK. ${falhas} falha(s). Galeria sincronizada.`, 'error');
        }
      });
    });
  }

  setGender(gender: ModelGender): void {
    this.modelForm.patchValue({ gender });
  }

  selectSamplePhoto(url: string): void {
    this.modelForm.patchValue({ primaryPhotoUrl: url });
    const current = this.galleryPhotos();
    if (current.length === 0) {
      this.galleryPhotos.set([
        { id: 'cover-sample', url, category: 'BOOK', orderIndex: 0, isCover: true }
      ]);
    } else {
      current[0].url = url;
      this.galleryPhotos.set([...current]);
    }
  }

  updateComputedAge(dateString?: string | null): void {
    if (!dateString) {
      this.computedAge.set(null);
      return;
    }
    const birth = new Date(dateString);
    if (isNaN(birth.getTime())) {
      this.computedAge.set(null);
      return;
    }
    const today = new Date();
    let age = today.getFullYear() - birth.getFullYear();
    const m = today.getMonth() - birth.getMonth();
    if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) {
      age--;
    }
    this.computedAge.set(age >= 0 ? age : null);
  }

  onSubmit(): void {
    if (this.modelForm.invalid) {
      this.modelForm.markAllAsTouched();
      this.showToast('Por favor, verifique os campos obrigatórios em destaque.', 'error');
      return;
    }

    this.isSaving.set(true);
    const formVal = this.modelForm.value;

    const parseDecimal = (val: any): number | null => {
      if (val === null || val === undefined || val === '') return null;
      if (typeof val === 'number') return isNaN(val) ? null : val;
      const strVal = String(val).trim().replace(',', '.');
      const num = parseFloat(strVal);
      return isNaN(num) ? null : num;
    };

    const payload: ModelFormData = {
      stageName: formVal.stageName.trim(),
      gender: formVal.gender,
      isStar: Boolean(formVal.isStar),
      isFeaturedHome: Boolean(formVal.isFeaturedHome),
      featuredOrder: formVal.isFeaturedHome && formVal.featuredOrder ? Number(formVal.featuredOrder) : null,
      isActive: Boolean(formVal.isActive),
      primaryPhotoUrl: formVal.primaryPhotoUrl ? formVal.primaryPhotoUrl.trim() : null,
      instagramUrl: formVal.instagramUrl ? formVal.instagramUrl.trim() : null,
      birthDate: formVal.birthDate || null,
      heightCm: parseDecimal(formVal.heightCm),
      city: formVal.city ? formVal.city.trim() : null,
      nationality: formVal.nationality ? formVal.nationality.trim() : null,
      dressSize: formVal.dressSize ? formVal.dressSize.trim() : null,
      shoeSize: formVal.shoeSize ? formVal.shoeSize.trim() : null,
      bustChestCm: parseDecimal(formVal.bustChestCm),
      waistCm: parseDecimal(formVal.waistCm),
      hipsCm: parseDecimal(formVal.hipsCm),
      hairColor: formVal.hairColor ? formVal.hairColor.trim() : null,
      eyesColor: formVal.eyesColor ? formVal.eyesColor.trim() : null
    };

    const currentId = this.modelId();

    // 🚀 NOVO MODELO: Criação textual encadeada com upload das fotos selecionadas e composite via switchMap
    if (!currentId) {
      const filesToSend = this.selectedFiles.length > 0
        ? this.selectedFiles.map(f => ({
            file: f,
            category: (((f as any).category || (f as any).mediaType || (f as any).type || 'BOOK') + '').toUpperCase() === 'POLAROID' ? ('POLAROID' as const) : ('BOOK' as const),
            mediaType: (((f as any).category || (f as any).mediaType || (f as any).type || 'BOOK') + '').toUpperCase() === 'POLAROID' ? ('POLAROID' as const) : ('BOOK' as const),
            type: (((f as any).category || (f as any).mediaType || (f as any).type || 'BOOK') + '').toUpperCase() === 'POLAROID' ? ('POLAROID' as const) : ('BOOK' as const)
          }))
        : this.galleryPhotos()
            .map(p => (p as any).file ? {
              file: (p as any).file as File,
              category: String(p.category || p.mediaType || p.type || 'BOOK').toUpperCase() === 'POLAROID' ? ('POLAROID' as const) : ('BOOK' as const),
              mediaType: String(p.category || p.mediaType || p.type || 'BOOK').toUpperCase() === 'POLAROID' ? ('POLAROID' as const) : ('BOOK' as const),
              type: String(p.category || p.mediaType || p.type || 'BOOK').toUpperCase() === 'POLAROID' ? ('POLAROID' as const) : ('BOOK' as const)
            } : null)
            .filter((item): item is { file: File; category: 'BOOK' | 'POLAROID'; mediaType: 'BOOK' | 'POLAROID'; type: 'BOOK' | 'POLAROID' } => item !== null && item.file instanceof File);

      this.adminModelService.createModel(payload).pipe(
        switchMap(createdModel => {
          const tasks$: Observable<any>[] = [];
          if (filesToSend.length > 0) {
            tasks$.push(this.adminModelService.uploadBatch(createdModel.id, filesToSend));
          }
          if (this.selectedCompositeFile) {
            tasks$.push(this.adminModelService.uploadComposite(createdModel.id, this.selectedCompositeFile));
          }
          if (tasks$.length === 0) return of(createdModel);
          return forkJoin(tasks$).pipe(map(() => createdModel));
        })
      ).subscribe({
        next: (createdModel) => {
          this.isSaving.set(false);
          const hasPhotos = filesToSend.length > 0;
          const hasComposite = !!this.selectedCompositeFile;
          let successMsg = '✅ Modelo cadastrado com sucesso!';
          if (hasPhotos && hasComposite) {
            successMsg = '✅ Modelo, fotos e composite cadastrados com sucesso!';
          } else if (hasPhotos) {
            successMsg = '✅ Modelo e fotos cadastrados com sucesso!';
          } else if (hasComposite) {
            successMsg = '✅ Modelo e composite cadastrados com sucesso!';
          }
          this.showToast(successMsg, 'success');
          this.saved.emit(createdModel);
          this.selectedFiles = [];
          this.selectedCompositeFile = null;
          setTimeout(() => {
            if (this.route.snapshot.url.some(s => s.path === 'new') || this.route.snapshot.paramMap.get('id')) {
              this.router.navigate(['/admin/models']);
            }
          }, 1000);
        },
        error: (err) => {
          this.isSaving.set(false);
          const msg = err?.error?.detail || err?.error?.message || err?.message || 'Erro ao cadastrar modelo, fotos ou composite.';
          this.showToast(msg, 'error');
          console.error('[onSubmit] Erro:', err);
        }
      });
      return;
    }

    // 🔴 Todos os IDs reais a excluir. Nao confia so no click de photoRemoved: usa PENDING + diff original.
    const todosIdsParaApagar: string[] = Array.from(new Set([
      ...Array.from(this.pendingDeleteIds()),
      ...(this.detectIdsRemovedFromDatabase_Collect(this.galleryPhotos()) || [])
    ])).filter(id => id && !String(id).startsWith('temp-'));

    // ============================================================
    // 1) EXCLUIR DEFINITIVAMENTE TODAS as fotos em lotes de 3 (chunkSize=3).
    // CADA DELETE tem try/catch INDIVIDUAL: um falhar NAO PARA os outros!
    // (Requisito do usuario: apagar MUITAS, quantas quiser, 50+ se precisar)
    // ============================================================
    const excluirEmLotes = new Promise<{ success: number; failed: number }>((resolveAll) => {
      if (!currentId || todosIdsParaApagar.length === 0) {
        resolveAll({ success: 0, failed: 0 });
        return;
      }
      let s = 0;
      let f = 0;
      let idx = 0;
      const total = todosIdsParaApagar.length;
      // Chunk de 3 em 3 (evita HTTP 429 Too Many Requests no bucket Supabase)
      const chunkSize = 3;
      const runChunk = () => {
        if (idx >= total) {
          resolveAll({ success: s, failed: f });
          return;
        }
        const chunkIds = todosIdsParaApagar.slice(idx, idx + chunkSize);
        idx += chunkSize;
        const deleteCalls = chunkIds.map(mediaId =>
          firstValueFrom(
            this.adminModelService.deleteModelMedia(currentId, mediaId).pipe(
              tap(() => { s++; }),
              catchError((err) => {
                console.warn('[onSubmit] Falha ao apagar media=' + mediaId, err);
                f++;
                return of(void 0);
              })
            )
          )
        );
        Promise.all(deleteCalls).finally(() => runChunk());
      };
      runChunk();
    });

    const saveModelOperation = () => {
      if (currentId) {
        return firstValueFrom(
          this.adminModelService.updateModel(currentId, payload).pipe(
            tap((saved) => {
              // Após salvar DADOS: recarrega galeria REAL (confirmacao final sincronia 1:1)
              this._refreshGalleryFromDatabase(currentId).subscribe({
                next: () => {
                  this.showToast('✅ Modelo atualizado com sucesso! Exclusoes, se houver, aplicadas.', 'success');
                  this.saved.emit(saved);
                  setTimeout(() => {
                    if (this.route.snapshot.paramMap.get('id')) {
                      this.router.navigate(['/admin/models']);
                    }
                  }, 1500);
                },
                error: () => {
                  this.saved.emit(saved);
                }
              });
            })
          )
        );
      } else {
        return firstValueFrom(
          this.adminModelService.createModel(payload).pipe(
            tap((saved) => {
              this.showToast('✅ Modelo cadastrado com sucesso!', 'success');
              this.saved.emit(saved);
              setTimeout(() => {
                if (this.route.snapshot.url.some(s => s.path === 'new') || this.route.snapshot.paramMap.get('id')) {
                  this.router.navigate(['/admin/models']);
                }
              }, 1000);
            })
          )
        );
      }
    };

    if (todosIdsParaApagar.length === 0) {
      saveModelOperation().catch((err) => {
        this.isSaving.set(false);
        const msg = err?.error?.detail || err?.error?.message || err?.message || 'Falha geral ao salvar modelo.';
        this.showToast(msg, 'error');
        console.error('[onSubmit] Erro geral:', err);
      }).finally(() => {
        setTimeout(() => { this.isSaving.set(false); }, 500);
      });
      return;
    }

    excluirEmLotes.then((resultDelete) => {
      const { success, failed } = resultDelete;
      this.pendingDeleteIds.set(new Set()); // zera tudo

      // Toast info de exclusao (se teve pelo menos 1)
      if (success + failed > 0) {
        if (failed === 0) {
          this.showToast(`🗑️ ${success} foto(s) apagada(s) DEFINITIVAMENTE do banco e storage Supabase.`, 'success');
        } else {
          this.showToast(`Exclusao: ${success} OK. ${failed} falha(s). Verifique se as fotos de falha ainda existem.`, 'error');
        }
      }

      return saveModelOperation();
    }).catch((err) => {
      this.isSaving.set(false);
      const msg = err?.error?.detail || err?.error?.message || err?.message || 'Falha geral ao salvar modelo.';
      this.showToast(msg, 'error');
      console.error('[onSubmit] Erro geral:', err);
    }).finally(() => {
      setTimeout(() => { this.isSaving.set(false); }, 500);
    });
  }

  /**
   * Helper que coleta IDs a APAGAR: compara a galeria atual com os IDs ORIGINAIS
   * do banco. Retorna array de strings com os IDs que sumiram.
   * (usado no onSubmit como DUPLA CHECAGEM antes de apagar).
   */
  private detectIdsRemovedFromDatabase_Collect(galeriaAtual: GalleryPhoto[]): string[] {
    const idsAtuais = new Set(
      (galeriaAtual || []).filter(p => p?.id && !String(p.id).startsWith('temp-')).map(p => String(p.id))
    );
    const out: string[] = [];
    for (const origId of Array.from(this.originalMediaIds())) {
      if (!idsAtuais.has(origId)) {
        out.push(String(origId));
      }
    }
    return out;
  }

  onCancel(): void {
    this.cancelled.emit();
    if (this.route.snapshot.paramMap.get('id') || this.route.snapshot.url.some(s => s.path === 'new')) {
      this.router.navigate(['/admin/models']);
    }
  }

  showToast(message: string, type: 'success' | 'error'): void {
    this.toast.set({ message, type });
    setTimeout(() => {
      this.toast.set(null);
    }, 4000);
  }
}
