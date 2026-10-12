export interface CandidatePhotoAdmin {
  id: string;
  displayOrder: number;
  url: string;        // Consumido diretamente pelo template HTML
  signedUrl?: string; // Disponível para fallbacks ou refresh
  type?: string;
  title?: string;
  fileUrl?: string;
  fileName?: string;
  fileSizeBytes?: number;
}

export type CandidateMedia = CandidatePhotoAdmin;

export interface CandidateDetail {
  id: string;
  fullName: string;
  email: string;
  phone: string;
  instagram?: string;
  birthDate: string;
  age: number;
  isMinor: boolean;
  guardianName?: string;
  guardianPhone?: string;
  guardianEmail?: string;
  city: string;
  state: string;
  biometrics: {
    height: number;
    bust: number;
    waist: number;
    hips: number;
    shoes: number;
    eyes: string;
    hair: string;
  };
  photos: CandidateMedia[];
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'PROMOTED' | 'ARCHIVED';
  coverPhoto?: string;
  coverPhotoUrl?: string;
  facePhotoUrl?: string;
  face_photo_url?: string;
  profilePhotoUrl?: string;
  profile_photo_url?: string;
  fullBodyPhotoUrl?: string;
  full_body_photo_url?: string;
  promotedModelId?: string;
  convertedToModelId?: string;
  internalNotes?: string;
  lgpdConsent: boolean;
  lgpdConsentAt: string;
  submittedAt: string;
  protocol?: string;
}
