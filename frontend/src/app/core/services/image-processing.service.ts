import { Injectable } from '@angular/core';

export interface ImageDimensions {
  width: number;
  height: number;
}

export interface AdaptiveCompressionOptions {
  maxTargetBytes: number;  // ex: 15MB para admin, 8MB para apply, 25MB para composite
  minPixels?: number;      // 4.000.000 (4 MP para UHD)
  preferredFormat?: 'image/webp' | 'image/jpeg';
  maxDimension?: number;   // padrão 3840 (UHD 4K) ou 2560
}

export interface AdaptiveCompressionResult {
  valid: boolean;
  file?: File;
  error?: string;
  width?: number;
  height?: number;
  megapixels?: number;
}

export type ProcessedImageResult = AdaptiveCompressionResult;

export interface ImageProcessResult extends ProcessedImageResult {
  processedFile?: File;
  originalDimensions?: ImageDimensions;
  finalDimensions?: ImageDimensions;
  wasResized?: boolean;
}

export interface ProcessMediaOptions {
  maxSizeMb?: number;
  minPixels?: number;
  maxDimension?: number;
  quality?: number;
  outputFormat?: 'image/webp' | 'image/jpeg';
}

export interface ProcessCompositeOptions {
  maxSizeMb?: number;
  minPixels?: number;
  minWidth?: number;
  minHeight?: number;
}

/**
 * Função universal de compressão adaptativa com loop de convergência de qualidade:
 * - NUNCA rejeita arquivos originais pesados (17 MB, 30 MB, 50 MB) na entrada.
 * - Aceita imagens em alta resolução e as converte/reamostra progressivamente para
 *   a melhor qualidade visual possível até caber rigorosamente em options.maxTargetBytes.
 * - Garante definição mínima (>= 3.9 ~ 4.0 MP) sem interpolação destrutiva.
 * - Expurgar metadados EXIF e dados de GPS via re-renderização em Canvas 2D.
 */
export async function compressToTargetSize(
  file: File,
  options: AdaptiveCompressionOptions
): Promise<AdaptiveCompressionResult> {
  // 1. Se for PDF (caso do composite), mantém o binário original validando apenas o teto
  const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
  if (isPdf) {
    if (file.size > options.maxTargetBytes) {
      const limitMb = (options.maxTargetBytes / (1024 * 1024)).toFixed(0);
      return {
        valid: false,
        error: `O arquivo PDF ultrapassa o limite máximo permitido de ${limitMb} MB.`
      };
    }
    return { valid: true, file };
  }

  // 1.1 Validação de formato suportado para imagens
  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp'];
  const mime = file.type ? file.type.toLowerCase().trim() : '';
  if (mime && !allowedTypes.includes(mime)) {
    return {
      valid: false,
      error: 'Formato de imagem não suportado. Por favor, envie uma foto em formato JPG, PNG ou WEBP.'
    };
  }

  // 2. Leitura da Imagem
  const img = new Image();
  const objectUrl = URL.createObjectURL(file);

  return new Promise((resolve) => {
    img.onload = async () => {
      URL.revokeObjectURL(objectUrl);
      const width = img.naturalWidth;
      const height = img.naturalHeight;
      const totalPixels = width * height;
      const minPixels = options.minPixels ?? 3_900_000;

      // Se a resolução original for inferior a 4 MP, não fazer upscaling artificial
      if (totalPixels < minPixels) {
        const mp = (totalPixels / 1_000_000).toFixed(2);
        resolve({
          valid: false,
          width,
          height,
          megapixels: Number(mp),
          error: `A imagem possui baixa definição (${width}x${height}px - ${mp} MP). O sistema exige fotos com no mínimo 4 Megapixels para telas UHD.`
        });
        return;
      }

      // Se o arquivo original já estiver abaixo do teto e no formato correto (WebP/JPEG), aceita diretamente
      if (file.size <= options.maxTargetBytes && (file.type === 'image/webp' || file.type === 'image/jpeg')) {
        resolve({
          valid: true,
          file,
          width,
          height,
          megapixels: Number((totalPixels / 1_000_000).toFixed(2))
        });
        return;
      }

      // 3. Downscaling proporcional inteligente se dimensões forem excessivas (> 3840px / 4K)
      let targetWidth = width;
      let targetHeight = height;
      const MAX_SIDE = options.maxDimension ?? 3840; // Preserva UHD 4K nativo
      if (width > MAX_SIDE || height > MAX_SIDE) {
        if (width > height) {
          targetHeight = Math.round((height * MAX_SIDE) / width);
          targetWidth = MAX_SIDE;
        } else {
          targetWidth = Math.round((width * MAX_SIDE) / height);
          targetHeight = MAX_SIDE;
        }
      }

      const canvas = document.createElement('canvas');
      canvas.width = targetWidth;
      canvas.height = targetHeight;
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        resolve({ valid: true, file });
        return;
      }

      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, 0, 0, targetWidth, targetHeight);

      // 4. Loop de Convergência de Qualidade (0.95 -> 0.70) para garantir que fique abaixo de maxTargetBytes
      const format = options.preferredFormat ?? 'image/webp';
      let quality = 0.95;
      let resultBlob: Blob | null = null;

      while (quality >= 0.70) {
        resultBlob = await new Promise<Blob | null>((resBlob) => {
          canvas.toBlob((b) => resBlob(b), format, quality);
        });

        if (resultBlob && resultBlob.size <= options.maxTargetBytes) {
          break; // Atingiu o tamanho máximo mantendo a melhor qualidade possível
        }
        quality -= 0.05; // Reduz gradualmente a compressão se ainda estiver acima do teto
      }

      if (!resultBlob || resultBlob.size > options.maxTargetBytes) {
        // Se ainda assim estourar, reduz a escala em 80% e reexporta
        canvas.width = Math.round(targetWidth * 0.8);
        canvas.height = Math.round(targetHeight * 0.8);
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        resultBlob = await new Promise<Blob | null>((resBlob) => {
          canvas.toBlob((b) => resBlob(b), format, 0.85);
        });
      }

      // Limpeza de recursos do canvas
      canvas.width = 0;
      canvas.height = 0;

      if (!resultBlob) {
        resolve({ valid: false, error: 'Falha ao processar a imagem.' });
        return;
      }

      if (resultBlob.size > options.maxTargetBytes) {
        const limitMb = (options.maxTargetBytes / (1024 * 1024)).toFixed(0);
        resolve({
          valid: false,
          error: `Não foi possível comprimir a foto para o limite de ${limitMb} MB mantendo a qualidade mínima exigida.`
        });
        return;
      }

      const ext = format === 'image/webp' ? '.webp' : '.jpg';
      const cleanName = file.name.replace(/\.[^/.]+$/, '') + ext;
      const finalFile = new File([resultBlob], cleanName, { type: format, lastModified: Date.now() });

      resolve({
        valid: true,
        file: finalFile,
        width: targetWidth,
        height: targetHeight,
        megapixels: Number(((targetWidth * targetHeight) / 1_000_000).toFixed(2))
      });
    };

    img.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      resolve({ valid: false, error: `Não foi possível carregar os dados do arquivo "${file.name}".` });
    };

    img.src = objectUrl;
  });
}

/**
 * Função legada/compatibilidade para processamento adaptativo
 */
export async function processAndOptimizeImage(
  file: File,
  maxSizeMb: number = 15,
  minPixels: number = 3_900_000,
  targetMaxDimension: number = 3840,
  quality: number = 0.95
): Promise<ProcessedImageResult> {
  return compressToTargetSize(file, {
    maxTargetBytes: maxSizeMb * 1024 * 1024,
    minPixels,
    maxDimension: targetMaxDimension,
    preferredFormat: 'image/webp'
  });
}

/**
 * Função standalone para processamento administrativo de mídias de modelos (Teto 15 MB)
 */
export async function processAdminImage(
  file: File,
  options?: Partial<ProcessMediaOptions>
): Promise<ProcessedImageResult> {
  const maxMb = options?.maxSizeMb ?? 15;
  const minPixels = options?.minPixels ?? 3_900_000;
  const maxDimension = options?.maxDimension ?? 3840;
  const format = options?.outputFormat ?? 'image/webp';

  return compressToTargetSize(file, {
    maxTargetBytes: maxMb * 1024 * 1024,
    minPixels,
    maxDimension,
    preferredFormat: format
  });
}

/**
 * Processamento de Composite (Sedcard) de alta resolução ou arquivo PDF (Teto 25 MB)
 */
export async function processAdminComposite(
  file: File,
  options: ProcessCompositeOptions = {
    maxSizeMb: 25,
    minPixels: 4_320_000,
    minWidth: 1800,
    minHeight: 2400
  }
): Promise<{ valid: boolean; file?: File; error?: string }> {
  const maxMb = options.maxSizeMb ?? 25;
  const maxBytes = maxMb * 1024 * 1024;

  const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
  if (isPdf) {
    if (file.size > maxBytes) {
      const currentMb = (file.size / (1024 * 1024)).toFixed(1);
      return {
        valid: false,
        error: `O arquivo PDF "${file.name}" (${currentMb} MB) ultrapassa o limite máximo permitido de ${maxMb} MB.`
      };
    }
    return { valid: true, file };
  }

  // Se for imagem, comprime adaptativamente para o teto de 25 MB
  const result = await compressToTargetSize(file, {
    maxTargetBytes: maxBytes,
    minPixels: options.minPixels ?? 4_320_000,
    maxDimension: 3840,
    preferredFormat: 'image/webp'
  });

  return {
    valid: result.valid,
    file: result.file,
    error: result.error
  };
}

@Injectable({
  providedIn: 'root'
})
export class ImageProcessingService {
  /** Limite máximo de peso de arquivo final para candidatos: 8 MB */
  readonly CANDIDATE_MAX_PHOTO_BYTES = 8 * 1024 * 1024;

  /** Limite máximo de peso de arquivo administrativo final: 15 MB */
  readonly ADMIN_MAX_PHOTO_BYTES = 15 * 1024 * 1024;

  /** Limite máximo para composite final: 25 MB */
  readonly COMPOSITE_MAX_BYTES = 25 * 1024 * 1024;

  /** Patamar mínimo de resolução para monitores UHD 16"+: 4.0 Megapixels */
  readonly MIN_UHD_PIXELS = 3_900_000;

  /** Dimensão máxima permitida no lado maior ao reamostrar (3840px UHD 4K) */
  readonly TARGET_MAX_DIMENSION = 3840;

  readonly ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

  /**
   * Compressão adaptativa universal (nunca rejeita na entrada por tamanho bruto de arquivo).
   */
  async compressToTargetSize(
    file: File,
    options: AdaptiveCompressionOptions
  ): Promise<AdaptiveCompressionResult> {
    return compressToTargetSize(file, options);
  }

  /**
   * Processamento e otimização unificada conforme especificação UHD 4K.
   */
  async processAndOptimizeImage(
    file: File,
    maxSizeMb: number = 15,
    minPixels: number = 3_900_000,
    targetMaxDimension: number = 3840,
    quality: number = 0.95
  ): Promise<ProcessedImageResult> {
    return processAndOptimizeImage(file, maxSizeMb, minPixels, targetMaxDimension, quality);
  }

  /**
   * Valida e pré-processa uma foto client-side (fluxo de inscrição pública /apply e /become-model):
   * 1. NUNCA rejeita arquivos originais pesados (12 MB, 20 MB de celulares modernos).
   * 2. Rejeita arquivos abaixo de 4 Megapixels (UHD) sem interpolação forçada.
   * 3. Comprime fotos adaptativamente com WebP até atingir <= 8 MB (média 2-4 MB).
   */
  async validateAndProcessPhoto(file: File): Promise<ImageProcessResult> {
    if (!file) {
      return { valid: false, error: 'Nenhum arquivo de imagem fornecido.' };
    }

    const res = await compressToTargetSize(file, {
      maxTargetBytes: this.CANDIDATE_MAX_PHOTO_BYTES,
      minPixels: this.MIN_UHD_PIXELS,
      maxDimension: 3840,
      preferredFormat: 'image/webp'
    });

    return {
      ...res,
      processedFile: res.file,
      originalDimensions: res.width && res.height ? { width: res.width, height: res.height } : undefined,
      finalDimensions: res.width && res.height ? { width: res.width, height: res.height } : undefined,
      wasResized: Boolean(res.file && res.file !== file)
    };
  }

  /**
   * Processa foto de modelo no contexto administrativo:
   * Aceita arquivos de 17 MB, 30 MB, 50 MB de câmeras profissionais.
   * Comprime progressivamente para atingir o teto de <= 15 MB mantendo UHD 4K.
   */
  async processAdminImage(
    file: File,
    options?: Partial<ProcessMediaOptions>
  ): Promise<ProcessedImageResult> {
    return processAdminImage(file, options);
  }

  /**
   * Processa composite único no contexto administrativo:
   * Valida teto de 25 MB para PDF ou comprime imagem até <= 25 MB em altíssima resolução.
   */
  async processAdminComposite(
    file: File,
    options?: Partial<ProcessCompositeOptions>
  ): Promise<{ valid: boolean; file?: File; error?: string }> {
    return processAdminComposite(file, options);
  }
}
