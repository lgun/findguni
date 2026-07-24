package com.findguni.service;

import com.findguni.model.EscapeGame;
import com.findguni.model.GameFlowMode;
import com.findguni.model.GameItem;
import com.findguni.model.GameStage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class QrPrintKitService {
    private static final int CARDS_PER_PAGE = 6;
    private static final int PAGE_WIDTH = 1240;
    private static final int PAGE_HEIGHT = 1754;
    private static final int PAGE_MARGIN = 48;
    private static final int CARD_GAP = 20;

    private final QRCodeService qrCodes;

    public QrPrintKitService(QRCodeService qrCodes) {
        this.qrCodes = qrCodes;
    }

    public QrKit build(EscapeGame game, List<GameStage> stages, List<GameItem> items) {
        List<QrCard> cards = new ArrayList<>();
        cards.add(card(0, "시작", "게임 시작", game.getTitle(), qrCodes.playUrl(game),
                qrCodes.generateFor(game), "start"));

        int stageCount = 0;
        if (game.getFlowMode() == null || game.getFlowMode() == GameFlowMode.QR_EXPLORATION) {
            for (GameStage stage : stages) {
                if (!stage.isQrEnabled()) continue;
                stageCount++;
                cards.add(card(cards.size(), twoDigit(cards.size()), "문제", stage.getTitle(),
                        qrCodes.stagePuzzleUrl(game, stage), qrCodes.generateForStage(game, stage), "stage"));
            }
        }

        int itemCount = 0;
        for (GameItem item : items) {
            if (!item.isQrEnabled()) continue;
            itemCount++;
            cards.add(card(cards.size(), twoDigit(cards.size()), "단서·아이템", item.getName(),
                    qrCodes.itemClueUrl(game, item), qrCodes.generateForItem(game, item), "item"));
        }

        List<QrCard> immutableCards = List.copyOf(cards);
        List<List<QrCard>> pages = new ArrayList<>();
        for (int from = 0; from < immutableCards.size(); from += CARDS_PER_PAGE) {
            pages.add(List.copyOf(immutableCards.subList(from,
                    Math.min(from + CARDS_PER_PAGE, immutableCards.size()))));
        }
        return new QrKit(game.getTitle(), game.getSlug(), immutableCards, List.copyOf(pages),
                stageCount, itemCount);
    }

    public byte[] zip(QrKit kit) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (QrCard card : kit.cards()) {
                zip.putNextEntry(new ZipEntry(card.filename()));
                zip.write(card.png());
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("QR-목록.txt"));
            zip.write(readme(kit).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("QR ZIP 파일을 만들지 못했습니다.", e);
        }
    }

    public byte[] pdf(QrKit kit) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle(kit.title() + " QR 인쇄 키트");
            document.getDocumentInformation().setCreator("Findguni");
            for (List<QrCard> pageCards : kit.pages()) {
                BufferedImage pageImage = renderPage(pageCards);
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                PDImageXObject image = LosslessFactory.createFromImage(document, pageImage);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.drawImage(image, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("QR PDF 문서를 만들지 못했습니다.", e);
        }
    }

    private QrCard card(int number, String label, String type, String title, String targetUrl,
                        byte[] png, String filenameType) {
        String safeTitle = sanitizeFilename(title);
        String filename = "%02d-%s-%s.png".formatted(number, filenameType, safeTitle);
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
        return new QrCard(number, label, type, title, targetUrl, dataUrl, filename, png);
    }

    private BufferedImage renderPage(List<QrCard> cards) throws IOException {
        BufferedImage image = new BufferedImage(PAGE_WIDTH, PAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int cardWidth = (PAGE_WIDTH - PAGE_MARGIN * 2 - CARD_GAP) / 2;
            int cardHeight = (PAGE_HEIGHT - PAGE_MARGIN * 2 - CARD_GAP * 2) / 3;
            for (int index = 0; index < cards.size(); index++) {
                int column = index % 2;
                int row = index / 2;
                int x = PAGE_MARGIN + column * (cardWidth + CARD_GAP);
                int y = PAGE_MARGIN + row * (cardHeight + CARD_GAP);
                drawCard(graphics, cards.get(index), x, y, cardWidth, cardHeight);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private void drawCard(Graphics2D graphics, QrCard card, int x, int y, int width, int height)
            throws IOException {
        graphics.setColor(new Color(36, 42, 48));
        graphics.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{10f, 8f}, 0f));
        graphics.drawRect(x, y, width, height);
        graphics.setStroke(new BasicStroke(1f));

        Font labelFont = new Font(Font.DIALOG, Font.BOLD, 22);
        Font typeFont = new Font(Font.DIALOG, Font.PLAIN, 18);
        Font titleFont = new Font(Font.DIALOG, Font.BOLD, 25);
        Font noteFont = new Font(Font.DIALOG, Font.PLAIN, 17);

        graphics.setFont(labelFont);
        graphics.drawString("QR " + card.label(), x + 24, y + 36);
        graphics.setFont(typeFont);
        drawRight(graphics, card.type(), x + width - 24, y + 36);

        BufferedImage qr = ImageIO.read(new ByteArrayInputStream(card.png()));
        if (qr == null) throw new IOException("QR 이미지를 읽지 못했습니다.");
        int qrSize = Math.min(320, width - 96);
        int qrX = x + (width - qrSize) / 2;
        int qrY = y + 55;
        Object interpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(qr, qrX, qrY, qrSize, qrSize, null);
        if (interpolation != null) graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);

        graphics.setFont(titleFont);
        drawCentered(graphics, fit(graphics, card.title(), width - 56), x + width / 2, qrY + qrSize + 36);

        int operatorY = y + height - 94;
        graphics.setColor(new Color(130, 137, 143));
        graphics.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{5f, 5f}, 0f));
        graphics.drawLine(x + 20, operatorY, x + width - 20, operatorY);
        graphics.setStroke(new BasicStroke(1f));
        graphics.setColor(new Color(36, 42, 48));
        graphics.setFont(noteFont);
        graphics.drawString("설치 위치  __________________________", x + 24, operatorY + 31);
        graphics.drawString("스캔 확인  □", x + 24, operatorY + 62);
        drawRight(graphics, "운영자 메모", x + width - 24, operatorY + 62);
    }

    private void drawCentered(Graphics2D graphics, String text, int centerX, int baselineY) {
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(text, centerX - metrics.stringWidth(text) / 2, baselineY);
    }

    private void drawRight(Graphics2D graphics, String text, int rightX, int baselineY) {
        graphics.drawString(text, rightX - graphics.getFontMetrics().stringWidth(text), baselineY);
    }

    private String fit(Graphics2D graphics, String text, int maxWidth) {
        String value = text == null || text.isBlank() ? "이름 없는 QR" : text.trim();
        if (graphics.getFontMetrics().stringWidth(value) <= maxWidth) return value;
        int[] codePoints = value.codePoints().toArray();
        for (int length = codePoints.length - 1; length > 0; length--) {
            String candidate = new String(codePoints, 0, length) + "…";
            if (graphics.getFontMetrics().stringWidth(candidate) <= maxWidth) return candidate;
        }
        return "…";
    }

    private String readme(QrKit kit) {
        StringBuilder text = new StringBuilder("\uFEFF")
                .append(kit.title()).append(" QR 인쇄 키트\n")
                .append("PDF 문서는 A4 한 장당 6개 카드이며 점선을 따라 오려 사용하세요.\n\n");
        for (QrCard card : kit.cards()) {
            text.append("QR ").append(card.label()).append(" | ")
                    .append(card.type()).append(" | ").append(card.title()).append("\n")
                    .append(card.targetUrl()).append("\n")
                    .append("설치 위치: ______________________________\n\n");
        }
        return text.toString();
    }

    private String sanitizeFilename(String value) {
        String normalized = Normalizer.normalize(value == null ? "qr" : value, Normalizer.Form.NFKC)
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^[.-]+|[.-]+$", "");
        if (normalized.isBlank()) normalized = "qr";
        int codePointCount = normalized.codePointCount(0, normalized.length());
        if (codePointCount > 48) {
            normalized = normalized.substring(0, normalized.offsetByCodePoints(0, 48));
        }
        return normalized;
    }

    private String twoDigit(int number) {
        return "%02d".formatted(number);
    }

    public record QrCard(int number, String label, String type, String title, String targetUrl,
                         String imageDataUrl, String filename, byte[] png) {}

    public record QrKit(String title, String slug, List<QrCard> cards, List<List<QrCard>> pages,
                        int stageCount, int itemCount) {
        public int totalCount() { return cards.size(); }
        public int pageCount() { return pages.size(); }
    }
}
