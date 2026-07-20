(function () {
    'use strict';

    var PREFERENCE_KEY = 'findguni.audio.preferences.v1';

    function ready(callback) {
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', callback, { once: true });
        else callback();
    }

    function safeStorage(storage, action, key, value) {
        try {
            if (action === 'get') return storage.getItem(key);
            if (action === 'set') storage.setItem(key, value);
        } catch (error) {
            return null;
        }
        return null;
    }

    function preferences() {
        var saved = safeStorage(window.localStorage, 'get', PREFERENCE_KEY);
        if (!saved) return { bgmEnabled: true, sfxEnabled: true };
        try {
            var parsed = JSON.parse(saved);
            return { bgmEnabled: parsed.bgmEnabled !== false, sfxEnabled: parsed.sfxEnabled !== false };
        } catch (error) {
            return { bgmEnabled: true, sfxEnabled: true };
        }
    }

    function savePreferences(value) {
        safeStorage(window.localStorage, 'set', PREFERENCE_KEY, JSON.stringify(value));
    }

    function clampVolume(value, fallback) {
        var volume = Number(value);
        if (!Number.isFinite(volume)) return fallback;
        return Math.max(0, Math.min(1, volume));
    }

    function initController(controller) {
        var bgm = controller.querySelector('[data-bgm-track]');
        var sfx = controller.querySelector('[data-sfx-track]');
        var startButton = controller.querySelector('[data-audio-start]');
        var status = controller.querySelector('[data-audio-status]');
        var state = preferences();
        var slug = controller.dataset.gameSlug || 'game';
        var stageKey = controller.dataset.stageKey || '';
        var positionKey = 'findguni.audio.bgmPosition.' + slug;
        var sfxPlayedKey = stageKey ? 'findguni.audio.sfxPlayed.' + slug + '.' + stageKey : '';
        var sfxStarting = false;

        if (bgm) {
            bgm.volume = clampVolume(controller.dataset.bgmVolume, 0.55);
            bgm.loop = controller.dataset.bgmLoop === 'true';
        }
        if (sfx) sfx.volume = clampVolume(controller.dataset.sfxVolume, 0.8);

        function announce(message, kind) {
            if (!status) return;
            status.textContent = message;
            if (kind) status.dataset.state = kind;
            else status.removeAttribute('data-state');
        }

        function updateButtons() {
            controller.querySelectorAll('[data-audio-toggle]').forEach(function (button) {
                var isBgm = button.dataset.audioToggle === 'bgm';
                var enabled = isBgm ? state.bgmEnabled : state.sfxEnabled;
                button.setAttribute('aria-pressed', String(enabled));
                button.textContent = (isBgm ? 'BGM ' : '효과음 ') + (enabled ? '켜짐' : '꺼짐');
                button.classList.toggle('is-muted', !enabled);
            });
        }

        function showRecovery(message) {
            announce(message || '브라우저가 자동 재생을 막았습니다. 버튼을 눌러 사운드를 시작하세요.', 'blocked');
            if (startButton) startButton.hidden = false;
        }

        function play(track) {
            if (!track) return Promise.resolve(true);
            try {
                var result = track.play();
                return result && typeof result.then === 'function' ? result.then(function () { return true; }).catch(function () { return false; }) : Promise.resolve(true);
            } catch (error) {
                return Promise.resolve(false);
            }
        }

        function restoreBgmPosition() {
            if (!bgm) return;
            var saved = Number(safeStorage(window.sessionStorage, 'get', positionKey));
            if (!Number.isFinite(saved) || saved <= 0) return;
            try {
                if (!Number.isFinite(bgm.duration) || saved < bgm.duration) bgm.currentTime = saved;
            } catch (error) {
                return;
            }
        }

        function playEligibleTracks(userInitiated) {
            var attempts = [];
            var hasEligibleTrack = false;
            if (bgm && state.bgmEnabled) {
                hasEligibleTrack = true;
                attempts.push(play(bgm));
            }
            if (sfx && state.sfxEnabled && (!sfxPlayedKey || safeStorage(window.sessionStorage, 'get', sfxPlayedKey) !== '1') && !sfxStarting) {
                hasEligibleTrack = true;
                sfxStarting = true;
                attempts.push(play(sfx).then(function (played) {
                    sfxStarting = false;
                    if (played && sfxPlayedKey) safeStorage(window.sessionStorage, 'set', sfxPlayedKey, '1');
                    return played;
                }));
            }
            if (!hasEligibleTrack) return Promise.resolve(true);
            return Promise.all(attempts).then(function (outcomes) {
                var succeeded = outcomes.every(function (outcome) { return outcome; });
                if (succeeded) {
                    if (startButton) startButton.hidden = true;
                    announce(userInitiated ? '사운드를 시작했습니다.' : '', userInitiated ? 'success' : '');
                } else {
                    showRecovery(userInitiated ? '사운드를 시작하지 못했습니다. 기기의 음량과 브라우저 권한을 확인하세요.' : '브라우저가 자동 재생을 막았습니다. 버튼을 눌러 사운드를 시작하세요.');
                }
                return succeeded;
            });
        }

        if (bgm) {
            if (bgm.readyState >= 1) restoreBgmPosition();
            else bgm.addEventListener('loadedmetadata', restoreBgmPosition, { once: true });
        }

        controller.querySelectorAll('[data-audio-toggle]').forEach(function (button) {
            button.addEventListener('click', function () {
                var type = button.dataset.audioToggle;
                if (type === 'bgm') {
                    state.bgmEnabled = !state.bgmEnabled;
                    if (!state.bgmEnabled && bgm) bgm.pause();
                } else {
                    state.sfxEnabled = !state.sfxEnabled;
                    if (!state.sfxEnabled && sfx) sfx.pause();
                }
                savePreferences(state);
                updateButtons();
                if ((type === 'bgm' && state.bgmEnabled) || (type === 'sfx' && state.sfxEnabled)) playEligibleTracks(true);
                else announce((type === 'bgm' ? 'BGM' : '효과음') + '을 껐습니다.', 'muted');
            });
        });

        [bgm, sfx].forEach(function (track) {
            if (!track) return;
            track.addEventListener('error', function () {
                announce('오디오 파일을 불러오지 못했습니다. 네트워크 상태를 확인하세요.', 'error');
                if (startButton) startButton.hidden = false;
            });
        });

        if (startButton) startButton.addEventListener('click', function () { playEligibleTracks(true); });

        window.addEventListener('pagehide', function () {
            if (bgm) {
                if (Number.isFinite(bgm.currentTime)) safeStorage(window.sessionStorage, 'set', positionKey, String(bgm.currentTime));
                bgm.pause();
            }
            if (sfx) sfx.pause();
        }, { once: true });

        updateButtons();
        playEligibleTracks(false);
    }

    ready(function () {
        document.querySelectorAll('[data-audio-controller]').forEach(initController);
    });
}());
