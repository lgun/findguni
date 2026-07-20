package com.findguni.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class AudioStorageService {
    public static final long MAX_BGM_BYTES = 30L * 1024 * 1024;
    public static final long MAX_SFX_BYTES = 8L * 1024 * 1024;
    private final Path audioRoot;

    public AudioStorageService(@Value("${findguni.upload-dir:./uploads}") String uploadDir) {
        Path uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.audioRoot = uploadRoot.resolve("audio").normalize();
        if (!audioRoot.getParent().equals(uploadRoot)) {
            throw new IllegalArgumentException("안전하지 않은 오디오 저장 경로입니다.");
        }
    }

    public String storeBgm(MultipartFile file) {
        return store(file, MAX_BGM_BYTES, "배경 음악");
    }

    public String storeSfx(MultipartFile file) {
        return store(file, MAX_SFX_BYTES, "효과음");
    }

    private String store(MultipartFile file, long maxBytes, String label) {
        if (file == null || file.isEmpty()) return null;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(label + " 파일 크기를 초과했습니다.");
        }
        AudioFormat format = detect(file);
        Path destination = audioRoot.resolve(UUID.randomUUID() + "." + format.extension).normalize();
        if (!destination.getParent().equals(audioRoot)) {
            throw new IllegalStateException("안전하지 않은 오디오 저장 경로입니다.");
        }
        try {
            Files.createDirectories(audioRoot);
            copyBounded(file, destination, maxBytes);
            return "/uploads/audio/" + destination.getFileName();
        } catch (IllegalArgumentException e) {
            deleteQuietly(destination);
            throw e;
        } catch (IOException e) {
            deleteQuietly(destination);
            throw new IllegalStateException(label + " 파일을 저장하지 못했습니다.", e);
        }
    }

    private AudioFormat detect(MultipartFile file) {
        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(12);
        } catch (IOException e) {
            throw new IllegalArgumentException("오디오 파일을 읽을 수 없습니다.", e);
        }
        if (startsWith(header, "ID3") || isMpegFrame(header)) return AudioFormat.MP3;
        if (startsWith(header, "OggS")) return AudioFormat.OGG;
        if (startsWith(header, "RIFF") && matchesAt(header, 8, "WAVE")) return AudioFormat.WAV;
        throw new IllegalArgumentException("MP3, OGG, WAV 오디오 파일만 업로드할 수 있습니다.");
    }

    private void copyBounded(MultipartFile file, Path destination, long maxBytes) throws IOException {
        try (InputStream input = file.getInputStream();
             OutputStream output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[16 * 1024];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IllegalArgumentException("오디오 파일 크기를 초과했습니다.");
                output.write(buffer, 0, read);
            }
        }
    }

    private boolean startsWith(byte[] bytes, String magic) {
        return matchesAt(bytes, 0, magic);
    }

    private boolean matchesAt(byte[] bytes, int offset, String magic) {
        if (bytes.length < offset + magic.length()) return false;
        for (int i = 0; i < magic.length(); i++) {
            if ((bytes[offset + i] & 0xff) != magic.charAt(i)) return false;
        }
        return true;
    }

    private boolean isMpegFrame(byte[] bytes) {
        if (bytes.length < 4) return false;
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        int third = bytes[2] & 0xff;
        return first == 0xff && (second & 0xe0) == 0xe0
                && (second & 0x18) != 0x08 && (second & 0x06) != 0
                && (third & 0xf0) != 0 && (third & 0xf0) != 0xf0
                && (third & 0x0c) != 0x0c;
    }

    private void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) {}
    }

    private enum AudioFormat {
        MP3("mp3"), OGG("ogg"), WAV("wav");

        private final String extension;

        AudioFormat(String extension) {
            this.extension = extension;
        }
    }
}
