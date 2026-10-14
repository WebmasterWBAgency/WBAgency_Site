import { Injectable } from '@angular/core';

export interface ImageDimensions {
  width: number;
  height: number;
}

export interface ImageProcessResult {
  valid: boolean;
  processedFile?: File;
  error?: string;
  originalDimensions?: ImageDimensions;
  finalDimensions?: ImageDimensions;
  wasResized?: boolean;
}

export interface ProcessMediaOptions {
  maxSizeMb: number;
  minPixels: number;
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

export async function processAdminImage(
  file: File,
  options: ProcessMediaOptions = {
    maxSizeMb: 15,
    minPixels: 4_000_000,
    maxDimension: 2560,
    quality: 0.90,
    outputFormat: 'image/webp'
  }
): Promise<{ valid: boolean; file?: File; error?: string }> {
  // 1. Validação do teto em MB
  const maxBytes = options.maxSizeMb * 1024 * 1024;
  if (file.size > maxBytes) {
    return {
      valid: false,
      error: `O arquivo "${file.name}" ultrapassa o limite máximo permitido de ${options.maxSizeMb} MB.`
    };
  }

  // 1.1 Validação de tipo MIME
  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp'];
  const mime = file.type ? file.type.toLowerCase() : '';
  if (!allowedTypes.includes(mime)) {
    return {
      valid: false,
      error: 'Formato de imagem não suportado. Por favor, envie uma foto em formato JPG, PNG ou WEBP.'
    };
  }

  // 2. Leitura de dimensões reais
  const img = new Image();
  const objectUrl = URL.createObjectURL(file);

  return new Promise((resolve) => {
    img.onload = async () => {
      URL.revokeObjectURL(objectUrl);
      const width = img.naturalWidth;
      const height = img.naturalHeight;
      const totalPixels = width * height;

      if (totalPixels < options.minPixels) {
        const currentMp = (totalPixels / 1_000_000).toFixed(1);
        resolve({
          valid: false,
          error: `Resolução insuficiente (${width}x${height}px - ${currentMp} MP). Envie fotos com no mínimo 4 Megapixels para nitidez em telas UHD.`
        });
        return;
      }

      // 3. Redimensionamento proporcional se o lado maior exceder o alvo
      let targetWidth = width;
      let targetHeight = height;
      if (options.maxDimension && (width > options.maxDimension || height > options.maxDimension)) {
        if (width > height) {
          targetHeight = Math.round((height * options.maxDimension) / width);
          targetWidth = options.maxDimension;
        } else {
          targetWidth = Math.round((width * options.maxDimension) / height);
          targetHeight = options.maxDimension;
        }
      }

      const canvas = document.createElement('canvas');
      canvas.width = targetWidth;
      canvas.height = targetHeight;
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        resolve({ valid: true, file }); // Fallback seguro
        return;
      }

      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, 0, 0, targetWidth, targetHeight);

      const outputFormat = options.outputFormat || 'image/webp';
      const quality = options.quality ?? 0.90;

      canvas.toBlob(
        (blob) => {
          // Desaloca dimensões do canvas
          canvas.width = 0;
          canvas.height = 0;

          if (!blob) {
            resolve({ valid: true, file });
            return;
          }
          const ext = outputFormat === 'image/webp' ? '.webp' : '.jpg';
          const newName = file.name.replace(/\.[^/.]+$/, '') + ext;
          const processedFile = new File([blob], newName, { type: outputFormat, lastModified: Date.now() });
          resolve({ valid: true, file: processedFile });
        },
        outputFormat,
        quality
      );
    };

    img.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      resolve({ valid: false, error: `Não foi possível ler os dados da imagem "${file.name}".` });
    };

    img.src = objectUrl;
  });
}

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
    // Validação de magic bytes para PDF (%PDF)
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

  // Validação de resolução gráfica mínima para impressão (mínimo ~1800x2400px ou 4.3 MP)
  const img = new Image();
  const objectUrl = URL.createObjectURL(file);

  return new Promise((resolve) => {
    img.onload = () => {
      URL.revokeObjectURL(objectUrl);
      const width = img.naturalWidth;
      const height = img.naturalHeight;
      const totalPixels = width * height;
      const minDimension = Math.min(width, height);
      const maxDimension = Math.max(width, height);

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

      // Se a imagem estiver muito pesada (> 12 MB) ou for PNG pesado (> 6 MB), comprime para alta qualidade preservando fidelidade
      if (file.size > 12 * 1024 * 1024 || (file.type === 'image/png' && file.size > 6 * 1024 * 1024)) {
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
  /** Limite máximo de peso de arquivo: 8 MB (padrão público/candidatos) */
  readonly MAX_FILE_SIZE_BYTES = 8 * 1024 * 1024;

  /** Limite máximo de peso de arquivo para admin: 15 MB */
  readonly ADMIN_MAX_PHOTO_BYTES = 15 * 1024 * 1024;

  /** Patamar mínimo de resolução para monitores UHD 16"+: 3.8 Megapixels (~4 MP) */
  readonly MIN_UHD_PIXELS = 3_800_000;

  /** Patamar mínimo de resolução estrito para fotos administrativas: 4.0 Megapixels */
  readonly ADMIN_MIN_PHOTO_PIXELS = 4_000_000;

  /** Limiar de reamostragem: imagens acima de 6 MP são suavemente reamostradas */
  readonly MAX_TARGET_PIXELS = 6_000_000;

  /** Dimensão máxima permitida no lado maior ao reamostrar (2560px padrão QHD/UHD) */
  readonly TARGET_MAX_DIMENSION = 2560;

  /** Qualidade de compressão JPEG/WebP de alta fidelidade visual (90% - faixa 0.88 - 0.92) */
  readonly COMPRESSION_QUALITY = 0.90;

  readonly ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

  /**
   * Processa foto de modelo no contexto administrativo:
   * Valida teto de 15 MB, mínimo de 4 MP e reamostra com WebP 0.90 / JPEG 0.92 (máx 2560px).
   */
  async processAdminImage(
    file: File,
    options?: Partial<ProcessMediaOptions>
  ): Promise<{ valid: boolean; file?: File; error?: string }> {
    const fullOptions: ProcessMediaOptions = {
      maxSizeMb: options?.maxSizeMb ?? 15,
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

  /**
   * Valida e pré-processa uma foto client-side (fluxo de inscrição pública):
   * 1. Rejeita arquivos acima de 8 MB.
   * 2. Rejeita arquivos abaixo de ~4 Megapixels (UHD).
   * 3. Comprime fotos em alta definição usando resolução alvo de ~4 Megapixels (lado maior até 2560px)
   *    com compressão JPEG/WebP (qualidade 0.90), reduzindo o payload total para menos de 10 MB.
   */
  async validateAndProcessPhoto(file: File): Promise<ImageProcessResult> {
    if (!file) {
      return { valid: false, error: 'Nenhum arquivo de imagem fornecido.' };
    }

    // 1. Validação estrita de peso (Teto individual de 8 MB)
    if (file.size > this.MAX_FILE_SIZE_BYTES) {
      return {
        valid: false,
        error: `A foto "${file.name}" excede o limite máximo permitido de 8 MB.`
      };
    }

    // 2. Validação de formato (MIME)
    const mime = file.type ? file.type.toLowerCase() : '';
    if (!this.ALLOWED_TYPES.includes(mime)) {
      return {
        valid: false,
        error: 'Formato de imagem não suportado. Por favor, envie uma foto em formato JPG, PNG ou WEBP.'
      };
    }

    // 3. Inspeção de dimensões em memória
    let dimensions: ImageDimensions;
    try {
      dimensions = await this.getImageDimensions(file);
    } catch (err) {
      return {
        valid: false,
        error: 'Não foi possível ler as dimensões da imagem. O arquivo pode estar corrompido.'
      };
    }

    const totalPixels = dimensions.width * dimensions.height;
    const megaPixels = totalPixels / 1_000_000;

    // 4. Bloqueio de baixa definição (< 3.8 MP)
    if (totalPixels < this.MIN_UHD_PIXELS) {
      return {
        valid: false,
        originalDimensions: dimensions,
        error: `A imagem selecionada possui baixa definição (${dimensions.width}x${dimensions.height} px - ${megaPixels.toFixed(1)} MP). Para garantir visualização nítida em telas de alta definição (UHD), envie fotos com resolução mínima de 4 Megapixels (aproximadamente 2560x1600 px ou superior).`
      };
    }

    // 5. Otimização e compressão para padrão UHD editorial:
    // Redimensiona para o alvo de ~4 MP (máx 2560px) e comprime a 90% qualidade JPEG para payload leve
    const maxSide = Math.max(dimensions.width, dimensions.height);
    const shouldOptimize = totalPixels > this.MAX_TARGET_PIXELS ||
                           maxSide > this.TARGET_MAX_DIMENSION ||
                           file.size > 1_200_000 ||
                           file.type === 'image/png';

    if (shouldOptimize) {
      try {
        const { blob, newDimensions } = await this.resizeToTargetUhd(file, dimensions);
        const fileNameWithoutExt = file.name.replace(/\.[^/.]+$/, '');
        const normalizedName = `${fileNameWithoutExt}.jpg`;
        const processedFile = new File([blob], normalizedName, {
          type: 'image/jpeg',
          lastModified: Date.now()
        });

        return {
          valid: true,
          processedFile,
          originalDimensions: dimensions,
          finalDimensions: newDimensions,
          wasResized: true
        };
      } catch (resizeErr) {
        // Fallback defensivo: se a reamostragem do canvas falhar, mas o arquivo é válido e <= 8 MB, aceita o original
        return {
          valid: true,
          processedFile: file,
          originalDimensions: dimensions,
          finalDimensions: dimensions,
          wasResized: false
        };
      }
    }

    // Resolução nativa adequada e peso leve já comprimido (< 1.2 MB)
    return {
      valid: true,
      processedFile: file,
      originalDimensions: dimensions,
      finalDimensions: dimensions,
      wasResized: false
    };
  }

  /**
   * Obtém as dimensões naturais de um arquivo de imagem em memória.
   */
  getImageDimensions(file: File): Promise<ImageDimensions> {
    return new Promise((resolve, reject) => {
      if (typeof createImageBitmap === 'function') {
        createImageBitmap(file)
          .then((bitmap) => {
            const dims = { width: bitmap.width, height: bitmap.height };
            bitmap.close();
            resolve(dims);
          })
          .catch(() => this.fallbackImageDimensions(file).then(resolve).catch(reject));
      } else {
        this.fallbackImageDimensions(file).then(resolve).catch(reject);
      }
    });
  }

  private fallbackImageDimensions(file: File): Promise<ImageDimensions> {
    return new Promise((resolve, reject) => {
      const url = URL.createObjectURL(file);
      const img = new Image();
      img.onload = () => {
        URL.revokeObjectURL(url);
        const dims = { width: img.naturalWidth, height: img.naturalHeight };
        img.src = '';
        resolve(dims);
      };
      img.onerror = () => {
        URL.revokeObjectURL(url);
        img.src = '';
        reject(new Error('Falha ao decodificar imagem.'));
      };
      img.src = url;
    });
  }

  /**
   * Reamostra suavemente a imagem para o padrão UHD (lado maior até 2560px),
   * preservando a proporção de aspecto exata e qualidade visual editorial.
   */
  private resizeToTargetUhd(
    file: File,
    dimensions: ImageDimensions
  ): Promise<{ blob: Blob; newDimensions: ImageDimensions }> {
    return new Promise((resolve, reject) => {
      const url = URL.createObjectURL(file);
      const img = new Image();

      img.onload = () => {
        URL.revokeObjectURL(url);

        let targetWidth = dimensions.width;
        let targetHeight = dimensions.height;
        const maxSide = Math.max(targetWidth, targetHeight);

        if (maxSide > this.TARGET_MAX_DIMENSION) {
          const scale = this.TARGET_MAX_DIMENSION / maxSide;
          targetWidth = Math.round(targetWidth * scale);
          targetHeight = Math.round(targetHeight * scale);
        }

        const canvas = document.createElement('canvas');
        canvas.width = targetWidth;
        canvas.height = targetHeight;

        const ctx = canvas.getContext('2d', { alpha: file.type === 'image/png' });
        if (!ctx) {
          canvas.width = 0;
          canvas.height = 0;
          img.src = '';
          reject(new Error('Contexto 2D do Canvas indisponível.'));
          return;
        }

        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'high';
        ctx.drawImage(img, 0, 0, targetWidth, targetHeight);

        const outputMime = file.type === 'image/png' ? 'image/png' : 'image/jpeg';
        canvas.toBlob(
          (blob) => {
            // Desalocação explícita de buffer gráfico para evitar OOM crash no mobile
            canvas.width = 0;
            canvas.height = 0;
            img.src = '';

            if (blob) {
              resolve({
                blob,
                newDimensions: { width: targetWidth, height: targetHeight }
              });
            } else {
              reject(new Error('Falha ao gerar Blob do Canvas.'));
            }
          },
          outputMime,
          this.COMPRESSION_QUALITY
        );
      };

      img.onerror = () => {
        URL.revokeObjectURL(url);
        img.src = '';
        reject(new Error('Erro ao carregar imagem no Canvas.'));
      };

      img.src = url;
    });
  }
}
