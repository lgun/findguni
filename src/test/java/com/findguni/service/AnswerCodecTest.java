package com.findguni.service;

import com.findguni.model.PuzzleType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerCodecTest {

    private final AnswerCodec codec = new AnswerCodec("test-answer-secret-with-enough-length");

    @Test
    void normalizesVisualLockInputsBeforeComparingAnswers() {
        assertThat(codec.normalize(PuzzleType.NUMBER_LOCK, " 12-34 ")).isEqualTo("1234");
        assertThat(codec.normalize(PuzzleType.ALPHABET_LOCK, " a b-c ")).isEqualTo("ABC");
        assertThat(codec.normalize(PuzzleType.DIRECTION_LOCK, "↑ 오른쪽 D 왼쪽"))
                .isEqualTo("UP,RIGHT,DOWN,LEFT");
        assertThat(codec.normalize(PuzzleType.COLOR_LOCK, "빨강 > G > blue"))
                .isEqualTo("RED,GREEN,BLUE");
    }

    @Test
    void digestMatchesEquivalentInputButRejectsDifferentAnswer() {
        String digest = codec.digest(PuzzleType.DIRECTION_LOCK, "UP,RIGHT,DOWN");

        assertThat(codec.matches(PuzzleType.DIRECTION_LOCK, "↑ → 아래", digest)).isTrue();
        assertThat(codec.matches(PuzzleType.DIRECTION_LOCK, "↑ ← 아래", digest)).isFalse();
    }
}
