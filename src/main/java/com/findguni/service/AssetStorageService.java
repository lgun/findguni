package com.findguni.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class AssetStorageService {
    public static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final int MAX_EDGE = 1600;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");
    private final Path uploadRoot;

    @Value("${findguni.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    public AssetStorageService(@Value("${findguni.upload-dir:./uploads}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public String storeItemImage(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("이미지는 최대 8MB까지 업로드할 수 있습니다.");
        String contentType = Objects.toString(file.getContentType(), "").toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) throw new IllegalArgumentException("JPG, PNG, WEBP 이미지만 업로드할 수 있습니다.");

        BufferedImage decoded = decode(file);
        BufferedImage resized = resize(decoded);
        boolean transparent = resized.getColorModel().hasAlpha();
        String extension = transparent ? "png" : "jpg";
        Path destination = uploadRoot.resolve(UUID.randomUUID() + "." + extension).normalize();
        if (!destination.getParent().equals(uploadRoot)) throw new IllegalStateException("안전하지 않은 업로드 경로입니다.");
        try {
            Files.createDirectories(uploadRoot);
            if (!ImageIO.write(resized, extension, destination.toFile())) {
                throw new IOException("지원되는 이미지 writer가 없습니다.");
            }
            String uploadPath = "/uploads/" + destination.getFileName();
            String normalizedBaseUrl = Objects.toString(publicBaseUrl, "").trim().replaceAll("/+$", "");
            return normalizedBaseUrl.isBlank() ? uploadPath : normalizedBaseUrl + uploadPath;
        } catch (IOException e) {
            try { Files.deleteIfExists(destination); } catch (IOException ignored) {}
            throw new IllegalStateException("이미지를 저장하지 못했습니다.", e);
        }
    }

    private BufferedImage decode(MultipartFile file) {
        try (InputStream input = file.getInputStream(); ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) throw new IllegalArgumentException("이미지 데이터를 읽을 수 없습니다.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw new IllegalArgumentException("손상되었거나 지원하지 않는 이미지입니다.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 20_000 || height > 20_000 || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException("이미지 해상도가 너무 큽니다.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new IllegalArgumentException("이미지를 디코딩하지 못했습니다.");
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("손상된 이미지입니다.", e);
        }
    }

    private BufferedImage resize(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1.0, (double) MAX_EDGE / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        boolean alpha = source.getColorModel().hasAlpha();
        BufferedImage target = new BufferedImage(targetWidth, targetHeight,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            if (!alpha) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
            }
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }
}
