import { Component, OnInit, inject, signal, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AdminModelService } from '../../../core/services/admin-model.service';
import {
  AdminModelFilterParams,
  AdminModelPageResponse,
  ModelAdminItem,
  ModelGender
} from '../../../shared/models/admin-model.interface';
import { ModelFormComponent } from './model-form/model-form.component';

export type ModelTab = 'ALL' | 'MAIN' | 'STARS' | 'INACTIVE';

@Component({
  selector: 'app-models-mgmt',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, ModelFormComponent],
  templateUrl: './models-mgmt.component.html',
  styleUrls: ['./models-mgmt.component.scss']
})
export class ModelsMgmtComponent implements OnInit {
  private readonly adminModelService = inject(AdminModelService);
  private readonly cdr = inject(ChangeDetectorRef);

  // Cache mestre em memória para garantir navegação instantânea e fallback sem tela branca
  private masterModels: ModelAdminItem[] = [];

  readonly models = signal<ModelAdminItem[]>([]);
  readonly isLoading = signal<boolean>(false);
  readonly isFormOpen = signal<boolean>(false);
  readonly editingModelId = signal<string | null>(null);

  // Paginação e totais (debug do envelope Spring Page)
  readonly totalElements = signal<number>(0);
  readonly totalPages = signal<number>(0);
  readonly pageSize = signal<number>(20);
  readonly page = signal<number>(0);

  readonly isConfirmDeleteOpen = signal<boolean>(false);
  readonly pendingDeleteModel = signal<ModelAdminItem | null>(null);
  readonly deleteMode = signal<'ARCHIVE' | 'PERMANENT'>('ARCHIVE');
  readonly isDeleting = signal<boolean>(false);
  readonly isRestoring = signal<boolean>(false);

  readonly filterGender = signal<ModelGender | 'ALL'>('ALL');
  readonly filterStar = signal<boolean | null>(null);
  readonly filterStatus = signal<boolean | null>(null);
  readonly searchTerm = signal<string>('');
  readonly activeTab = signal<ModelTab>('ALL');

  readonly viewMode = signal<'CARDS' | 'TABLE'>('CARDS');
  readonly toast = signal<{ message: string; type: 'success' | 'error' } | null>(null);
  readonly brokenPhotoIds = signal<Set<string>>(new Set());
  readonly updatingStatusId = signal<string | null>(null);

  setViewMode(mode: 'CARDS' | 'TABLE'): void {
    this.viewMode.set(mode);
  }

  setTab(tab: ModelTab): void {
    this.activeTab.set(tab);
    this.page.set(0);
    this.loadModels();
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    this.page.set(0);
    this.loadModels();
  }

  onThumbError(eventOrModelId?: any, modelId?: string): void {
    if (eventOrModelId && typeof eventOrModelId === 'object' && eventOrModelId.target) {
      const img = eventOrModelId.target as HTMLImageElement;
      if (img && !img.src.includes('placeholder-polaroid.jpg')) {
        img.src = 'assets/images/placeholder-polaroid.jpg';
      }
    }
    const id = typeof eventOrModelId === 'string' ? eventOrModelId : modelId;
    if (id) {
      this.brokenPhotoIds.update(s => new Set(s).add(id));
    }
  }

  hasBrokenThumb(modelId: string): boolean {
    return !modelId || this.brokenPhotoIds().has(modelId);
  }

  ngOnInit(): void {
    this.loadModels();
  }

  loadModels(): void {
    this.isLoading.set(true);
    const currentTab = this.activeTab();

    // Monta os parâmetros estritamente sem enviar strings "null" ou vazias
    const params: any = {
      page: this.page(),
      size: this.pageSize()
    };

    const cleanSearch = (this.searchTerm() && typeof this.searchTerm() === 'string' && this.searchTerm().trim().length > 0 && this.searchTerm().trim() !== 'null' && this.searchTerm().trim() !== 'undefined')
      ? this.searchTerm().trim()
      : null;

    if (cleanSearch) {
      params.search = cleanSearch;
    }

    if (this.filterGender() && this.filterGender() !== 'ALL') {
      params.gender = this.filterGender();
    }

    // Aplicação dos filtros por aba
    switch (currentTab) {
      case 'MAIN':
        params.isActive = true;
        params.isStar = false;
        break;
      case 'STARS':
        params.isStar = true;
        break;
      case 'INACTIVE':
        params.isActive = false;
        break;
      case 'ALL':
      default:
        // Na aba TODOS, não envia filtros booleanos restritivos
        break;
    }

    console.log('%c[models-mgmt] loadModels() iniciado com params:', 'color:#4B584E;font-weight:bold', params);

    this.adminModelService.getModels(params).subscribe({
      next: (res: any) => {
        let items: any[] = [];
        let totalEl = 0;

        // Extração tolerante de envelope Spring Data Pageable ou Array puro
        if (Array.isArray(res)) {
          items = res;
          totalEl = res.length;
        } else if (res && Array.isArray(res.content)) {
          items = res.content;
          totalEl = res.totalElements ?? res.content.length;
        } else if (res && res.data && Array.isArray(res.data.content)) {
          items = res.data.content;
          totalEl = res.data.totalElements ?? items.length;
        } else if (res && Array.isArray(res.data)) {
          items = res.data;
          totalEl = res.total ?? items.length;
        }

        const totalPg = (res as any)?.totalPages ?? Math.max(1, Math.ceil(totalEl / (this.pageSize() || 20)));

        // Normalização dos campos para os Cards e Tabela
        let mappedList: ModelAdminItem[] = items.map((m: any) => {
          const cover = m.coverPhotoUrl 
            || m.primaryPhotoUrl 
            || m.coverImageUrl 
            || m.media?.[0]?.fileUrl 
            || 'assets/images/placeholder-polaroid.jpg';

          return {
            ...m,
            coverUrl: cover,
            coverPhotoUrl: cover,
            primaryPhotoUrl: cover,
            isActive: m.isActive ?? m.active ?? true,
            isStar: m.isStar ?? false
          };
        });

        // Se a busca na aba TODOS retornar modelos, guarda no cache mestre
        if (currentTab === 'ALL' && !cleanSearch && (!this.filterGender() || this.filterGender() === 'ALL') && mappedList.length > 0) {
          this.masterModels = mappedList;
        }

        // Fallback resiliente: se a API retornar lista vazia mas temos modelos no cache mestre,
        // aplica a filtragem em memória para garantir consistência
        if (mappedList.length === 0 && this.masterModels.length > 0) {
          const fallbackFiltered = this.filterLocally(this.masterModels, currentTab, cleanSearch, this.filterGender());
          if (fallbackFiltered.length > 0) {
            console.log('[models-mgmt] ⚡ Aplicando fallback do cache mestre:', fallbackFiltered.length, 'itens');
            mappedList = fallbackFiltered;
            totalEl = fallbackFiltered.length;
          }
        }

        console.log('[models-mgmt] ✅ FINAL -> list.length=', mappedList.length, ' totalElements=', totalEl, 'totalPages=', totalPg);

        this.models.set(mappedList);
        this.totalElements.set(totalEl);
        this.totalPages.set(totalPg);
        this.isLoading.set(false);

        // Força detecção de mudanças
        this.cdr.markForCheck();
        try { this.cdr.detectChanges(); } catch { /* standalone */ }
      },
      error: (err) => {
        console.error('[ERRO /admin/models]', err);
        // Tenta usar o cache mestre em caso de erro de rede
        if (this.masterModels.length > 0) {
          const fallbackFiltered = this.filterLocally(this.masterModels, currentTab, cleanSearch, this.filterGender());
          if (fallbackFiltered.length > 0) {
            console.log('[models-mgmt] ⚡ Recuperado do cache mestre após erro de requisição:', fallbackFiltered.length, 'itens');
            this.models.set(fallbackFiltered);
            this.totalElements.set(fallbackFiltered.length);
            this.totalPages.set(1);
            this.isLoading.set(false);
            this.cdr.markForCheck();
            return;
          }
        }

        this.models.set([]);
        this.totalElements.set(0);
        this.totalPages.set(0);
        this.isLoading.set(false);
        this.cdr.markForCheck();
        const detail = (err as any)?.error?.detail || (err as any)?.message || '';
        this.showToast(`Erro ao carregar catálogo de modelos. ${detail ? 'Detalhe: ' + detail : ''}`, 'error');
      }
    });
  }

  /**
   * Filtragem em memória local garantindo tolerância a falhas de rede ou latência.
   */
  private filterLocally(
    list: ModelAdminItem[],
    tab: ModelTab,
    search: string | null,
    gender: ModelGender | 'ALL'
  ): ModelAdminItem[] {
    return list.filter((m) => {
      // 1. Filtro da Aba
      if (tab === 'MAIN') {
        const isNotStar = !m.isStar && m.status !== 'CAST_STAR';
        const isActive = m.isActive !== false && !m.archivedAt && m.status !== 'ARCHIVED';
        if (!isNotStar || !isActive) return false;
      } else if (tab === 'STARS') {
        const isStar = m.isStar === true || m.status === 'CAST_STAR';
        const notArchived = !m.archivedAt && m.status !== 'ARCHIVED';
        if (!isStar || !notArchived) return false;
      } else if (tab === 'INACTIVE') {
        const isInactive = m.isActive === false || !!m.archivedAt || m.status === 'ARCHIVED' || m.status === 'APPLICANT_DECLINED';
        if (!isInactive) return false;
      }

      // 2. Filtro de Gênero
      if (gender && gender !== 'ALL' && m.gender !== gender) {
        return false;
      }

      // 3. Filtro de Busca
      if (search) {
        const term = search.toLowerCase();
        const stageMatch = m.stageName ? m.stageName.toLowerCase().includes(term) : false;
        const fullMatch = m.fullName ? m.fullName.toLowerCase().includes(term) : false;
        const cityMatch = m.city ? m.city.toLowerCase().includes(term) : false;
        if (!stageMatch && !fullMatch && !cityMatch) return false;
      }

      return true;
    });
  }

  openCreateForm(): void {
    this.editingModelId.set(null);
    this.isFormOpen.set(true);
  }

  openEditForm(model: ModelAdminItem): void {
    this.editingModelId.set(model.id);
    this.isFormOpen.set(true);
  }

  closeForm(): void {
    this.isFormOpen.set(false);
    this.editingModelId.set(null);
  }

  onModelSaved(savedModel: ModelAdminItem): void {
    this.masterModels = [];
    this.closeForm();
    this.loadModels();
    this.showToast(`Modelo "${savedModel.stageName}" salvo com sucesso!`, 'success');
  }

  toggleStatus(model: ModelAdminItem, event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    if (this.updatingStatusId() === model.id) return;

    const nextStatus = !model.isActive;
    this.updatingStatusId.set(model.id);

    this.adminModelService.updateStatus(model.id, nextStatus).subscribe({
      next: (updated) => {
        this.updatingStatusId.set(null);
        this.masterModels = [];
        this.models.update((list) =>
          list.map((m) => (m.id === model.id ? { ...m, isActive: updated.isActive } : m))
        );
        const name = model.stageName || model.fullName || 'Modelo';
        this.showToast(
          `Status de "${name}" alterado para ${nextStatus ? 'Ativo' : 'Inativo'}.${!nextStatus ? ' A opção Excluir agora está disponível.' : ''}`,
          'success'
        );
        this.cdr.markForCheck();
        try { this.cdr.detectChanges(); } catch { /* standalone */ }
      },
      error: (err) => {
        this.updatingStatusId.set(null);
        console.error('[models-mgmt] Erro ao alternar status do modelo:', err);
        this.showToast('Erro ao atualizar status do modelo.', 'error');
        this.cdr.markForCheck();
      }
    });
  }

  toggleStar(model: ModelAdminItem): void {
    const nextStar = !model.isStar;
    this.adminModelService.updateStar(model.id, nextStar).subscribe({
      next: (updated) => {
        this.masterModels = [];
        this.models.update((list) =>
          list.map((m) => (m.id === model.id ? { ...m, isStar: updated.isStar } : m))
        );
        this.showToast(
          `Modelo "${model.stageName}" ${nextStar ? 'adicionado às Stars ★' : 'removido das Stars'}.`,
          'success'
        );
      },
      error: () => this.showToast('Erro ao atualizar classificação Star.', 'error')
    });
  }

  setGenderFilter(gender: ModelGender | 'ALL'): void {
    this.filterGender.set(gender);
    this.loadModels();
  }

  setStarFilter(val: boolean | null): void {
    this.filterStar.set(val);
    this.page.set(0);
    this.loadModels();
  }

  setStatusFilter(val: boolean | null): void {
    this.filterStatus.set(val);
    this.page.set(0);
    this.loadModels();
  }

  showToast(message: string, type: 'success' | 'error'): void {
    this.toast.set({ message, type });
    setTimeout(() => {
      this.toast.set(null);
    }, 4000);
  }

  // ============================================================
  // ⚡ TRANSIÇÃO DE ESTADOS DO CICLO DE VIDA DO TALENTO
  // ============================================================

  approveApplicant(model: ModelAdminItem): void {
    this.adminModelService.updateTalentStatus(model.id, 'CAST_PUBLIC').subscribe({
      next: () => {
        this.showToast(`Talento "${model.fullName || model.stageName}" aprovado e adicionado ao Elenco!`, 'success');
        this.loadModels();
      },
      error: (err) => this.showToast(err?.error?.message || 'Erro ao aprovar aplicante.', 'error')
    });
  }

  declineApplicant(model: ModelAdminItem): void {
    this.adminModelService.updateTalentStatus(model.id, 'APPLICANT_DECLINED').subscribe({
      next: () => {
        this.showToast(`Candidatura de "${model.fullName || model.stageName}" arquivada como recusada.`, 'success');
        this.loadModels();
      },
      error: (err) => this.showToast(err?.error?.message || 'Erro ao recusar aplicante.', 'error')
    });
  }

  archiveModel(model: ModelAdminItem): void {
    this.adminModelService.updateTalentStatus(model.id, 'ARCHIVED').subscribe({
      next: () => {
        this.showToast(`Modelo "${model.stageName}" movido para Arquivados.`, 'success');
        this.loadModels();
      },
      error: (err) => this.showToast(err?.error?.message || 'Erro ao arquivar modelo.', 'error')
    });
  }

  reactivateModel(model: ModelAdminItem): void {
    this.adminModelService.updateTalentStatus(model.id, 'CAST_PUBLIC').subscribe({
      next: () => {
        this.showToast(`Modelo "${model.stageName}" reativado no Elenco com sucesso!`, 'success');
        this.loadModels();
      },
      error: (err) => this.showToast(err?.error?.message || 'Erro ao reativar modelo.', 'error')
    });
  }

  // ============================================================
  // 🗑️ GESTÃO DE ARQUIVAMENTO E EXCLUSÃO DEFINITIVA
  // ============================================================

  /**
   * Abre modal para mover para o Arquivo Morto (Soft Delete).
   */
  confirmArchive(model: ModelAdminItem): void {
    if (!model || !model.id) return;
    this.pendingDeleteModel.set(model);
    this.deleteMode.set('ARCHIVE');
    this.isConfirmDeleteOpen.set(true);
  }

  /**
   * Abre modal para exclusão permanente e purga de arquivos (Hard Delete).
   */
  confirmPermanentDelete(model: ModelAdminItem): void {
    if (!model || !model.id) return;
    this.pendingDeleteModel.set(model);
    this.deleteMode.set('PERMANENT');
    this.isConfirmDeleteOpen.set(true);
  }

  /**
   * Alias de confirmação com base na aba ativa.
   */
  confirmDelete(model: ModelAdminItem): void {
    if (this.activeTab() === 'INACTIVE') {
      this.confirmPermanentDelete(model);
    } else {
      this.confirmArchive(model);
    }
  }

  /**
   * Fecha modal de confirmação.
   */
  cancelDelete(): void {
    this.isConfirmDeleteOpen.set(false);
    this.pendingDeleteModel.set(null);
    this.isDeleting.set(false);
  }

  /**
   * Confirma a ação:
   * - ARCHIVE: Soft delete (archived_at = now, is_active = false)
   * - PERMANENT: Hard delete imediato (banco + Supabase Storage)
   */
  executeDelete(): void {
    const target = this.pendingDeleteModel();
    if (!target || !target.id || this.isDeleting()) return;

    const isPermanent = this.deleteMode() === 'PERMANENT';
    this.isDeleting.set(true);

    this.adminModelService.deleteModel(target.id, isPermanent).subscribe({
      next: () => {
        this.masterModels = [];
        this.models.update((list) => list.filter((m) => m.id !== target.id));
        this.loadModels();
        if (isPermanent) {
          this.showToast(`Modelo "${target.stageName}" e suas fotos foram excluídos definitivamente.`, 'success');
        } else {
          this.showToast(`Modelo "${target.stageName}" foi movido para o Arquivo Morto.`, 'success');
        }
        this.cancelDelete();
      },
      error: (err) => {
        console.error('[Excluir/Arquivar Modelo] Falha backend:', err);
        const detail = (err as any)?.error?.detail || (err as any)?.message || '';
        this.showToast(
          `Erro ao ${isPermanent ? 'excluir definitivamente' : 'arquivar'} modelo "${target.stageName}". ${detail ? ` Detalhe: ${detail}` : 'Tente novamente em instantes.'}`,
          'error'
        );
        this.isDeleting.set(false);
      }
    });
  }

  /**
   * Restaura o modelo do Arquivo Morto para o Elenco Principal (archived_at = null, is_active = false).
   */
  restoreModel(model: ModelAdminItem): void {
    if (!model || !model.id || this.isRestoring()) return;
    this.isRestoring.set(true);

    this.adminModelService.restoreModel(model.id).subscribe({
      next: () => {
        this.isRestoring.set(false);
        this.masterModels = [];
        this.models.update((list) => list.filter((m) => m.id !== model.id));
        this.loadModels();
        this.showToast(`Modelo "${model.stageName}" restaurado com sucesso! Revise seus dados no Elenco Principal para reativá-lo.`, 'success');
      },
      error: (err) => {
        this.isRestoring.set(false);
        console.error('[Restaurar Modelo] Falha backend:', err);
        const detail = (err as any)?.error?.detail || (err as any)?.message || '';
        this.showToast(`Erro ao restaurar modelo "${model.stageName}". ${detail ? `Detalhe: ${detail}` : ''}`, 'error');
      }
    });
  }
}
