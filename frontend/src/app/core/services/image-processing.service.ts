import { Injectable } from '@angular/core';

export interface ImageDimensions {
  width: number;
  height: number;
}

export interface ProcessedImageResult {
  valid: boolean;
  file?: File;
  error?: string;
  width?: number;
  height?: number;
  megapixels?: number;
}

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
 * Rotina unificada e imperativa de processamento, validação e otimização para UHD 4K:
 * 1. Validação de peso de arquivo bruto (teto padrão 8 MB).
 * 2. Validação estrita de formato suportado (JPG, PNG, WebP).
 * 3. Validação de definição mínima (4 Megapixels UHD).
 * 4. Downscaling proporcional inteligente se o lado maior exceder 2560 px.
 * 5. Sanitização de metadados EXIF/GPS via re-renderização em Canvas 2D e exportação WebP com qualidade 0.90.
 */
export async function processAndOptimizeImage(
  file: File,
  maxSizeMb: number = 8,
  minPixels: number = 4_000_000,
  targetMaxDimension: number = 2560,
  quality: number = 0.90
): Promise<ProcessedImageResult> {
  // 1. Validação de peso de arquivo bruto
  if (file.size > maxSizeMb * 1024 * 1024) {
    return {
      valid: false,
      error: `O arquivo "${file.name}" excede o tamanho máximo de ${maxSizeMb} MB.`
    };
  }

  // 1.1 Validação de tipo MIME
  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp'];
  const mime = file.type ? file.type.toLowerCase().trim() : '';
  if (mime && !allowedTypes.includes(mime)) {
    return {
      valid: false,
      error: 'Formato de imagem não suportado. Por favor, envie uma foto em formato JPG, PNG ou WEBP.'
    };
  }

  // 2. Leitura de dimensões reais
  const img = new Image();
  const objectUrl = URL.createObjectURL(file);

  return new Promise((resolve) => {
    img.onload = () => {
      URL.revokeObjectURL(objectUrl);
      const width = img.naturalWidth;
      const height = img.naturalHeight;
      const totalPixels = width * height;
      const megapixels = Number((totalPixels / 1_000_000).toFixed(2));

      // 3. Bloqueio se for inferior a 4 MP
      if (totalPixels < minPixels) {
        resolve({
          valid: false,
          width,
          height,
          megapixels,
          error: `A foto possui definição insuficiente (${width}x${height}px - ${megapixels} MP). O sistema exige fotos com no mínimo 4 Megapixels para visualização em telas UHD.`
        });
        return;
      }

      // 4. Downscaling proporcional automático (se lado maior > targetMaxDimension)
      let targetWidth = width;
      let targetHeight = height;
      if (width > targetMaxDimension || height > targetMaxDimension) {
        if (width > height) {
          targetHeight = Math.round((height * targetMaxDimension) / width);
          targetWidth = targetMaxDimension;
        } else {
          targetWidth = Math.round((width * targetMaxDimension) / height);
          targetHeight = targetMaxDimension;
        }
      }

      // 5. Renderização em Canvas e exportação limpa (expurga EXIF/GPS nativamente)
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

      canvas.toBlob(
        (blob) => {
          // Desaloca buffer de memória gráfica do Canvas
          canvas.width = 0;
          canvas.height = 0;

          if (!blob) {
            resolve({ valid: false, error: 'Falha ao processar a imagem.' });
            return;
          }
          const baseName = file.name.replace(/\.[^/.]+$/, '');
          const newFile = new File([blob], `${baseName}.webp`, { type: 'image/webp', lastModified: Date.now() });
          resolve({
            valid: true,
            file: newFile,
            width: targetWidth,
            height: targetHeight,
            megapixels: Number(((targetWidth * targetHeight) / 1_000_000).toFixed(2))
          });
        },
        'image/webp',
        quality
      );
    };

    img.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      resolve({ valid: false, error: `Não foi possível carregar o arquivo "${file.name}".` });
    };

    img.src = objectUrl;
  });
}

/**
 * Função standalone para processamento administrativo de mídias de modelos
 */
export async function processAdminImage(
  file: File,
  options?: Partial<ProcessMediaOptions>
): Promise<ProcessedImageResult> {
  const maxSizeMb = options?.maxSizeMb ?? 8;
  const minPixels = options?.minPixels ?? 4_000_000;
  const targetMaxDimension = options?.maxDimension ?? 2560;
  const quality = options?.quality ?? 0.90;
  return processAndOptimizeImage(file, maxSizeMb, minPixels, targetMaxDimension, quality);
}

/**
 * Processamento de Composite (Sedcard) de alta resolução ou arquivo PDF
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

  if (file.size > maxBytes) {
    const currentMb = (file.size / (1024 * 1024)).toFixed(1);
    return {
      valid: false,
      error: `O arquivo "${file.name}" (${currentMb} MB) ultrapassa o limite máximo permitido de ${maxMb} MB.`
    };
  }

  const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
  if (isPdf) {
    try {
      const slice = await file.slice(0, 5).text();
      if (!slice.startsWith('%PDF')) {
        return {
          valid: false,
          error: `O arquivo PDF "${file.name}" possui cabeçalho corrompido ou inválido.`
        };
      }
    } catch {
      // Fallback seguro se slice text falhar
    }
    return { valid: true, file };
  }

  const isImage = ['image/jpeg', 'image/png', 'image/webp'].includes(file.type?.toLowerCase() || '')
    || /\.(jpe?g|png|webp)$/i.test(file.name);

  if (!isImage) {
    return {
      valid: false,
      error: `Formato de composite não suportado. Envie um arquivo PDF, JPG, PNG ou WEBP.`
    };
  }

  const img = new Image();
  const objectUrl = URL.createObjectURL(file);

  return new Promise((resolve) => {
    img.onload = () => {
      URL.revokeObjectURL(objectUrl);
      const width = img.naturalWidth;
      const height = img.naturalHeight;
      const totalPixels = width * height;
      const minDimension = Math.min(width, height);

      const targetMinPixels = options.minPixels ?? 4_320_000;
      const targetShortSide = Math.min(options.minWidth ?? 1800, options.minHeight ?? 2400);

      if (totalPixels < targetMinPixels && minDimension < targetShortSide) {
        const currentMp = (totalPixels / 1_000_000).toFixed(1);
        resolve({
          valid: false,
          error: `Resolução gráfica insuficiente para impressão (${width}x${height}px - ${currentMp} MP). Composites em imagem devem ter no mínimo ~1800x2400px (qualidade gráfica de folha A4).`
        });
        return;
      }

      // Se a imagem estiver pesada (> 8 MB) ou for PNG pesado (> 4 MB), comprime para WebP preservando fidelidade
      if (file.size > 8 * 1024 * 1024 || (file.type === 'image/png' && file.size > 4 * 1024 * 1024)) {
        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          resolve({ valid: true, file });
          return;
        }

        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'high';
        ctx.drawImage(img, 0, 0, width, height);

        canvas.toBlob(
          (blob) => {
            canvas.width = 0;
            canvas.height = 0;
            if (!blob) {
              resolve({ valid: true, file });
              return;
            }
            const processedFile = new File([blob], file.name.replace(/\.[^/.]+$/, '') + '.webp', {
              type: 'image/webp',
              lastModified: Date.now()
            });
            resolve({ valid: true, file: processedFile });
          },
          'image/webp',
          0.92
        );
      } else {
        resolve({ valid: true, file });
      }
    };

    img.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      resolve({ valid: false, error: `Não foi possível carregar a imagem do composite "${file.name}".` });
    };

    img.src = objectUrl;
  });
}

@Injectable({
  providedIn: 'root'
})
export class ImageProcessingService {
  /** Limite máximo de peso de arquivo para fotografias: 8 MB */
  readonly MAX_FILE_SIZE_BYTES = 8 * 1024 * 1024;

  /** Limite máximo de peso de arquivo administrativo: 8 MB */
  readonly ADMIN_MAX_PHOTO_BYTES = 8 * 1024 * 1024;

  /** Patamar mínimo de resolução para monitores UHD 16"+: 4.0 Megapixels */
  readonly MIN_UHD_PIXELS = 4_000_000;

  /** Patamar mínimo de resolução estrito para fotos administrativas: 4.0 Megapixels */
  readonly ADMIN_MIN_PHOTO_PIXELS = 4_000_000;

  /** Dimensão máxima permitida no lado maior ao reamostrar (2560px padrão QHD/UHD) */
  readonly TARGET_MAX_DIMENSION = 2560;

  /** Qualidade de compressão WebP de alta fidelidade visual (90%) */
  readonly COMPRESSION_QUALITY = 0.90;

  readonly ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

  /**
   * Processamento e otimização unificada conforme especificação UHD 4K (4 MP / Teto 8 MB).
   */
  async processAndOptimizeImage(
    file: File,
    maxSizeMb: number = 8,
    minPixels: number = 4_000_000,
    targetMaxDimension: number = 2560,
    quality: number = 0.90
  ): Promise<ProcessedImageResult> {
    return processAndOptimizeImage(file, maxSizeMb, minPixels, targetMaxDimension, quality);
  }

  /**
   * Valida e pré-processa uma foto client-side (fluxo de inscrição pública /apply e /become-model):
   * 1. Rejeita arquivos acima de 8 MB.
   * 2. Rejeita arquivos abaixo de 4 Megapixels (UHD).
   * 3. Comprime fotos em alta definição (lado maior até 2560px) com WebP qualidade 0.90.
   */
  async validateAndProcessPhoto(file: File): Promise<ImageProcessResult> {
    if (!file) {
      return { valid: false, error: 'Nenhum arquivo de imagem fornecido.' };
    }

    const res = await processAndOptimizeImage(
      file,
      8,
      this.MIN_UHD_PIXELS,
      this.TARGET_MAX_DIMENSION,
      this.COMPRESSION_QUALITY
    );

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
   * Valida teto de 8 MB, mínimo de 4 MP e reamostra com WebP 0.90 (máx 2560px).
   */
  async processAdminImage(
    file: File,
    options?: Partial<ProcessMediaOptions>
  ): Promise<ProcessedImageResult> {
    const fullOptions: ProcessMediaOptions = {
      maxSizeMb: options?.maxSizeMb ?? 8,
      minPixels: options?.minPixels ?? this.ADMIN_MIN_PHOTO_PIXELS,
      maxDimension: options?.maxDimension ?? this.TARGET_MAX_DIMENSION,
      quality: options?.quality ?? this.COMPRESSION_QUALITY,
      outputFormat: options?.outputFormat ?? 'image/webp'
    };
    return processAdminImage(file, fullOptions);
  }

  /**
   * Processa composite único no contexto administrativo:
   * Valida teto de 25 MB, formato PDF ou imagem com resolução gráfica mínima (~1800x2400px).
   */
  async processAdminComposite(
    file: File,
    options?: Partial<ProcessCompositeOptions>
  ): Promise<{ valid: boolean; file?: File; error?: string }> {
    const fullOptions: ProcessCompositeOptions = {
      maxSizeMb: options?.maxSizeMb ?? 25,
      minPixels: options?.minPixels ?? 4_320_000,
      minWidth: options?.minWidth ?? 1800,
      minHeight: options?.minHeight ?? 2400
    };
    return processAdminComposite(file, fullOptions);
  }
}
