import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Subject, debounceTime, distinctUntilChanged, catchError, of } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { fixUtf8, sanitizeCandidateName } from '../../../core/utils/text-sanitizer.util';
import { extractList } from '../../../core/utils/response-extractor.util';
import { CandidateStatusEnum } from '../../../shared/models/candidate.model';

export interface CandidateApplicationRow {
  id: string;
  fullName: string;
  email: string;
  phone: string;
  birthDate: string;
  age: number;
  isMinor: boolean;
  city: string;
  state: string;
  height: number;
  bust: number;
  waist: number;
  hips: number;
  shoes: number;
  status: CandidateStatusEnum;
  hasPhotos: boolean;
  polaroidsCount: number;
  coverPhoto?: string;
  coverPhotoUrl?: string;
  facePhotoUrl?: string;
  face_photo_url?: string;
  photos?: { id: string; url: string; fileUrl?: string; type: string }[];
  createdAt: string;
  updatedAt?: string;
  archivedAt?: string;
  photoError?: boolean;
  protocol?: string;
}

@Component({
  selector: 'app-candidate-table',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './candidate-table.component.html',
  styleUrls: ['./candidate-table.component.scss']
})
export class CandidateTableComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly searchSubject = new Subject<string>();

  readonly CandidateStatusEnum = CandidateStatusEnum;

  applications: CandidateApplicationRow[] = [];
  isLoading = false;

  // Parâmetros de Filtro e Paginação
  selectedStatus: CandidateStatusEnum | string = CandidateStatusEnum.PENDING;
  selectedMinor = '';
  searchQuery = '';
  pendingCount = 0;
  approvedCount = 0;
  rejectedCount = 0;
  archivedCount = 0;
  totalCount = 0;

  // Ações de Mudança de Status, Promoção e Arquivo Morto
  updatingStatusId: string | null = null;
  isPromotingId: string | null = null;
  restoringCandidateId: string | null = null;

  // Modal 1: Mover para Arquivo Morto (Soft Delete)
  candidateToArchive: CandidateApplicationRow | null = null;
  isMoveToArchiveModalOpen = false;
  isMovingToArchive = false;

  // Modal 2: Exclusão Permanente Definitiva (apenas a partir do Arquivo Morto)
  candidateToPermanentlyDelete: CandidateApplicationRow | null = null;
  isPermanentDeleteModalOpen = false;
  isPermanentlyDeleting = false;
  deleteConfirmInput = '';

  // Getters/setters para retrocompatibilidade
  get isArchiveModalOpen(): boolean {
    return this.isPermanentDeleteModalOpen || this.isMoveToArchiveModalOpen;
  }
  set isArchiveModalOpen(val: boolean) {
    this.isPermanentDeleteModalOpen = val;
  }
  get isArchiving(): boolean {
    return this.isPermanentlyDeleting || this.isMovingToArchive;
  }
  set isArchiving(val: boolean) {
    this.isPermanentlyDeleting = val;
  }

  feedbackMessage = '';

  // Expurgo e Gestão do Arquivo Morto (Manual sob demanda)
  isPurging = false;

  pageIndex = 0;
  pageSize = 15;
  totalElements = 0;
  totalPages = 0;

  sortBy = 'createdAt';
  sortDirection: 'ASC' | 'DESC' = 'DESC';

  // Helper IDENTICO ao CandidateService._resolveCandidatePhotoUrl (stmytwsd projeto CORRETO!)
  // Resolve URLs de coverPhoto / photos[0].url CRUAS / incompletas / projeto errado (zmpqmdi)
  // que vem do backend e aparecem na LISTAGEM de candidaturas (tabela).
  // Se a URL vier invalida: remonta do zero extraindo path relativo. Nunca retorna vazio se houver path.
  private _resolveCandidatePhotoUrl(rawUrlOrFile: any, fallbackRaw: string | null = null): string {
    const SUPABASE_PUBLIC_BASE = 'https://stmytwsdlonpnirqiufq.supabase.co/storage/v1/object/public/candidates-uploads';
    const CORRECT_PROJECT_HOST = 'stmytwsdlonpnirqiufq.supabase.co';
    const CORRECT_BUCKETS_ALLOWED = new Set(['candidates-uploads', 'models-media', 'site-assets']);
    let rawUrl = '';
    let filePath: string = '';
    if (typeof rawUrlOrFile === 'string') {
      rawUrl = rawUrlOrFile.trim();
    } else if (rawUrlOrFile && typeof rawUrlOrFile === 'object') {
      rawUrl = (rawUrlOrFile.url || rawUrlOrFile.fileUrl || fallbackRaw || '').trim();
      filePath = ((rawUrlOrFile.filePath || rawUrlOrFile.storagePath || '') + '').trim().replace(/^\/+/, '');
    }
    if (!rawUrl && fallbackRaw) rawUrl = fallbackRaw.trim();
    if (!rawUrl && !filePath) return '';

    const invalid = (u: string): boolean => {
      if (!u || u.length < 10) return true;
      if (!u.startsWith('http')) return true;
      if (/\/(candidates-uploads|models-media)\/?$/i.test(u)) return true;
      return false;
    };

    let final = '';
    if (!invalid(rawUrl)) final = rawUrl;
    else {
      let relative = '';
      if (filePath) {
        const slashIdx = filePath.indexOf('/');
        relative = (slashIdx >= 0 && CORRECT_BUCKETS_ALLOWED.has(filePath.substring(0, slashIdx)))
          ? filePath.substring(slashIdx + 1)
          : filePath;
      } else if (rawUrl) {
        try {
          const pu = new URL(rawUrl);
          const m = pu.pathname.match(/object\/public\/[^/]+\/(.+)$/);
          if (m && m[1]) relative = m[1];
          else {
            const full = pu.pathname.split('/').filter(Boolean).join('/');
            const candidatesIdx = full.indexOf('submissions/');
            if (candidatesIdx >= 0 && /\.(jpe?g|png|webp|heic)/i.test(full)) relative = full.substring(candidatesIdx);
          }
        } catch {}
      }
      if (relative) {
        const prefix = (relative.startsWith('submissions/') || relative.startsWith('candidates/'))
          ? SUPABASE_PUBLIC_BASE
          : 'https://stmytwsdlonpnirqiufq.supabase.co/storage/v1/object/public/models-media';
        final = `${prefix}/${relative}`;
      } else final = rawUrl;
    }

    if (final) {
      final = final.replace(/(object\/public\/)(candidates-uploads\/){2,}/g, '$1candidates-uploads/')
        .replace(/(object\/public\/candidates-uploads\/)\/?candidates-uploads\//g, '$1')
        .replace(/(?<!:)\/\/+/g, '/').replace('https:/', 'https://');
      let cleaned = final;
      if (cleaned.includes('v=')) {
        cleaned = cleaned
          .replace(/([?&])v=[^&]*(&|$)/g, (m: any, sep: any, end: any) => (end === '&' ? sep : ''))
          .replace(/[?&]$/, '');
      }
      const sep = cleaned.includes('?') ? '&' : '?';
      final = `${cleaned}${sep}v=${Math.floor(Date.now() / 3_600_000)}`;
    }
    return final;
  }

  ngOnInit(): void {
    this.setupSearchDebounce();
    this.loadApplications();
    this.loadCounts();
  }

  setupSearchDebounce(): void {
    this.searchSubject.pipe(
      debounceTime(350),
      distinctUntilChanged()
    ).subscribe((term) => {
      this.searchQuery = term;
      this.pageIndex = 0;
      this.loadApplications();
    });
  }

  onSearchChange(term: string): void {
    this.searchSubject.next(term);
  }

  setStatusFilter(status: CandidateStatusEnum | string): void {
    this.selectedStatus = status;
    this.pageIndex = 0;
    this.loadApplications();
  }

  toggleSort(field: string): void {
    if (this.sortBy === field) {
      this.sortDirection = this.sortDirection === 'ASC' ? 'DESC' : 'ASC';
    } else {
      this.sortBy = field;
      this.sortDirection = 'DESC';
    }
    this.loadApplications();
  }

  loadApplications(): void {
    this.isLoading = true;
    let params = new HttpParams()
      .set('page', String(this.pageIndex))
      .set('size', String(this.pageSize))
      .set('sortBy', this.sortBy)
      .set('sortDirection', this.sortDirection);

    if (this.selectedStatus) {
      params = params.set('status', this.selectedStatus);
    }
    if (this.selectedMinor) params = params.set('isMinor', this.selectedMinor);
    if (this.searchQuery) params = params.set('search', this.searchQuery);

    this.http.get<any>(`${environment.apiUrl}/admin/applications`, { params }).subscribe({
      next: (res) => {
        let rawItems: any[] = [];
        if (Array.isArray(res)) {
          rawItems = res;
          this.totalElements = res.length;
          this.totalPages = 1;
        } else if (res && Array.isArray(res.content)) {
          rawItems = res.content;
          this.totalElements = res.totalElements ?? res.content.length;
          this.totalPages = res.totalPages ?? 1;
        } else if (res && res.data) {
          rawItems = Array.isArray(res.data) ? res.data : (res.data.content || []);
          this.totalElements = res.data.totalElements ?? rawItems.length;
          this.totalPages = res.data.totalPages ?? 1;
        } else {
          rawItems = res?.content ?? (Array.isArray(res) ? res : []);
          this.totalElements = res?.totalElements ?? rawItems.length;
          this.totalPages = res?.totalPages ?? 1;
        }

        // Se na aba Arquivo Morto o endpoint /admin/applications retornar vazio, consulta fallback /admin/candidates
        if (this.selectedStatus === CandidateStatusEnum.ARCHIVED && rawItems.length === 0) {
          let fbParams = new HttpParams()
            .set('status', CandidateStatusEnum.ARCHIVED)
            .set('page', String(this.pageIndex))
            .set('size', String(this.pageSize));
          if (this.searchQuery) fbParams = fbParams.set('search', this.searchQuery);

          this.http.get<any>(`${environment.apiUrl}/admin/candidates`, { params: fbParams }).subscribe({
            next: (fbRes) => {
              const fbItems = fbRes?.content || (Array.isArray(fbRes) ? fbRes : (fbRes?.data?.content || []));
              if (fbItems && fbItems.length > 0) {
                this.totalElements = fbRes?.totalElements ?? fbItems.length;
                this.totalPages = fbRes?.totalPages ?? 1;
                this.renderApplications(fbItems);
                return;
              }
              this.renderApplications([]);
            },
            error: () => this.renderApplications([])
          });
          return;
        }

        this.renderApplications(rawItems);
      },
      error: (err) => {
        if (this.selectedStatus === CandidateStatusEnum.ARCHIVED) {
          let fbParams = new HttpParams()
            .set('status', CandidateStatusEnum.ARCHIVED)
            .set('page', String(this.pageIndex))
            .set('size', String(this.pageSize));
          if (this.searchQuery) fbParams = fbParams.set('search', this.searchQuery);

          this.http.get<any>(`${environment.apiUrl}/admin/candidates`, { params: fbParams }).subscribe({
            next: (fbRes) => {
              const fbItems = fbRes?.content || (Array.isArray(fbRes) ? fbRes : (fbRes?.data?.content || []));
              if (fbItems && fbItems.length > 0) {
                this.totalElements = fbRes?.totalElements ?? fbItems.length;
                this.totalPages = fbRes?.totalPages ?? 1;
                this.renderApplications(fbItems);
                return;
              }
              this.renderApplications([]);
            },
            error: () => this.renderApplications([])
          });
          return;
        }
        console.error('Erro ao carregar candidaturas:', err);
        this.renderApplications([]);
      }
    });
  }

  renderApplications(rawItems: any[]): void {
    const isArchivedTab = this.selectedStatus === CandidateStatusEnum.ARCHIVED;
    // Filtro estrito: apenas status PROMOTED não é exibido no Scouting Desk
    const items = rawItems.filter((item: any) => item.status !== CandidateStatusEnum.PROMOTED);

    this.applications = items.map((item: any) => {
      const directCover = this.getCoverPhoto(item);
      let finalCover = directCover ? (this._resolveCandidatePhotoUrl(directCover) || directCover) : '';

      if (!finalCover) {
        const fallback = item.coverPhoto || item.coverPhotoUrl || item.facePhotoUrl || item.firstPhotoUrl || item.photos?.[0]?.url || item.photos?.[0]?.fileUrl || '';
        if (fallback) finalCover = this._resolveCandidatePhotoUrl(fallback) || fallback;
      }

      const isArchived = isArchivedTab || item.status === CandidateStatusEnum.ARCHIVED || !!item.archivedAt;
      const normalizedStatus: CandidateStatusEnum = isArchived 
        ? CandidateStatusEnum.ARCHIVED 
        : (item.status === CandidateStatusEnum.DECLINED ? CandidateStatusEnum.REJECTED : (item.status as CandidateStatusEnum));

      return {
        ...item,
        status: normalizedStatus,
        fullName: sanitizeCandidateName(item.fullName),
        city: fixUtf8(item.city),
        state: fixUtf8(item.state),
        height: item.height ?? item.heightCm,
        shoes: item.shoes ?? item.shoeSize,
        bust: item.bust ?? item.bustCm,
        waist: item.waist ?? item.waistCm,
        hips: item.hips ?? item.hipsCm,
        polaroidsCount: item.photoCount ?? item.polaroidsCount ?? (item.photos ? item.photos.length : (finalCover ? 1 : 0)),
        coverPhoto: finalCover || item.coverPhoto || item.firstPhotoUrl || undefined,
        coverPhotoUrl: finalCover || item.coverPhotoUrl || item.firstPhotoUrl || undefined,
        facePhotoUrl: item.facePhotoUrl || finalCover || item.firstPhotoUrl || undefined,
        photos: item.photos,
        photoError: false,
        protocol: (item.protocol || '').toString().trim() || undefined,
        archivedAt: item.archivedAt,
        updatedAt: item.updatedAt,
      };
    });

    this.isLoading = false;
    this.cdr.markForCheck();
  }

  getExpurgoDate(candidate: any): Date {
    const raw = candidate?.archivedAt || candidate?.updatedAt || candidate?.createdAt;
    const base = raw ? new Date(raw) : new Date();
    return new Date(base.getTime() + 7 * 24 * 60 * 60 * 1000);
  }

  getCoverPhoto(candidate: any): string | null {
    if (!candidate) return null;

    // 1. Campo direto da submissão (facePhotoUrl / face_photo_url / coverPhotoUrl / coverPhoto / firstPhotoUrl)
    if (candidate.facePhotoUrl) return candidate.facePhotoUrl;
    if (candidate.face_photo_url) return candidate.face_photo_url;
    if (candidate.coverPhotoUrl) return candidate.coverPhotoUrl;
    if (candidate.coverPhoto) return candidate.coverPhoto;
    if (candidate.firstPhotoUrl) return candidate.firstPhotoUrl;

    // 2. Se as fotos vierem como array (candidate.photos)
    if (Array.isArray(candidate.photos) && candidate.photos.length > 0) {
      // Procura especificamente a foto do rosto
      const facePhoto = candidate.photos.find((p: any) => 
        p.type === 'POLAROID_ROSTO' || 
        p.photo_position === 1 || 
        p.photoPosition === 1
      );
      if (facePhoto) return facePhoto.url || facePhoto.file_url || facePhoto.fileUrl || null;

      // Fallback para a primeira foto disponível da lista
      const first = candidate.photos[0];
      return first.url || first.file_url || first.fileUrl || null;
    }

    return null;
  }

  onPhotoError(candidate: CandidateApplicationRow): void {
    candidate.photoError = true;
  }

  loadCounts(): void {
    this.http.get<any>(`${environment.apiUrl}/admin/applications/counts`).subscribe({
      next: (counts) => {
        if (counts) {
          if (counts.pending !== undefined) this.pendingCount = counts.pending;
          if (counts.approved !== undefined) this.approvedCount = counts.approved;
          if (counts.rejected !== undefined) this.rejectedCount = counts.rejected;
          if (counts.archived !== undefined) this.archivedCount = counts.archived;
          if (counts.total !== undefined) this.totalCount = counts.total;
        }
      },
      error: () => {}
    });
  }

  openCandidateDetail(candidateId: string): void {
    if (!candidateId) return;
    this.router.navigate(['/admin/candidaturas', candidateId]);
  }

  updateStatus(candidate: CandidateApplicationRow, newStatus: CandidateStatusEnum, event?: Event): void {
    if (event) event.stopPropagation();
    if (!candidate || this.updatingStatusId) return;

    this.updatingStatusId = candidate.id;
    const previousStatus = candidate.status;
    candidate.status = newStatus;

    const payload = { status: newStatus };
    const url = `${environment.apiUrl}/admin/applications/${candidate.id}/decision`;
    const fallbackUrl = `${environment.apiUrl}/admin/candidates/${candidate.id}/status`;

    this.http.patch<any>(url, payload).pipe(
      catchError(() => this.http.patch<any>(fallbackUrl, payload))
    ).subscribe({
      next: () => {
        this.updatingStatusId = null;
        const labelMap: Partial<Record<CandidateStatusEnum, string>> = {
          [CandidateStatusEnum.APPROVED]: 'Aprovada',
          [CandidateStatusEnum.REJECTED]: 'Declinada',
          [CandidateStatusEnum.PENDING]: 'Pendente'
        };
        this.showFeedback(`Status de "${candidate.fullName}" atualizado para ${labelMap[newStatus] || newStatus} com sucesso no banco de dados.`);
        this.loadApplications();
        this.loadCounts();
      },
      error: () => {
        candidate.status = previousStatus;
        this.updatingStatusId = null;
        this.showFeedback('Erro ao atualizar status no banco de dados. Tente novamente.');
      }
    });
  }

  updateCandidateStatus(candidate: CandidateApplicationRow, newStatus: CandidateStatusEnum, event?: Event): void {
    this.updateStatus(candidate, newStatus, event);
  }

  confirmMoveToArchive(candidate: CandidateApplicationRow, event?: Event): void {
    if (event) event.stopPropagation();
    this.candidateToArchive = candidate;
    this.isMoveToArchiveModalOpen = true;
  }

  closeMoveToArchiveModal(): void {
    this.candidateToArchive = null;
    this.isMoveToArchiveModalOpen = false;
  }

  executeMoveToArchive(): void {
    if (!this.candidateToArchive || this.isMovingToArchive) return;
    const candidate = this.candidateToArchive;
    this.isMovingToArchive = true;

    const url = `${environment.apiUrl}/admin/applications/${candidate.id}?permanent=false`;
    const fallbackUrl = `${environment.apiUrl}/admin/candidates/${candidate.id}?permanent=false`;

    this.http.delete(url).pipe(
      catchError(() => this.http.delete(fallbackUrl))
    ).subscribe({
      next: () => {
        this.isMovingToArchive = false;
        this.isMoveToArchiveModalOpen = false;
        this.applications = this.applications.filter(a => a.id !== candidate.id);
        this.totalElements = Math.max(0, this.totalElements - 1);
        this.loadCounts();
        this.showFeedback(`Candidatura de "${candidate.fullName}" enviada para o Arquivo Morto com sucesso.`);
        this.candidateToArchive = null;
      },
      error: (err) => {
        this.isMovingToArchive = false;
        this.isMoveToArchiveModalOpen = false;
        const msg = err?.error?.message || 'Erro ao enviar candidatura para o Arquivo Morto.';
        this.showFeedback(msg);
        this.candidateToArchive = null;
      }
    });
  }

  restoreCandidate(candidate: CandidateApplicationRow, event?: Event): void {
    if (event) event.stopPropagation();
    if (!candidate || this.restoringCandidateId) return;

    this.restoringCandidateId = candidate.id;
    const url = `${environment.apiUrl}/admin/applications/${candidate.id}/restore`;
    const fallbackUrl = `${environment.apiUrl}/admin/candidates/${candidate.id}/restore`;

    this.http.patch<any>(url, {}).pipe(
      catchError(() => this.http.patch<any>(fallbackUrl, {}))
    ).subscribe({
      next: () => {
        this.restoringCandidateId = null;
        this.applications = this.applications.filter(a => a.id !== candidate.id);
        this.totalElements = Math.max(0, this.totalElements - 1);
        this.loadCounts();
        this.showFeedback(`Candidatura de "${candidate.fullName}" restaurada com sucesso para a esteira de Pendentes.`);
      },
      error: (err) => {
        this.restoringCandidateId = null;
        const msg = err?.error?.message || 'Erro ao restaurar candidatura. Tente novamente.';
        this.showFeedback(msg);
      }
    });
  }

  confirmPermanentDelete(candidate: CandidateApplicationRow, event?: Event): void {
    if (event) event.stopPropagation();
    this.candidateToPermanentlyDelete = candidate;
    this.candidateToArchive = candidate;
    this.deleteConfirmInput = '';
    this.isPermanentDeleteModalOpen = true;
  }

  closePermanentDeleteModal(): void {
    this.candidateToPermanentlyDelete = null;
    this.candidateToArchive = null;
    this.deleteConfirmInput = '';
    this.isPermanentDeleteModalOpen = false;
  }

  executePermanentDelete(): void {
    const candidate = this.candidateToPermanentlyDelete || this.candidateToArchive;
    if (!candidate || this.isPermanentlyDeleting || this.deleteConfirmInput !== 'EXCLUIR') return;
    this.isPermanentlyDeleting = true;

    const url = `${environment.apiUrl}/admin/applications/${candidate.id}?permanent=true`;
    const fallbackUrl = `${environment.apiUrl}/admin/candidates/${candidate.id}?permanent=true`;

    this.http.delete(url).pipe(
      catchError(() => this.http.delete(fallbackUrl))
    ).subscribe({
      next: () => {
        this.isPermanentlyDeleting = false;
        this.isPermanentDeleteModalOpen = false;
        this.applications = this.applications.filter(a => a.id !== candidate.id);
        this.totalElements = Math.max(0, this.totalElements - 1);
        this.loadCounts();
        this.showFeedback(`Candidato(a) "${candidate.fullName}" e todas as suas mídias foram permanentemente excluídos.`);
        this.candidateToPermanentlyDelete = null;
        this.candidateToArchive = null;
        this.deleteConfirmInput = '';
      },
      error: (err) => {
        this.isPermanentlyDeleting = false;
        this.isPermanentDeleteModalOpen = false;
        const msg = err?.error?.message || 'Erro ao excluir definitivamente a candidatura.';
        this.showFeedback(msg);
        this.candidateToPermanentlyDelete = null;
        this.candidateToArchive = null;
        this.deleteConfirmInput = '';
      }
    });
  }

  // Métodos retrocompatíveis
  confirmDelete(candidate: CandidateApplicationRow, event?: Event): void {
    if (this.selectedStatus === CandidateStatusEnum.ARCHIVED) {
      this.confirmPermanentDelete(candidate, event);
    } else {
      this.confirmMoveToArchive(candidate, event);
    }
  }

  openArchiveModal(candidate: CandidateApplicationRow, event?: Event): void {
    this.confirmDelete(candidate, event);
  }

  closeArchiveModal(): void {
    this.closeMoveToArchiveModal();
    this.closePermanentDeleteModal();
  }

  executeArchive(): void {
    if (this.isPermanentDeleteModalOpen) {
      this.executePermanentDelete();
    } else {
      this.executeMoveToArchive();
    }
  }

  confirmPurgeArchive(): void {
    const confirmed = window.confirm(
      'Atenção: Deseja executar o expurgo manual do Arquivo Morto agora? Todos os registros e fotografias de candidaturas arquivadas serão permanentemente excluídos do banco de dados e do armazenamento.'
    );

    if (!confirmed) return;

    this.isPurging = true;
    this.http.post<any>(`${environment.apiUrl}/admin/candidates/archive/cleanup?all=true`, {}).pipe(
      catchError(() => this.http.post<any>(`${environment.apiUrl}/admin/applications/archive/cleanup?all=true`, {}))
    ).subscribe({
      next: (res) => {
        this.isPurging = false;
        alert(res?.message || 'Arquivo morto expurgado com sucesso.');
        this.loadApplications(); // Recarrega a listagem
        this.loadCounts();
      },
      error: (err) => {
        this.isPurging = false;
        console.error('Erro ao expurgar arquivo morto:', err);
        alert('Falha ao executar a limpeza do arquivo morto.');
      }
    });
  }

  showFeedback(msg: string): void {
    this.feedbackMessage = msg;
    setTimeout(() => {
      if (this.feedbackMessage === msg) {
        this.feedbackMessage = '';
      }
    }, 4500);
  }

  goToPage(page: number): void {
    this.pageIndex = page;
    this.loadApplications();
  }

  getUpperBound(): number {
    return Math.min((this.pageIndex + 1) * this.pageSize, this.totalElements);
  }

  openDetail(id: string): void {
    this.router.navigate(['/admin/candidaturas', id]);
  }

}
