package com.wbscouting.api.security;

import com.wbscouting.api.exception.InvalidFileException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes unitários para ImageSecuritySanitizer.
 * Valida a remoção de metadados EXIF/GPS de celulares e a preservação da integridade visual das fotos.
 */
class ImageSecuritySanitizerTest {

    @Test
    @DisplayName("Deve eliminar integralmente metadados EXIF/GPS e dados de smartphone em fotos JPEG")
    void shouldStripExifAndGpsMetadataFromMobilePhotos() throws IOException {
        int width = 120;
        int height = 80;

        // 1. Gera imagem de teste com padrão visual conhecido (metade vermelha, metade azul)
        BufferedImage original = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = original.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(Color.BLUE);
        g.fillRect(width / 2, 0, width / 2, height);
        g.dispose();

        ByteArrayOutputStream jpegBaos = new ByteArrayOutputStream();
        ImageIO.write(original, "jpg", jpegBaos);
        byte[] pureJpeg = jpegBaos.toByteArray();

        // 2. Injeta segmento APP1 (EXIF) simulando foto capturada por celular com GPS e dados do aparelho
        String gpsPayload = "Exif\0\0Make=Apple;Model=iPhone 15 Pro;GPSLat=-23.55052;GPSLong=-46.633308;Altitude=760m";
        byte[] exifPayloadBytes = gpsPayload.getBytes(StandardCharsets.ISO_8859_1);
        int app1Length = 2 + exifPayloadBytes.length; // 2 bytes de comprimento + dados

        ByteArrayOutputStream mobilePhotoBaos = new ByteArrayOutputStream();
        // SOI (Start of Image)
        mobilePhotoBaos.write(pureJpeg[0]);
        mobilePhotoBaos.write(pureJpeg[1]);

        // Marcador APP1: FF E1 + tamanho big-endian + payload
        mobilePhotoBaos.write(0xFF);
        mobilePhotoBaos.write(0xE1);
        mobilePhotoBaos.write((app1Length >> 8) & 0xFF);
        mobilePhotoBaos.write(app1Length & 0xFF);
        mobilePhotoBaos.write(exifPayloadBytes);

        // Restante do JPEG original (a partir do byte 2)
        mobilePhotoBaos.write(pureJpeg, 2, pureJpeg.length - 2);
        byte[] mobilePhotoBytes = mobilePhotoBaos.toByteArray();

        // Assegura que o arquivo bruto contém os dados sensíveis simulados de smartphone
        String dirtyContent = new String(mobilePhotoBytes, StandardCharsets.ISO_8859_1);
        assertThat(dirtyContent).contains("iPhone 15 Pro");
        assertThat(dirtyContent).contains("GPSLat=-23.55052");

        MockMultipartFile dirtyFile = new MockMultipartFile(
                "photo",
                "IMG_20261012_GPS.jpg",
                "image/jpeg",
                mobilePhotoBytes
        );

        // 3. Executa a sanitização ativa
        MultipartFile sanitized = ImageSecuritySanitizer.sanitizeAndReencode(dirtyFile);

        // 4. Verificações de eliminação estrita de metadados
        assertThat(sanitized).isNotNull();
        assertThat(sanitized.getSize()).isGreaterThan(0);
        assertThat(sanitized.getContentType()).isEqualTo("image/jpeg");

        byte[] cleanBytes = sanitized.getBytes();
        String cleanContent = new String(cleanBytes, StandardCharsets.ISO_8859_1);

        // Todos os metadados de câmera e GPS foram eliminados
        assertThat(cleanContent).doesNotContain("iPhone 15 Pro");
        assertThat(cleanContent).doesNotContain("GPSLat");
        assertThat(cleanContent).doesNotContain("GPSLong");
        assertThat(cleanContent).doesNotContain("Apple");
        assertThat(cleanContent).doesNotContain("Exif");

        // O marcador APP1 (0xFF 0xE1) não está presente no arquivo resultante
        boolean containsApp1Marker = false;
        for (int i = 0; i < cleanBytes.length - 1; i++) {
            if ((cleanBytes[i] & 0xFF) == 0xFF && (cleanBytes[i + 1] & 0xFF) == 0xE1) {
                containsApp1Marker = true;
                break;
            }
        }
        assertThat(containsApp1Marker).isFalse();

        // 5. Preservação da integridade visual da foto
        BufferedImage reloaded = ImageIO.read(new ByteArrayInputStream(cleanBytes));
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getWidth()).isEqualTo(width);
        assertThat(reloaded.getHeight()).isEqualTo(height);

        // Amostra de pixel do lado esquerdo (deve permanecer predominantemente vermelho)
        Color leftSample = new Color(reloaded.getRGB(width / 4, height / 2));
        assertThat(leftSample.getRed()).isGreaterThan(200);
        assertThat(leftSample.getBlue()).isLessThan(50);

        // Amostra de pixel do lado direito (deve permanecer predominantemente azul)
        Color rightSample = new Color(reloaded.getRGB((3 * width) / 4, height / 2));
        assertThat(rightSample.getBlue()).isGreaterThan(200);
        assertThat(rightSample.getRed()).isLessThan(50);
    }

    @Test
    @DisplayName("Deve sanitizar PNG e preservar dimensões e canal de transparência alfa")
    void shouldSanitizePngPreservingDimensionsAndTransparency() throws IOException {
        int width = 80;
        int height = 80;

        BufferedImage pngImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = pngImage.createGraphics();
        g.setColor(new Color(0, 180, 0, 128)); // Verde semi-transparente
        g.fillOval(10, 10, 60, 60);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(pngImage, "png", baos);
        byte[] pngBytes = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", pngBytes);

        MultipartFile sanitized = ImageSecuritySanitizer.sanitizeAndReencode(file);

        assertThat(sanitized).isNotNull();
        assertThat(sanitized.getContentType()).isEqualTo("image/png");

        BufferedImage reloaded = ImageIO.read(new ByteArrayInputStream(sanitized.getBytes()));
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getWidth()).isEqualTo(width);
        assertThat(reloaded.getHeight()).isEqualTo(height);
        assertThat(reloaded.getColorModel().hasAlpha()).isTrue();
    }

    @Test
    @DisplayName("Deve rejeitar arquivo com magic bytes corrompidos ou executáveis mascarados")
    void shouldRejectCorruptOrDisguisedFiles() {
        byte[] invalidHeader = new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04}; // Assinatura MZ de executável
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", invalidHeader);

        assertThatThrownBy(() -> ImageSecuritySanitizer.sanitizeAndReencode(file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Formato de imagem não suportado");
    }

    @Test
    @DisplayName("Deve rejeitar quando extensão declarada divergir dos magic bytes reais (disfarce)")
    void shouldRejectDisguisedExtensionMismatch() {
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile disguisedJpg = new MockMultipartFile("file", "fake.jpg", "image/jpeg", pngHeader);

        assertThatThrownBy(() -> ImageSecuritySanitizer.sanitizeAndReencode(disguisedJpg))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Assinatura de arquivo inválida ou formato disfarçado");

        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
        MockMultipartFile disguisedPng = new MockMultipartFile("file", "fake.png", "image/png", jpegHeader);

        assertThatThrownBy(() -> ImageSecuritySanitizer.sanitizeAndReencode(disguisedPng))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Assinatura de arquivo inválida ou formato disfarçado");
    }

    @Test
    @DisplayName("Deve rejeitar arquivo nulo ou vazio")
    void shouldRejectNullOrEmptyFile() {
        assertThatThrownBy(() -> ImageSecuritySanitizer.sanitizeAndReencode(null))
                .isInstanceOf(InvalidFileException.class);

        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> ImageSecuritySanitizer.sanitizeAndReencode(emptyFile))
                .isInstanceOf(InvalidFileException.class);
    }
}
