package com.findguni.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioStorageServiceTest {

    @TempDir
    Path uploadDir;

    @Test
    void storesMp3OggAndWavByMagicUnderAudioRootWithUuidNames() throws Exception {
        AudioStorageService storage = new AudioStorageService(uploadDir.toString());

        String mp3Url = storage.storeBgm(file("../outside.exe", "application/octet-stream", mp3Bytes()));
        String oggUrl = storage.storeSfx(file("effect.bin", "text/plain", oggBytes()));
        String wavUrl = storage.storeSfx(file("effect.mp3", "audio/mpeg", wavBytes()));

        assertThat(mp3Url).matches("/uploads/audio/[0-9a-f-]{36}\\.mp3");
        assertThat(oggUrl).matches("/uploads/audio/[0-9a-f-]{36}\\.ogg");
        assertThat(wavUrl).matches("/uploads/audio/[0-9a-f-]{36}\\.wav");
        assertThat(mp3Url).doesNotContain("outside", "..", "exe");
        assertStoredInsideAudioRoot(mp3Url, mp3Bytes());
        assertStoredInsideAudioRoot(oggUrl, oggBytes());
        assertStoredInsideAudioRoot(wavUrl, wavBytes());
        assertThat(Files.list(uploadDir.resolve("audio"))).hasSize(3);
    }

    @Test
    void rejectsFakeAudioAndOversizedSfxWithoutLeavingFiles() throws Exception {
        AudioStorageService storage = new AudioStorageService(uploadDir.toString());

        assertThatThrownBy(() -> storage.storeBgm(file(
                "fake.mp3", "audio/mpeg", "not audio".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);

        byte[] oversized = new byte[(int) AudioStorageService.MAX_SFX_BYTES + 1];
        byte[] header = oggBytes();
        System.arraycopy(header, 0, oversized, 0, header.length);
        assertThatThrownBy(() -> storage.storeSfx(file(
                "too-large.ogg", "audio/ogg", oversized)))
                .isInstanceOf(IllegalArgumentException.class);

        Path audioRoot = uploadDir.resolve("audio");
        assertThat(Files.notExists(audioRoot) || Files.list(audioRoot).findAny().isEmpty()).isTrue();
    }

    @Test
    void bgmLimitIsLargerThanSfxLimit() {
        assertThat(AudioStorageService.MAX_BGM_BYTES).isEqualTo(30L * 1024 * 1024);
        assertThat(AudioStorageService.MAX_SFX_BYTES).isEqualTo(8L * 1024 * 1024);
        assertThat(AudioStorageService.MAX_BGM_BYTES).isGreaterThan(AudioStorageService.MAX_SFX_BYTES);
    }

    private void assertStoredInsideAudioRoot(String url, byte[] expected) throws Exception {
        Path audioRoot = uploadDir.resolve("audio").toAbsolutePath().normalize();
        Path stored = uploadDir.resolve(url.substring("/uploads/".length())).toAbsolutePath().normalize();
        assertThat(stored.getParent()).isEqualTo(audioRoot);
        assertThat(stored).isRegularFile();
        assertThat(Files.readAllBytes(stored)).isEqualTo(expected);
    }

    private MockMultipartFile file(String originalName, String contentType, byte[] bytes) {
        return new MockMultipartFile("audio", originalName, contentType, bytes);
    }

    private byte[] mp3Bytes() {
        return new byte[]{'I', 'D', '3', 4, 0, 0, 0, 0, 0, 2, 0x11, 0x22};
    }

    private byte[] oggBytes() {
        return new byte[]{'O', 'g', 'g', 'S', 0, 2, 0, 0, 0, 0, 0, 0};
    }

    private byte[] wavBytes() {
        return new byte[]{'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'A', 'V', 'E'};
    }
}
