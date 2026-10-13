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

@Injectable({
  providedIn: 'root'
})
export class ImageProcessingService {
  /** Limite máximo de peso de arquivo: 8 MB */
  readonly MAX_FILE_SIZE_BYTES = 8 * 1024 * 1024;

  /** Patamar mínimo de resolução para monitores UHD 16"+: 3.8 Megapixels (~4 MP) */
  readonly MIN_UHD_PIXELS = 3_800_000;

  /** Limiar de reamostragem: imagens acima de 6 MP são suavemente reamostradas */
  readonly MAX_TARGET_PIXELS = 6_000_000;

  /** Dimensão máxima permitida no lado maior ao reamostrar (2560px padrão QHD/UHD) */
  readonly TARGET_MAX_DIMENSION = 2560;

  /** Qualidade de compressão JPEG/WebP de alta fidelidade visual (90% - faixa 0.88 - 0.92) */
  readonly COMPRESSION_QUALITY = 0.90;

  readonly ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

  /**
   * Valida e pré-processa uma foto client-side:
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
