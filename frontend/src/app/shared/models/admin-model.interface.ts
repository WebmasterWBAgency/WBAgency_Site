export type ModelGender = 'FEMALE' | 'MALE';

export interface ModelAdminItem {
  id: string;
  stageName: string;
  fullName?: string | null;
  protocol?: string | null;
  email?: string | null;
  phone?: string | null;
  status?: string | null;
  gender: ModelGender;
  isStar: boolean;
  isFeaturedHome: boolean;
  featuredOrder?: number | null;
  isActive: boolean;
  primaryPhotoUrl?: string | null;
  coverPhotoUrl?: string | null;
  coverImageUrl?: string | null;
  media?: any[] | null;
  /**
   * Caminho do arquivo no Storage Supabase.
   * USO EXCLUSIVO COMO FALLBACK caso primaryPhotoUrl venha incompleta
   * (ex: /public/models-media/ sem path do objeto → causa HTTP 400).
   * Nao utilizar diretamente para exibir na UI: sempre use primaryPhotoUrl.
   */
  filePath?: string | null;
  /** @deprecated Use filePath. Mantido para compatibilidade DTOs legados. */
  storagePath?: string | null;
  instagramUrl?: string | null;
  birthDate?: string | null;
  age?: number | null;
  heightCm?: number | null;
  weightKg?: number | null;
  city?: string | null;
  state?: string | null;
  nationality?: string | null;
  dressSize?: string | null;
  shoeSize?: string | null;
  bustChestCm?: number | null;
  waistCm?: number | null;
  hipsCm?: number | null;
  hairColor?: string | null;
  eyesColor?: string | null;
  legalGuardianName?: string | null;
  legalGuardianContact?: string | null;
  lgpdAccepted?: boolean | null;
  internalNotes?: string | null;
  archivedAt?: string | null;
  createdAt?: string;
  updatedAt?: string;
  // Campos auxiliares para visualização
  photosCount?: number;
  compositeReady?: boolean;
}

export interface ModelFormData {
  stageName: string;
  gender: ModelGender;
  isStar: boolean;
  isFeaturedHome: boolean;
  featuredOrder?: number | null;
  isActive: boolean;
  primaryPhotoUrl?: string | null;
  instagramUrl?: string | null;
  birthDate?: string | null;
  heightCm?: number | null;
  city?: string | null;
  nationality?: string | null;
  dressSize?: string | null;
  shoeSize?: string | null;
  bustChestCm?: number | null;
  waistCm?: number | null;
  hipsCm?: number | null;
  hairColor?: string | null;
  eyesColor?: string | null;
}

export interface AdminModelFilterParams {
  gender?: ModelGender | 'ALL';
  isStar?: boolean | null;
  isActive?: boolean | null;
  archived?: boolean | null;
  status?: string;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface AdminModelPageResponse {
  content: ModelAdminItem[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first?: boolean;
  last?: boolean;
}
