import { Component, Input, Output, EventEmitter, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { GalleryPhoto, PhotoCategory } from '../../../../../shared/models/gallery.model';
import { ImageProcessingService } from '../../../../../core/services/image-processing.service';
import { AdminModelService } from '../../../../../core/services/admin-model.service';

export type PhotoFilterType = 'ALL' | 'BOOK' | 'POLAROID';

@Component({
  selector: 'app-photo-uploader-grid',
  standalone: true,
  imports: [CommonModule, DragDropModule],
  templateUrl: './photo-uploader-grid.component.html',
  styleUrls: ['./photo-uploader-grid.component.scss']
})
export class PhotoUploaderGridComponent {
  private readonly imageProcessingService = inject(ImageProcessingService);
  private readonly adminModelService = inject(AdminModelService);

  @Input() modelId: string | null = null;
  @Input() photos: GalleryPhoto[] = [];
  @Output() photosChange = new EventEmitter<GalleryPhoto[]>();
  @Output() fileUploaded = new EventEmitter<File[]>();
  @Output() validationError = new EventEmitter<string>();
  @Output() categoryChanged = new EventEmitter<{ photo: GalleryPhoto; newCategory: 'BOOK' | 'POLAROID' }>();
  /**
   * Evento disparado quando o usuario clica em EXCLUIR FOTO (botao vermelho ✕).
   * Passa a FOTO QUE FOI REMOVIDA. O pai (model-form) deve:
   *  - Se photo.id existir e NAO comecar com 'temp-', marcar em pendingDeleteIds para
   *    EXCLUIR DEFINITIVAMENTE no banco+storage SOB CLIQUE DE SALVAR.
   *  - Se photo.id comecar com 'temp-' (previa local sem upload), apenas ignorar.
   */
  @Output() photoRemoved = new EventEmitter<GalleryPhoto>();

  isDraggingOverZone = false;
  photoFilter: PhotoFilterType = 'ALL';

  /** Compatibilidade com testes legados e chamadas diretas */
  get selectedCategoryFilter(): PhotoFilterType {
    return this.photoFilter;
  }

  set selectedCategoryFilter(filter: PhotoFilterType) {
    this.photoFilter = filter;
  }

  setPhotoFilter(filter: PhotoFilterType): void {
    this.photoFilter = filter;
  }

  get allPhotos(): GalleryPhoto[] {
    return this.photos || [];
  }

  set allPhotos(photos: GalleryPhoto[]) {
    this.photos = photos;
  }

  get filteredPhotos(): GalleryPhoto[] {
    if (this.photoFilter === 'BOOK') {
      return this.allPhotos.filter(p => ((p as any).mediaType || (p as any).type || p.category)?.toUpperCase() === 'BOOK');
    }
    if (this.photoFilter === 'POLAROID') {
      return this.allPhotos.filter(p => ((p as any).mediaType || (p as any).type || p.category)?.toUpperCase() === 'POLAROID');
    }
    return this.allPhotos;
  }

  getPhotoGlobalIndex(photo: GalleryPhoto): number {
    const idx = (this.photos || []).findIndex(p => p.id === photo.id);
    return idx >= 0 ? idx : 0;
  }

  readonly maxPhotos = 15;
  readonly maxSizeBytes = 15 * 1024 * 1024; // 15MB
  readonly allowedTypes = ['image/jpeg', 'image/png', 'image/webp'];

  get bookPhotos(): GalleryPhoto[] {
    return (this.photos || []).filter(p => (p.category || (p as any).mediaType || (p as any).type || 'BOOK').toUpperCase() === 'BOOK');
  }

  get polaroidPhotos(): GalleryPhoto[] {
    return (this.photos || []).filter(p => (p.category || (p as any).mediaType || (p as any).type || '').toUpperCase() === 'POLAROID');
  }

  get bookPhotosCount(): number {
    return this.bookPhotos.length;
  }

  get polaroidPhotosCount(): number {
    return this.polaroidPhotos.length;
  }

  onPhotoError(photo: GalleryPhoto): void {
    if (!photo) return;
    const placeholder = 'assets/images/placeholder-polaroid.jpg';

    if (!(photo as any).__triedFallback && photo.filePath && !photo.filePath.startsWith('models/')) {
      (photo as any).__triedFallback = true;
      const clean = photo.filePath.replace(/^\//, '');
      const fallbackUrl = `https://stmytwsdlonpnirqiufq.supabase.co/storage/v1/object/public/models-media/${clean}?v=${Date.now()}`;
      photo.fileUrl = fallbackUrl;
      photo.url = fallbackUrl;
      photo.mediaUrl = fallbackUrl;
      photo.previewUrl = fallbackUrl;
    } else {
      (photo as any).__triedFallback = true;
      photo.fileUrl = placeholder;
      photo.url = placeholder;
      photo.mediaUrl = placeholder;
      photo.previewUrl = placeholder;
    }
  }

  get currentTargetCategory(): PhotoCategory {
    return this.selectedCategoryFilter === 'POLAROID' ? 'POLAROID' : 'BOOK';
  }

  get isUploadDisabled(): boolean {
    const targetCount = this.currentTargetCategory === 'POLAROID' 
      ? this.polaroidPhotosCount 
      : this.bookPhotosCount;
    return targetCount >= this.maxPhotos;
  }

  get currentCategoryAvailableSlots(): number {
    const targetCount = this.currentTargetCategory === 'POLAROID' 
      ? this.polaroidPhotosCount 
      : this.bookPhotosCount;
    return Math.max(0, this.maxPhotos - targetCount);
  }

  async onDropFiles(event: DragEvent): Promise<void> {
    event.preventDefault();
    this.isDraggingOverZone = false;
    if (this.isUploadDisabled) {
      this.validationError.emit(`Limite máximo de ${this.maxPhotos} fotos atingido para este álbum.`);
      return;
    }
    if (event.dataTransfer?.files) {
      await this.processSelectedFiles(event.dataTransfer.files);
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    if (!this.isUploadDisabled) {
      this.isDraggingOverZone = true;
    }
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.isDraggingOverZone = false;
  }

  async onFileInputChange(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      await this.processSelectedFiles(input.files);
      input.value = ''; // Reseta input para permitir mesmo arquivo se necessário
    }
  }

  async processSelectedFiles(fileList: FileList | File[]): Promise<void> {
    const targetCat = this.currentTargetCategory;
    const catLabel = targetCat === 'POLAROID' ? 'Polaroids Técnicas' : 'Book Editorial';
    const currentCount = targetCat === 'POLAROID' ? this.polaroidPhotosCount : this.bookPhotosCount;
    const availableSlots = this.maxPhotos - currentCount;

    if (availableSlots <= 0) {
      this.validationError.emit(`Limite máximo de ${this.maxPhotos} fotos atingido para este álbum (${catLabel}).`);
      return;
    }

    const filesArray = Array.from(fileList);
    if (filesArray.length > availableSlots) {
      this.validationError.emit(
        `Limite de ${this.maxPhotos} fotos para ${catLabel}. Apenas as primeiras ${availableSlots} foto(s) foram adicionadas.`
      );
    }

    const validFiles: File[] = [];

    for (const file of filesArray) {
      if (validFiles.length >= availableSlots) {
        break;
      }
      if (!this.allowedTypes.includes(file.type)) {
        this.validationError.emit('Formato de arquivo não suportado. Apenas JPG, PNG ou WebP são permitidos.');
        continue;
      }
      if (file.size > this.maxSizeBytes) {
        this.validationError.emit(`O arquivo "${file.name}" excede o limite máximo permitido de 15 MB.`);
        continue;
      }

      const result = await this.imageProcessingService.processAdminImage(file, {
        maxSizeMb: 15,
        minPixels: 4_000_000
      });
      if (!result.valid) {
        this.validationError.emit(result.error || 'Erro na validação da imagem.');
        continue;
      }

      validFiles.push(result.file || file);
    }

    if (validFiles.length > 0) {
      validFiles.forEach(f => {
        (f as any).category = targetCat;
        (f as any).mediaType = targetCat;
        (f as any).type = targetCat;
      });
      this.fileUploaded.emit(validFiles);

      // ============================================================
      // IMUTABILIDADE: Cria NOVO array com spread operator (NÃO muta input)
      // ============================================================
      const novas: GalleryPhoto[] = validFiles.map((file, idx) => ({
        id: 'temp-' + Math.random().toString(36).substring(2, 9),
        url: URL.createObjectURL(file),
        category: targetCat,
        mediaType: targetCat as any,
        type: targetCat as any,
        orderIndex: this.photos.length + idx,
        isCover: this.photos.length === 0 && idx === 0 && targetCat === 'BOOK',
        file,
        uploadProgress: 100,
        isUploading: false
      }));

      // Novo array (imutavel) + atualiza indices + emite para o pai:
      let novaLista = [...this.photos, ...novas];
      novaLista = this._applyOrderAndCover(novaLista);
      this.photosChange.emit(novaLista);
    }
  }

  onReorder(event: CdkDragDrop<GalleryPhoto[]>): void {
    if (this.photoFilter === 'ALL') {
      let lista = [...this.photos];
      moveItemInArray(lista, event.previousIndex, event.currentIndex);
      lista = this._applyOrderAndCover(lista);
      this.photos = lista;
      this.photosChange.emit(lista);
    } else {
      const filtered = this.filteredPhotos;
      const itemMoved = filtered[event.previousIndex];
      const targetItem = filtered[event.currentIndex];
      if (!itemMoved || !targetItem) return;

      const prevGlobalIdx = this.photos.findIndex(p => p.id === itemMoved.id);
      const currGlobalIdx = this.photos.findIndex(p => p.id === targetItem.id);

      if (prevGlobalIdx >= 0 && currGlobalIdx >= 0) {
        let lista = [...this.photos];
        moveItemInArray(lista, prevGlobalIdx, currGlobalIdx);
        lista = this._applyOrderAndCover(lista);
        this.photos = lista;
        this.photosChange.emit(lista);
      }
    }
  }

  setAsCover(item: GalleryPhoto | number): void {
    const index = typeof item === 'number' ? item : this.photos.findIndex(p => p.id === item.id);
    if (index < 0 || index >= this.photos.length) return;
    let lista = [...this.photos];
    const [target] = lista.splice(index, 1);
    lista.unshift(target);
    lista = this._applyOrderAndCover(lista);
    this.photos = lista;
    this.photosChange.emit(lista);
  }

  removePhoto(item: GalleryPhoto | number): void {
    const index = typeof item === 'number' ? item : this.photos.findIndex(p => p.id === item.id);
    if (index < 0 || index >= this.photos.length) return;
    const fotoExcluida = this.photos[index];
    let lista = this.photos.filter((_, i) => i !== index);
    lista = this._applyOrderAndCover(lista);
    this.photos = lista;
    // Emite primeiro a exclusao (para o pai marcar no banco se for real)
    if (fotoExcluida) {
      try { this.photoRemoved.emit({ ...fotoExcluida }); } catch { /* no-op */ }
    }
    this.photosChange.emit(lista);
  }

  toggleMediaType(photo: GalleryPhoto, targetType?: 'BOOK' | 'POLAROID'): void {
    const idx = this.photos.findIndex(p => p.id === photo.id);
    if (idx < 0) return;

    const currentCat = (photo.category || (photo as any).mediaType || (photo as any).type || 'BOOK').toUpperCase();
    const novaCategoria: PhotoCategory = targetType 
      ? targetType 
      : (currentCat === 'BOOK' ? 'POLAROID' : 'BOOK');

    if (currentCat === novaCategoria) return;
    
    // Validação de limite máximo de 15 fotos na categoria destino
    if (novaCategoria === 'POLAROID' && this.polaroidPhotosCount >= this.maxPhotos) {
      this.validationError.emit('Limite máximo de 15 fotos para Polaroids Técnicas já foi atingido. Remova uma foto antes de transferir.');
      return;
    }
    if (novaCategoria === 'BOOK' && this.bookPhotosCount >= this.maxPhotos) {
      this.validationError.emit('Limite máximo de 15 fotos para Book Editorial já foi atingido. Remova uma foto antes de transferir.');
      return;
    }

    const applyLocalChange = (isCoverValue?: boolean) => {
      let lista = [...this.photos];
      const willBeCover = isCoverValue !== undefined ? isCoverValue : (novaCategoria === 'POLAROID' ? false : lista[idx].isCover);
      lista[idx] = { 
        ...lista[idx], 
        category: novaCategoria, 
        mediaType: novaCategoria as any, 
        type: novaCategoria as any,
        isCover: willBeCover
      };
      photo.category = novaCategoria;
      (photo as any).mediaType = novaCategoria;
      (photo as any).type = novaCategoria;
      photo.isCover = willBeCover;

      // Se virou Polaroid e era capa, elege a primeira foto do Book
      if (novaCategoria === 'POLAROID' && !lista.some(p => Boolean(p.isCover) && p.category === 'BOOK')) {
        const firstBook = lista.find(p => p.category === 'BOOK');
        if (firstBook) firstBook.isCover = true;
      }

      this.photos = lista;
      this.photosChange.emit(lista);
      this.categoryChanged.emit({ photo: lista[idx], newCategory: novaCategoria });
    };

    // Se a foto tiver ID real do banco (não é temp-) e modelId estiver presente, persiste via API:
    if (this.modelId && photo.id && !String(photo.id).startsWith('temp-') && photo.id !== 'fallback-cover') {
      this.adminModelService.updateMediaType(this.modelId, String(photo.id), novaCategoria).subscribe({
        next: (res) => {
          applyLocalChange(res.isCover);
        },
        error: (err) => {
          console.error('[toggleMediaType] Erro ao alternar tipo de foto no backend:', err);
          this.validationError.emit('Falha ao alternar categoria da foto no servidor.');
        }
      });
    } else {
      applyLocalChange();
    }
  }

  toggleCategory(photo: GalleryPhoto): void {
    this.toggleMediaType(photo);
  }

  updateOrderIndices(): void {
    // Mantido apenas para compatibilidade com código antigo.
    // Use _applyOrderAndCover (imutavel) agora.
    this.photos.forEach((photo, idx) => {
      photo.orderIndex = idx;
      photo.isCover = idx === 0;
    });
    this.photosChange.emit([...this.photos]);
  }

  /**
   * Helper IMUTAVEL. Recebe lista, retorna NOVA lista com:
   * - orderIndex sequencial 0..N
   * - Exatamente UMA foto como isCover (sempre a de posicao 0)
   */
  private _applyOrderAndCover(lista: GalleryPhoto[]): GalleryPhoto[] {
    if (lista.length === 0) return [];
    const nova = lista.map((p, idx) => ({
      ...p,
      orderIndex: idx,
      isCover: idx === 0 ? true : false
    }));
    return nova;
  }

  /**
   * trackBy para *ngFor de fotos: previne que Angular recrie
   * TODO o DOM quando a galeria sofre pequenas alteracoes.
   * (Performance + evita "flicker" / desaparecimento de previews temporarios)
   */
  trackByPhotoId(index: number, item: GalleryPhoto): string {
    return item.id + '-' + (item.orderIndex ?? index);
  }
}
