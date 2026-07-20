package com.findguni.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetStorageServiceTest {

    @TempDir
    Path uploadDir;

    @Test
    void storesDecodedPngAndJpegInsideConfiguredUploadDirectoryWithGeneratedNames() throws Exception {
        AssetStorageService storage = new AssetStorageService(uploadDir.toString());

        String pngUrl = storage.storeItemImage(new MockMultipartFile(
                "photo", "clue.png", "image/png", imageBytes("png", true)));
        String jpegUrl = storage.storeItemImage(new MockMultipartFile(
                "photo", "clue.jpg", "image/jpeg", imageBytes("jpg", false)));
        String traversalNameUrl = storage.storeItemImage(new MockMultipartFile(
                "photo", "../outside.png", "image/png", imageBytes("png", true)));

        assertThat(pngUrl).matches("/uploads/[0-9a-f-]{36}\\.png");
        assertThat(jpegUrl).matches("/uploads/[0-9a-f-]{36}\\.jpg");
        assertThat(traversalNameUrl).matches("/uploads/[0-9a-f-]{36}\\.png");
        assertThat(traversalNameUrl).doesNotContain("outside", "..");
        assertStoredImage(pngUrl);
        assertStoredImage(jpegUrl);
        assertStoredImage(traversalNameUrl);
        assertThat(Files.list(uploadDir)).hasSize(3);
    }

    @Test
    void rejectsFakeImagesOversizedFilesAndPixelBombDimensions() throws Exception {
        AssetStorageService storage = new AssetStorageService(uploadDir.toString());

        assertThatThrownBy(() -> storage.storeItemImage(new MockMultipartFile(
                "photo", "fake.png", "image/png", "not an image".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.storeItemImage(new MockMultipartFile(
                "photo", "huge.jpg", "image/jpeg", new byte[(int) AssetStorageService.MAX_BYTES + 1])))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.storeItemImage(new MockMultipartFile(
                "photo", "pixel-bomb.png", "image/png", oversizedDimensionsPng())))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(Files.list(uploadDir)).isEmpty();
    }

    private void assertStoredImage(String url) throws Exception {
        Path stored = uploadDir.resolve(url.substring("/uploads/".length())).normalize();
        assertThat(stored.getParent()).isEqualTo(uploadDir.toAbsolutePath().normalize());
        assertThat(stored).isRegularFile();
        assertThat(ImageIO.read(stored.toFile())).isNotNull();
    }

    private byte[] imageBytes(String format, boolean alpha) throws Exception {
        BufferedImage image = new BufferedImage(3, 2,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(1, 0, Color.GREEN.getRGB());
        image.setRGB(2, 0, Color.BLUE.getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }

    private byte[] oversizedDimensionsPng() throws Exception {
        byte[] png = imageBytes("png", true);
        int oversizedWidth = 20_001;
        png[16] = (byte) (oversizedWidth >>> 24);
        png[17] = (byte) (oversizedWidth >>> 16);
        png[18] = (byte) (oversizedWidth >>> 8);
        png[19] = (byte) oversizedWidth;
        return png;
    }
}
