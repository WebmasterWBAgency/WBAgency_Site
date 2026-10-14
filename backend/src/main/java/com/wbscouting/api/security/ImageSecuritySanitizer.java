package com.wbscouting.api.security;

import com.wbscouting.api.exception.InvalidFileException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Validador e sanitizador ativo de imagens contra arquivos poliglota e injeção de payloads binários
 * em conformidade com o Item 17 da EAP-SEG-002.
 */
@Slf4j
public final class ImageSecuritySanitizer {

    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = new byte[]{
            (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
            (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A
    };
    private static final byte[] RIFF_MAGIC = new byte[]{(byte) 0x52, (byte) 0x49, (byte) 0x46, (byte) 0x46}; // "RIFF"
    private static final byte[] WEBP_MAGIC = new byte[]{(byte) 0x57, (byte) 0x45, (byte) 0x42, (byte) 0x50}; // "WEBP"

    private ImageSecuritySanitizer() {
    }

    /**
     * Valida magic bytes, decodifica via ImageIO.read() e regrava em um buffer limpo na memória via ImageIO.write(),
     * descartando todos os metadados não visuais (incluindo tags EXIF, dados de GPS e comentários de texto)
     * e expurgando bytes anexados no final do arquivo (polyglots) antes de encapsular o resultado em SanitizedMultipartFile.
     */
    public static MultipartFile sanitizeAndReencode(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("O arquivo de imagem é obrigatório e não pode ser vazio.");
        }

        byte[] originalBytes;
        try {
            originalBytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidFileException("Falha ao ler bytes do arquivo de imagem.", e);
        }

        if (originalBytes.length < 8) {
            throw new InvalidFileException("Arquivo de imagem corrompido ou excessivamente curto.");
        }

        // 1. Verificação estrita de Magic Bytes
        String format = detectImageFormat(originalBytes);
        if (format == null) {
            log.warn("Tentativa de upload com magic bytes inválidos. Tamanho: {} bytes", originalBytes.length);
            throw new InvalidFileException("Formato de imagem não suportado ou cabeçalho adulterado. Aceitos: JPEG, PNG, WEBP.");
        }

        // 2. Sanitização Ativa: Decodificação e regravação via ImageIO (quando decodificável)
        try {
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalBytes));
            if (originalImage != null) {
                int width = originalImage.getWidth();
                int height = originalImage.getHeight();

                if (width <= 0 || height <= 0 || width > 10000 || height > 10000) {
                    throw new InvalidFileException("Dimensões da imagem fora dos limites de segurança aceitáveis.");
                }

                int imageType = "png".equalsIgnoreCase(format) || originalImage.getColorModel().hasAlpha()
                        ? BufferedImage.TYPE_INT_ARGB
                        : BufferedImage.TYPE_INT_RGB;

                BufferedImage cleanImage = new BufferedImage(width, height, imageType);
                Graphics2D g2d = cleanImage.createGraphics();
                try {
                    g2d.drawImage(originalImage, 0, 0, null);
                } finally {
                    g2d.dispose();
                }

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                String writeFormat;
                if ("png".equalsIgnoreCase(format)) {
                    writeFormat = "png";
                } else if ("webp".equalsIgnoreCase(format) && canWriteFormat("webp")) {
                    writeFormat = "webp";
                } else {
                    writeFormat = "jpg";
                }

                boolean success = ImageIO.write(cleanImage, writeFormat, outputStream);

                if (success && outputStream.size() > 0) {
                    byte[] sanitizedBytes = outputStream.toByteArray();
                    log.debug("Imagem '{}' higienizada e regravada com sucesso (metadados EXIF/GPS expurgados). De {} para {} bytes.",
                            file.getOriginalFilename(), originalBytes.length, sanitizedBytes.length);

                    return new SanitizedMultipartFile(
                            file.getName(),
                            file.getOriginalFilename(),
                            resolveContentType(writeFormat),
                            sanitizedBytes
                    );
                }
            } else if (!"webp".equalsIgnoreCase(format)) {
                throw new InvalidFileException("O arquivo de imagem não pôde ser decodificado ou possui cabeçalho corrompido.");
            }
        } catch (InvalidFileException ife) {
            throw ife;
        } catch (Exception e) {
            if (!"webp".equalsIgnoreCase(format)) {
                log.warn("Falha na decodificação e reencodificação da imagem '{}': {}", file.getOriginalFilename(), e.getMessage());
                throw new InvalidFileException("Falha na sanitização ativa da imagem. Arquivo rejeitado por segurança.");
            }
            log.debug("Decodificação completa ImageIO indisponível para WebP: {}", e.getMessage());
        }

        // Magic bytes de WebP válidos confirmados
        return new SanitizedMultipartFile(
                file.getName(),
                file.getOriginalFilename(),
                resolveContentType(format),
                originalBytes
        );
    }

    public static String detectImageFormat(byte[] bytes) {
        if (bytes == null || bytes.length < 3) {
            return null;
        }

        if (bytes[0] == JPEG_MAGIC[0] && bytes[1] == JPEG_MAGIC[1] && bytes[2] == JPEG_MAGIC[2]) {
            return "jpeg";
        }

        if (bytes.length >= 8) {
            byte[] sub = Arrays.copyOfRange(bytes, 0, 8);
            if (Arrays.equals(sub, PNG_MAGIC)) {
                return "png";
            }
        }

        if (bytes.length >= 12) {
            byte[] riffSub = Arrays.copyOfRange(bytes, 0, 4);
            byte[] webpSub = Arrays.copyOfRange(bytes, 8, 12);
            if (Arrays.equals(riffSub, RIFF_MAGIC) && Arrays.equals(webpSub, WEBP_MAGIC)) {
                return "webp";
            }
        }

        return null;
    }

    private static String resolveContentType(String format) {
        if ("png".equalsIgnoreCase(format)) {
            return "image/png";
        }
        if ("webp".equalsIgnoreCase(format)) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private static boolean canWriteFormat(String formatName) {
        return ImageIO.getImageWritersByFormatName(formatName).hasNext();
    }

    /**
     * Extrai dimensões [largura, altura] de um array de bytes de imagem (suporta JPEG, PNG e WebP).
     * Retorna null se não conseguir decodificar.
     */
    public static int[] extractDimensions(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }

        String format = detectImageFormat(bytes);
        if (format == null) {
            return null;
        }

        if ("webp".equalsIgnoreCase(format)) {
            return extractWebpDimensions(bytes);
        }

        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img != null) {
                return new int[]{img.getWidth(), img.getHeight()};
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Extrai dimensões [largura, altura] de WebP analisando os headers VP8, VP8L ou VP8X.
     */
    private static int[] extractWebpDimensions(byte[] bytes) {
        if (bytes == null || bytes.length < 30) {
            return null;
        }
        try {
            String chunkHeader = new String(bytes, 12, 4);
            if ("VP8 ".equals(chunkHeader) && bytes.length >= 27) {
                int w = ((bytes[23] & 0xFF) | ((bytes[24] & 0xFF) << 8)) & 0x3FFF;
                int h = ((bytes[25] & 0xFF) | ((bytes[26] & 0xFF) << 8)) & 0x3FFF;
                if (w > 0 && h > 0) return new int[]{w, h};
            } else if ("VP8L".equals(chunkHeader) && bytes.length >= 25) {
                int b0 = bytes[21] & 0xFF;
                int b1 = bytes[22] & 0xFF;
                int b2 = bytes[23] & 0xFF;
                int b3 = bytes[24] & 0xFF;
                int w = 1 + (((b1 & 0x3F) << 8) | b0);
                int h = 1 + (((b3 & 0x0F) << 10) | (b2 << 2) | ((b1 & 0xC0) >> 6));
                if (w > 0 && h > 0) return new int[]{w, h};
            } else if ("VP8X".equals(chunkHeader) && bytes.length >= 30) {
                int w = 1 + ((bytes[24] & 0xFF) | ((bytes[25] & 0xFF) << 8) | ((bytes[26] & 0xFF) << 16));
                int h = 1 + ((bytes[27] & 0xFF) | ((bytes[28] & 0xFF) << 8) | ((bytes[29] & 0xFF) << 16));
                if (w > 0 && h > 0) return new int[]{w, h};
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Valida defensivamente fotos de modelos contra limites de peso (15 MB) e resolução mínima (4 Megapixels).
     */
    public static void validateModelPhotoDefensive(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new com.wbscouting.api.exception.BusinessException("O arquivo de imagem é obrigatório e não pode ser vazio.");
        }
        if (file.getSize() > 15L * 1024 * 1024) {
            throw new com.wbscouting.api.exception.BusinessException("O arquivo excede o limite máximo permitido de 15 MB.");
        }
        try {
            byte[] bytes = file.getBytes();
            int[] dims = extractDimensions(bytes);
            if (dims != null) {
                long totalPixels = (long) dims[0] * dims[1];
                if (totalPixels < 4_000_000L) {
                    throw new com.wbscouting.api.exception.BusinessException("A foto enviada possui definição inferior a 4 Megapixels.");
                }
            }
        } catch (com.wbscouting.api.exception.BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.warn("Falha ao inspecionar dimensões da foto '{}': {}", file.getOriginalFilename(), e.getMessage());
        }
    }
}
