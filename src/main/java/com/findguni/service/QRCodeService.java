package com.findguni.service;

import com.findguni.model.EscapeGame;
import com.findguni.model.GameItem;
import com.findguni.model.GameStage;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;

@Service
public class QRCodeService {
    private final String publicBaseUrl;
    private final String canonicalHost;

    public QRCodeService(@Value("${findguni.public-base-url}") String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
        this.canonicalHost = canonicalHost(this.publicBaseUrl);
    }

    public String playUrl(EscapeGame game) {
        return publicBaseUrl + "/play/" + game.getSlug();
    }

    public String itemClueUrl(EscapeGame game, GameItem item) {
        return playUrl(game) + "/clue/" + item.getStableKey();
    }

    public String stagePuzzleUrl(EscapeGame game, GameStage stage) {
        return playUrl(game) + "/puzzle/" + stage.getStableKey();
    }

    public byte[] generateFor(EscapeGame game) {
        return generate(playUrl(game));
    }

    public byte[] generateForItem(EscapeGame game, GameItem item) {
        return generate(itemClueUrl(game, item));
    }

    public byte[] generateForStage(EscapeGame game, GameStage stage) {
        return generate(stagePuzzleUrl(game, stage));
    }

    public byte[] generate(String payload) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 640, 640,
                    Map.of(EncodeHintType.CHARACTER_SET, "UTF-8",
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("QR 코드를 생성하지 못했습니다.", e);
        }
    }

    public Optional<String> decode(MultipartFile frame) {
        if (frame == null || frame.isEmpty()) return Optional.empty();
        if (frame.getSize() > AssetStorageService.MAX_BYTES) {
            throw new IllegalArgumentException("스캔 이미지는 최대 8MB까지 전송할 수 있습니다.");
        }
        try {
            BufferedImage image = ImageIO.read(frame.getInputStream());
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1
                    || (long) image.getWidth() * image.getHeight() > 40_000_000L) {
                throw new IllegalArgumentException("QR 스캔 이미지가 올바르지 않습니다.");
            }
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
            try {
                return Optional.of(new MultiFormatReader().decode(bitmap).getText());
            } catch (NotFoundException e) {
                return Optional.empty();
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("QR 스캔 이미지를 읽을 수 없습니다.", e);
        }
    }

    public Optional<String> parseItemKey(String payload, String expectedSlug) {
        return parseTarget(payload, expectedSlug)
                .filter(target -> target.type() == QrTargetType.CLUE)
                .map(QrTarget::stableKey);
    }

    public Optional<QrTarget> parseTarget(String payload, String expectedSlug) {
        if (payload == null || payload.isBlank()) return Optional.empty();
        String value = payload.trim();
        for (QrTargetType type : QrTargetType.values()) {
            String internalPrefix = "findguni:" + type.pathSegment + ":" + expectedSlug + ":";
            if (value.startsWith(internalPrefix)) {
                return validStableKey(value.substring(internalPrefix.length()))
                        .map(key -> new QrTarget(type, key));
            }
        }
        try {
            URI uri = URI.create(value);
            if (uri.getHost() != null && !canonicalHost.isEmpty()
                    && !hostMatches(uri.getHost(), canonicalHost)) {
                return Optional.empty();
            }
            String path;
            path = uri.getPath();
            Matcher matcher = Pattern.compile("^/play/" + Pattern.quote(expectedSlug)
                    + "/(clue|puzzle)/([0-9a-fA-F-]{36})/?$").matcher(path);
            if (!matcher.matches()) return Optional.empty();
            QrTargetType type = "puzzle".equals(matcher.group(1)) ? QrTargetType.STAGE : QrTargetType.CLUE;
            return validStableKey(matcher.group(2)).map(key -> new QrTarget(type, key));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String canonicalHost(String baseUrl) {
        try {
            if (baseUrl == null || baseUrl.isBlank()) return "";
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private boolean hostMatches(String parsedHost, String expectedHost) {
        if (expectedHost == null || expectedHost.isBlank()) return true;
        return parsedHost != null && parsedHost.equalsIgnoreCase(expectedHost);
    }

    private Optional<String> validStableKey(String key) {
        try {
            return Optional.of(java.util.UUID.fromString(key).toString());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public enum QrTargetType {
        CLUE("clue"), STAGE("stage");

        private final String pathSegment;
        QrTargetType(String pathSegment) { this.pathSegment = pathSegment; }
    }

    public record QrTarget(QrTargetType type, String stableKey) {}
}
