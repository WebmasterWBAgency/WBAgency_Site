export type CandidateStatus =
  | 'PENDING'
  | 'REVIEWING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CONTACTED'
  | 'ARCHIVED'
  | 'APPLICANT_PENDING'
  | 'CAST_PUBLIC';

export type SubmissionStatus = CandidateStatus;

export type CandidateGender = 'FEMALE' | 'MALE' | 'NON_BINARY' | 'OTHER';
export type SubmissionGender = CandidateGender;

export interface CandidatePhoto {
  id: string;
  url: string;
  fileUrl?: string;
  storagePath?: string;
  candidateId?: string;
  displayOrder?: number;
  type: 'POLAROID_ROSTO' | 'POLAROID_PERFIL' | 'CORPO_INTEIRO' | 'COMPOSITE';
}

export interface Candidate {
  id: string;
  fullName: string;
  email: string;
  phone: string;
  birthDate: string;
  isMinor: boolean;
  guardianName?: string;
  guardianPhone?: string;
  guardianEmail?: string;
  city: string;
  state: string;
  height: number;
  heightCm?: number;
  bust: number;
  waist: number;
  hips: number;
  shoes: number;
  shoeSize?: number | string;
  eyeColor: string;
  hairColor: string;
  status: CandidateStatus;
  photos: CandidatePhoto[];
  notes?: string;
  createdAt: string;
  instagramHandle?: string;
  age?: number;
  gender?: string;
  protocol?: string;
  coverPhoto?: string;
  coverPhotoUrl?: string;
  facePhotoUrl?: string;
  face_photo_url?: string;
  profilePhotoUrl?: string;
  fullBodyPhotoUrl?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  convertedToModelId?: string;
}

export interface CandidateSubmissionResponse {
  id: string;
  protocol: string;
  message?: string;
  status: CandidateStatus;
  convertedToModelId?: string | null;
  createdAt: string;
  updatedAt?: string;

  // Dados Cadastrais & Contato
  fullName?: string;
  email?: string;
  phone?: string;
  birthDate?: string;
  age?: number;
  gender?: string;
  city?: string;
  state?: string;

  // Medidas e Características Físicas
  height?: number;
  heightCm?: number;
  bust?: number;
  bustCm?: number;
  waist?: number;
  waistCm?: number;
  hips?: number;
  hipsCm?: number;
  shoes?: number;
  shoeSize?: number | string;
  eyeColor?: string;
  hairColor?: string;
  instagramHandle?: string;

  // Responsável Legal
  guardianName?: string;
  guardianPhone?: string;
  guardianEmail?: string;

  // Mídias Fotográficas
  facePhotoUrl?: string;
  profilePhotoUrl?: string;
  fullBodyPhotoUrl?: string;
  coverPhoto?: string;
  coverPhotoUrl?: string;
  photos?: CandidatePhoto[];

  // Auditoria de Revisão
  reviewedBy?: string;
  reviewedAt?: string;
  feedbackNotes?: string;
  internalNotes?: string;
  adminNotes?: string;
}

export interface CandidateStatusUpdate {
  status: CandidateStatus;
  feedbackNotes?: string;
  adminNotes?: string;
  notes?: string;
}

export interface CandidateStatusUpdatePayload {
  status: CandidateStatus;
  notes?: string;
  adminNotes?: string;
  feedbackNotes?: string;
}

export interface CandidateSubmissionFilters {
  search?: string;
  status?: CandidateStatus | 'ALL';
  gender?: string;
  minHeight?: number;
  maxHeight?: number;
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
  sort?: string;
  isMinor?: boolean;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first?: boolean;
  last?: boolean;
  empty?: boolean;
}

export interface CandidatePageResponse extends PageResponse<Candidate> {}

