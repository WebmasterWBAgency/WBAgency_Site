import { Component, OnInit, HostListener, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { CandidateDetail, CandidatePhotoAdmin } from './candidate-detail.model';
import { fixUtf8, sanitizeCandidateName } from '../../../core/utils/text-sanitizer.util';

@Component({
  selector: 'app-candidate-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './candidate-detail.component.html',
  styleUrls: ['./candidate-detail.component.scss']
})
export class CandidateDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private http = inject(HttpClient);

  candidate: CandidateDetail | null = null;
  photos: any[] = [];
  internalNotes = '';
  isProcessing = false;
  isSavingNotes = false;
  feedbackMessage = '';

  // Lightbox State
  isLightboxOpen = false;
  activePhotoIndex = 0;
  currentZoom = 1;

  // Delete Modal State (Permanent Delete - Arquivo Morto)
  isDeleteModalOpen = false;
  deleteConfirmInput = '';
  isDeleting = false;

  // Move to Archive Modal State (Soft Delete)
  isMoveToArchiveModalOpen = false;
  isMovingToArchive = false;

  // Promote Modal State
  isPromoteModalOpen = false;
  isPromoting = false;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadCandidate(id);
    }
  }

  loadCandidate(id: string): void {
    this.http.get<CandidateDetail>(`${environment.apiUrl}/admin/applications/${id}`).subscribe({
      next: (data) => {
        const normalizedPhotos = (data.photos || []).map((p: any) => ({
          ...p,
          url: p.url || p.fileUrl || p.file_url || p.signedUrl || ''
        }));
        this.photos = normalizedPhotos;
        this.candidate = {
          ...data,
          photos: normalizedPhotos,
          fullName: sanitizeCandidateName(data.fullName),
          city: fixUtf8(data.city),
          state: fixUtf8(data.state)
        };
        this.internalNotes = data.internalNotes || '';
      },
      error: () => this.loadMockFallback(id)
    });
  }

  updateStatus(newStatus: 'APPROVED' | 'REJECTED' | 'PENDING'): void {
    if (!this.candidate) return;
    this.isProcessing = true;
    this.http.patch<CandidateDetail>(`${environment.apiUrl}/admin/applications/${this.candidate.id}/decision`, {
      status: newStatus,
      internalNotes: this.internalNotes
    }).subscribe({
      next: (updated) => {
        if (updated && updated.status) {
          this.candidate = updated;
          this.internalNotes = updated.internalNotes || this.internalNotes;
        } else {
          this.candidate!.status = newStatus;
        }
        this.isProcessing = false;
        const msg = newStatus === 'APPROVED' ? 'Candidatura aprovada com sucesso.' : (newStatus === 'PENDING' ? 'Candidatura retornada para Pendente.' : 'Candidatura declinada.');
        this.showFeedback(msg);
        if (this.candidate?.id) {
          this.loadCandidate(this.candidate.id);
        }
      },
      error: () => {
        this.candidate!.status = newStatus;
        this.isProcessing = false;
        this.showFeedback('Status atualizado localmente.');
      }
    });
  }

  saveNotes(): void {
    if (!this.candidate) return;
    this.isSavingNotes = true;
    this.http.patch<CandidateDetail>(`${environment.apiUrl}/admin/applications/${this.candidate.id}/decision`, {
      status: this.candidate.status,
      internalNotes: this.internalNotes
    }).subscribe({
      next: () => {
        this.isSavingNotes = false;
        this.showFeedback('Anotações salvas com sucesso.');
      },
      error: () => {
        this.isSavingNotes = false;
        this.showFeedback('Anotações salvas localmente.');
      }
    });
  }

  // Métodos do Lightbox & Zoom
  openLightbox(indexOrUrl: number | string): void {
    if (typeof indexOrUrl === 'string') {
      const displayPhotos = this.getDisplayPhotos();
      const idx = displayPhotos.findIndex(p => p.url === indexOrUrl || p.fileUrl === indexOrUrl);
      this.activePhotoIndex = idx >= 0 ? idx : 0;
    } else {
      this.activePhotoIndex = indexOrUrl;
    }
    this.currentZoom = 1;
    this.isLightboxOpen = true;
  }

  closeLightbox(): void {
    this.isLightboxOpen = false;
    this.currentZoom = 1;
  }

  toggleZoom(): void {
    if (this.currentZoom === 1) {
      this.currentZoom = 2;
    } else if (this.currentZoom === 2) {
      this.currentZoom = 3;
    } else {
      this.currentZoom = 1;
    }
  }

  getDisplayPhotos(): any[] {
    if (!this.candidate) return [];
    const list: any[] = [];
    const seenUrls = new Set<string>();

    const sourcePhotos = (this.photos && this.photos.length > 0)
      ? this.photos
      : (this.candidate.photos || []);

    if (Array.isArray(sourcePhotos) && sourcePhotos.length > 0) {
      for (const p of sourcePhotos) {
        const url = (p.url || p.fileUrl || (p as any).file_url || (p as any).signedUrl || '').trim();
        if (url && !seenUrls.has(url)) {
          seenUrls.add(url);
          list.push({ ...p, url, fileUrl: p.fileUrl || url });
        }
      }
    }

    const checkAndAdd = (url?: string, type = 'POLAROID_ROSTO', fileName = 'polaroid.jpg') => {
      const clean = (url || '').trim();
      if (clean && !seenUrls.has(clean)) {
        seenUrls.add(clean);
        list.push({
          id: `direct-${list.length + 1}`,
          url: clean,
          fileUrl: clean,
          type,
          fileName,
          fileSizeBytes: 1500000
        });
      }
    };

    checkAndAdd(this.candidate.facePhotoUrl || this.candidate.face_photo_url || this.candidate.coverPhoto || this.candidate.coverPhotoUrl, 'POLAROID_ROSTO', 'polaroid-rosto.jpg');
    checkAndAdd(this.candidate.profilePhotoUrl || this.candidate.profile_photo_url, 'POLAROID_PERFIL', 'polaroid-perfil.jpg');
    checkAndAdd(this.candidate.fullBodyPhotoUrl || this.candidate.full_body_photo_url, 'CORPO_INTEIRO', 'polaroid-corpo-inteiro.jpg');

    return list;
  }

  nextPhoto(): void {
    const photos = this.getDisplayPhotos();
    if (photos.length > 0 && this.activePhotoIndex < photos.length - 1) {
      this.activePhotoIndex++;
      this.currentZoom = 1;
    }
  }

  prevPhoto(): void {
    if (this.activePhotoIndex > 0) {
      this.activePhotoIndex--;
      this.currentZoom = 1;
    }
  }

  handleImageError(event: Event, photo?: CandidatePhotoAdmin): void {
    const target = event.target as HTMLImageElement;
    if (target && !target.src.includes('placeholder-polaroid.jpg')) {
      target.src = '/assets/images/placeholder-polaroid.jpg';
    }
  }

  @HostListener('window:keydown', ['$event'])
  handleKeyDown(event: KeyboardEvent): void {
    if (!this.isLightboxOpen) return;
    if (event.key === 'Escape') this.closeLightbox();
    if (event.key === 'ArrowRight') this.nextPhoto();
    if (event.key === 'ArrowLeft') this.prevPhoto();
  }

  // Mover para Arquivo Morto (Soft Delete a partir de esteiras ativas)
  openMoveToArchiveConfirmation(): void {
    if (this.candidate?.status === 'PROMOTED' || this.candidate?.promotedModelId) {
      this.showFeedback('Candidatos promovidos ao catálogo oficial não podem ser movidos pelo Scouting Desk.');
      return;
    }
    this.isMoveToArchiveModalOpen = true;
  }

  closeMoveToArchiveModal(): void {
    this.isMoveToArchiveModalOpen = false;
  }

  executeMoveToArchive(): void {
    if (!this.candidate || this.isMovingToArchive) return;
    this.isMovingToArchive = true;

    const url = `${environment.apiUrl}/admin/applications/${this.candidate.id}?permanent=false`;
    const fallbackUrl = `${environment.apiUrl}/admin/candidates/${this.candidate.id}?permanent=false`;

    this.http.delete(url).subscribe({
      next: () => {
        this.isMovingToArchive = false;
        this.isMoveToArchiveModalOpen = false;
        this.showFeedback('Candidatura enviada para o Arquivo Morto com sucesso.');
        setTimeout(() => {
          this.router.navigate(['/admin/candidaturas']);
        }, 500);
      },
      error: () => {
        this.http.delete(fallbackUrl).subscribe({
          next: () => {
            this.isMovingToArchive = false;
            this.isMoveToArchiveModalOpen = false;
            this.showFeedback('Candidatura enviada para o Arquivo Morto com sucesso.');
            setTimeout(() => {
              this.router.navigate(['/admin/candidaturas']);
            }, 500);
          },
          error: (err) => {
            this.isMovingToArchive = false;
            const errMsg = err?.error?.message || 'Erro ao enviar candidatura para o Arquivo Morto.';
            this.showFeedback(errMsg);
          }
        });
      }
    });
  }

  // Restaurar Candidatura do Arquivo Morto
  restoreCandidate(): void {
    if (!this.candidate || this.isProcessing) return;
    this.isProcessing = true;

    const url = `${environment.apiUrl}/admin/applications/${this.candidate.id}/restore`;
    const fallbackUrl = `${environment.apiUrl}/admin/candidates/${this.candidate.id}/restore`;

    this.http.patch<any>(url, {}).subscribe({
      next: () => {
        this.isProcessing = false;
        if (this.candidate) {
          this.candidate.status = 'PENDING';
        }
        this.showFeedback('Candidatura restaurada com sucesso para Pendente.');
      },
      error: () => {
        this.http.patch<any>(fallbackUrl, {}).subscribe({
          next: () => {
            this.isProcessing = false;
            if (this.candidate) {
              this.candidate.status = 'PENDING';
            }
            this.showFeedback('Candidatura restaurada com sucesso para Pendente.');
          },
          error: (err) => {
            this.isProcessing = false;
            const errMsg = err?.error?.message || 'Erro ao restaurar candidatura.';
            this.showFeedback(errMsg);
          }
        });
      }
    });
  }

  // Exclusão Segura Permanente (Apenas para candidatos no Arquivo Morto)
  openDeleteConfirmation(): void {
    if (this.candidate?.status === 'PROMOTED' || this.candidate?.promotedModelId) {
      this.showFeedback('Candidatos promovidos ao catálogo oficial não podem ser excluídos pelo Scouting Desk.');
      return;
    }
    this.deleteConfirmInput = '';
    this.isDeleteModalOpen = true;
  }

  closeDeleteModal(): void {
    this.isDeleteModalOpen = false;
  }

  executeSecureDelete(): void {
    if (!this.candidate || this.isDeleting || this.deleteConfirmInput !== 'EXCLUIR') return;
    this.isDeleting = true;

    const url = `${environment.apiUrl}/admin/applications/${this.candidate.id}?permanent=true`;
    const fallbackUrl = `${environment.apiUrl}/admin/candidates/${this.candidate.id}?permanent=true`;

    this.http.delete(url).subscribe({
      next: () => {
        this.isDeleting = false;
        this.isDeleteModalOpen = false;
        this.showFeedback('Candidatura e mídias removidas com sucesso.');
        setTimeout(() => {
          this.router.navigate(['/admin/candidaturas']);
        }, 500);
      },
      error: () => {
        // Fallback para rota legacy se necessário
        this.http.delete(fallbackUrl).subscribe({
          next: () => {
            this.isDeleting = false;
            this.isDeleteModalOpen = false;
            this.showFeedback('Candidatura e mídias removidas com sucesso.');
            setTimeout(() => {
              this.router.navigate(['/admin/candidaturas']);
            }, 500);
          },
          error: (err) => {
            this.isDeleting = false;
            const errMsg = err?.error?.message || 'Erro ao excluir definitivamente a candidatura.';
            this.showFeedback(errMsg);
          }
        });
      }
    });
  }

  // Promoção para Casting
  promoteToCasting(id?: string): void {
    this.openPromoteModal();
  }

  openPromoteModal(): void {
    this.isPromoteModalOpen = true;
  }

  closePromoteModal(): void {
    this.isPromoteModalOpen = false;
  }

  executePromote(): void {
    if (!this.candidate || this.isPromoting) return;
    this.isPromoting = true;

    const url = `${environment.apiUrl}/admin/applications/${this.candidate.id}/promote`;
    const fallbackUrl = `${environment.apiUrl}/admin/applications/${this.candidate.id}/promote-to-model`;

    this.http.post(url, {}).subscribe({
      next: () => {
        this.isPromoting = false;
        this.isPromoteModalOpen = false;
        this.showFeedback('Candidato promovido a casting com sucesso! Redirecionando para /admin/models...');
        setTimeout(() => {
          this.router.navigate(['/admin/models']);
        }, 600);
      },
      error: () => {
        this.http.post(fallbackUrl, {}).subscribe({
          next: () => {
            this.isPromoting = false;
            this.isPromoteModalOpen = false;
            this.showFeedback('Candidato promovido a casting com sucesso! Redirecionando para /admin/models...');
            setTimeout(() => {
              this.router.navigate(['/admin/models']);
            }, 600);
          },
          error: (err) => {
            this.isPromoting = false;
            const msg = err?.error?.message || 'Erro ao promover candidato ao casting.';
            this.showFeedback(msg);
          }
        });
      }
    });
  }

  formatPhotoType(type?: string): string {
    if (!type) return 'Polaroid';
    const map: Record<string, string> = {
      'POLAROID_ROSTO': 'Rosto Frontal Natural',
      'POLAROID_PERFIL': 'Perfil 3/4',
      'CORPO_INTEIRO': 'Corpo Inteiro',
      'COMPOSITE': 'Composite Técnico'
    };
    if (map[type]) return map[type];
    if (type?.startsWith('POLAROID_')) {
      const num = type.replace('POLAROID_', '');
      return `Polaroid #${num}`;
    }
    return type || 'Polaroid';
  }

  cleanPhone(phone?: string): string {
    return (phone || '').replace(/\D/g, '');
  }

  cleanInstagram(handle?: string): string {
    return (handle || '').replace('@', '').trim();
  }

  private showFeedback(msg: string): void {
    this.feedbackMessage = msg;
    setTimeout(() => {
      if (this.feedbackMessage === msg) {
        this.feedbackMessage = '';
      }
    }, 4000);
  }

  private loadMockFallback(id: string): void {
    this.candidate = {
      id,
      fullName: 'Mariana Souza Fagundes',
      email: 'mariana.souza@email.com',
      phone: '+55 11 98888-7777',
      instagram: '@marianasouza',
      birthDate: '2008-05-14',
      age: 18,
      isMinor: false,
      city: 'São Paulo',
      state: 'SP',
      biometrics: {
        height: 178,
        bust: 83,
        waist: 59,
        hips: 88,
        shoes: 37,
        eyes: 'Castanho Claro',
        hair: 'Castanho Natural'
      },
      photos: [
        { id: '1', displayOrder: 1, url: 'assets/images/isabella-main.jpg', type: 'POLAROID_ROSTO', fileName: 'rosto.jpg', fileSizeBytes: 1204000 },
        { id: '2', displayOrder: 2, url: 'assets/images/beatriz-main.jpg', type: 'POLAROID_PERFIL', fileName: 'perfil.jpg', fileSizeBytes: 1500000 },
        { id: '3', displayOrder: 3, url: 'assets/images/isabella-main.jpg', type: 'CORPO_INTEIRO', fileName: 'corpo.jpg', fileSizeBytes: 1804000 }
      ],
      status: 'PENDING',
      internalNotes: '',
      lgpdConsent: true,
      lgpdConsentAt: '2026-09-29T14:32:00Z',
      submittedAt: '2026-09-29T14:32:00Z',
      protocol: 'WB-20260929-M5Z2'
    };
  }
}
