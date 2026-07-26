(function () {
    'use strict';

    function ready(callback) {
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', callback, { once: true });
        else callback();
    }

    ready(function () {
        var scanner = document.querySelector('[data-scanner]');
        if (!scanner) return;
        var video = scanner.querySelector('[data-scanner-video]');
        var canvas = scanner.querySelector('[data-scanner-canvas]');
        var placeholder = scanner.querySelector('[data-scanner-placeholder]');
        var status = scanner.querySelector('[data-scanner-status]');
        var startButton = scanner.querySelector('[data-camera-start]');
        var stopButton = scanner.querySelector('[data-camera-stop]');
        var fileInput = scanner.querySelector('[data-scanner-file]');
        var endpoint = scanner.dataset.scanEndpoint;
        var csrfMeta = document.querySelector('meta[name="_csrf"]');
        var csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
        var stream = null;
        var timer = null;
        var detecting = false;
        var submitting = false;
        var detector = null;

        function message(text, kind) {
            status.textContent = text;
            status.dataset.state = kind || 'info';
        }

        function stop() {
            if (timer) window.clearInterval(timer);
            timer = null;
            detecting = false;
            if (stream) stream.getTracks().forEach(function (track) { track.stop(); });
            stream = null;
            video.pause();
            video.srcObject = null;
            video.hidden = true;
            placeholder.hidden = false;
            stopButton.hidden = true;
            startButton.hidden = false;
        }

        async function responseMessage(response) {
            var contentType = response.headers.get('content-type') || '';
            if (contentType.indexOf('application/json') >= 0) {
                var data = await response.json();
                var accepted = typeof data.accepted === 'boolean' ? data.accepted : data.success !== false;
                return {
                    success: accepted,
                    message: data.message || '',
                    found: data.found !== false,
                    redirectUrl: data.redirectUrl || null,
                    targetType: data.targetType || null,
                    item: data.item || null
                };
            }
            return { success: response.ok, message: '' };
        }

        function showAcquiredItem(item) {
            if (!item || !item.stableKey) return;
            document.querySelectorAll('[data-inventory-list]').forEach(function (list) {
                if (list.querySelector('[data-inventory-item="' + CSS.escape(item.stableKey) + '"]')) return;
                var card = document.createElement('li');
                card.className = 'tool-item-card';
                card.dataset.inventoryItem = item.stableKey;
                var media = document.createElement('div');
                media.className = 'tool-item-card__media';
                if (item.imageUrl) {
                    var image = document.createElement('img');
                    image.src = item.imageUrl;
                    image.alt = (item.name || '아이템') + ' 이미지';
                    media.appendChild(image);
                } else {
                    var emoji = document.createElement('span');
                    emoji.textContent = item.emoji || '◇';
                    media.appendChild(emoji);
                }
                var content = document.createElement('div');
                var name = document.createElement('strong');
                name.textContent = item.name || '새 아이템';
                var description = document.createElement('p');
                description.textContent = item.clueText || item.description || '';
                content.appendChild(name);
                content.appendChild(description);
                card.appendChild(media);
                card.appendChild(content);
                list.appendChild(card);
                list.hidden = false;
            });
            document.querySelectorAll('[data-inventory-empty]').forEach(function (empty) { empty.hidden = true; });
            document.querySelectorAll('[data-inventory-count]').forEach(function (count) {
                var list = document.querySelector('[data-inventory-list]');
                count.textContent = (list ? list.querySelectorAll('[data-inventory-item]').length : 0) + '개';
            });
        }

        async function submit(formData) {
            if (submitting) return;
            submitting = true;
            message('QR 단서를 확인하고 있습니다…');
            try {
                var headers = {};
                if (csrfMeta && csrfHeaderMeta) headers[csrfHeaderMeta.content] = csrfMeta.content;
                var response = await fetch(endpoint, { method: 'POST', body: formData, headers: headers, credentials: 'same-origin', redirect: 'follow' });
                var result = await responseMessage(response);
                stop();
                message(result.message || '단서를 확인했습니다. 잠시 뒤 페이지로 이동합니다.', 'success');
                if (result.targetType === 'CLUE' && result.item) showAcquiredItem(result.item);
                if (result.redirectUrl) {
                    window.setTimeout(function () { window.location.assign(result.redirectUrl); },
                        result.targetType === 'CLUE' && result.item ? 450 : 0);
                    return;
                }
                if (!response.ok || !result.success) {
                    throw new Error(result.message || '이 게임에서 사용할 수 없는 QR입니다.');
                }
                window.setTimeout(function () { window.location.reload(); }, 650);
            } catch (error) {
                message(error && error.message ? error.message : 'QR을 확인하지 못했습니다. 다시 시도해 주세요.', 'error');
            } finally {
                submitting = false;
            }
        }

        function submitPayload(payload) {
            if (!payload || submitting) return;
            var form = new FormData();
            form.append('payload', payload);
            submit(form);
        }

        function drawFrame(callback) {
            if (!video.videoWidth || !video.videoHeight || submitting) return;
            var width = Math.min(640, video.videoWidth);
            var height = Math.round(video.videoHeight * width / video.videoWidth);
            canvas.width = width;
            canvas.height = height;
            var context = canvas.getContext('2d', { alpha: false });
            context.drawImage(video, 0, 0, width, height);
            canvas.toBlob(function (blob) { if (blob) callback(blob); }, 'image/jpeg', 0.8);
        }

        async function detectLocally() {
            if (!detector || detecting || submitting || video.readyState < 2) return;
            detecting = true;
            try {
                var results = await detector.detect(video);
                if (results && results.length && results[0].rawValue) submitPayload(results[0].rawValue);
            } catch (error) {
                message('QR을 화면 중앙에 맞추고 잠시 멈춰 주세요.');
            } finally {
                detecting = false;
            }
        }

        function submitServerFrame() {
            drawFrame(function (blob) {
                var form = new FormData();
                form.append('frame', blob, 'qr-frame.jpg');
                submit(form);
            });
        }

        async function start() {
            if (stream || submitting) return;
            if (!window.isSecureContext) {
                message('카메라는 HTTPS 보안 연결에서만 사용할 수 있습니다. 아래의 사진 촬영을 이용해 주세요.', 'error');
                return;
            }
            if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                message('이 브라우저에서는 실시간 카메라를 사용할 수 없습니다. 아래에서 QR 사진을 선택해 주세요.', 'error');
                return;
            }
            message('카메라 권한을 확인하고 있습니다…');
            try {
                stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false });
                video.srcObject = stream;
                await video.play();
                video.hidden = false;
                placeholder.hidden = true;
                startButton.hidden = true;
                stopButton.hidden = false;
                if ('BarcodeDetector' in window) {
                    try { detector = new window.BarcodeDetector({ formats: ['qr_code'] }); }
                    catch (error) { detector = null; }
                }
                if (detector) {
                    message('QR을 화면 안에 맞춰 주세요. 브라우저에서 직접 인식합니다.');
                    timer = window.setInterval(detectLocally, 350);
                } else {
                    message('QR을 화면 안에 맞춰 주세요. 서버가 안전하게 프레임을 확인합니다.');
                    timer = window.setInterval(submitServerFrame, 1200);
                }
            } catch (error) {
                stop();
                if (error && (error.name === 'NotAllowedError' || error.name === 'SecurityError')) message('카메라 권한이 거부되었습니다. 브라우저 설정에서 허용하거나 QR 사진을 선택해 주세요.', 'error');
                else if (error && (error.name === 'NotFoundError' || error.name === 'DevicesNotFoundError')) message('사용 가능한 카메라를 찾지 못했습니다. 저장된 QR 사진을 선택해 주세요.', 'error');
                else message('카메라를 시작하지 못했습니다. 아래의 사진 촬영 기능을 이용해 주세요.', 'error');
            }
        }

        startButton.addEventListener('click', start);
        stopButton.addEventListener('click', function () { stop(); message('카메라를 껐습니다.'); });
        fileInput.addEventListener('change', function () {
            var file = fileInput.files && fileInput.files[0];
            if (!file) return;
            if (!file.type.startsWith('image/')) { message('이미지 파일을 선택해 주세요.', 'error'); return; }
            var form = new FormData();
            form.append('frame', file, file.name || 'qr-photo.jpg');
            submit(form);
            fileInput.value = '';
        });
        document.addEventListener('findguni:scanner-open', start);
        document.addEventListener('findguni:scanner-close', stop);
        window.addEventListener('pagehide', stop);
        document.addEventListener('visibilitychange', function () { if (document.hidden) stop(); });
    });
}());
