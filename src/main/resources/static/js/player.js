(function () {
    'use strict';

    function ready(callback) {
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', callback, { once: true });
        else callback();
    }

    function setAnswer(shell, value) {
        var answer = shell.querySelector('input[name="answer"]');
        if (answer) answer.value = value;
    }

    function announce(shell, message) {
        var status = shell.querySelector('[data-puzzle-status]');
        if (status) status.textContent = message;
    }

    function initWheels(shell) {
        var wheels = Array.prototype.slice.call(shell.querySelectorAll('[data-wheel]'));
        if (!wheels.length) return;
        var alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('');
        var digits = '0123456789'.split('');

        function valuesFor(wheel) { return wheel.dataset.wheel === 'alphabet' ? alphabet : digits; }
        function updateAnswer() {
            setAnswer(shell, wheels.map(function (wheel) { return wheel.dataset.value; }).join(''));
        }
        function render(wheel) {
            var values = valuesFor(wheel);
            var index = values.indexOf(wheel.dataset.value);
            var previous = wheel.querySelector('[data-wheel-prev]');
            var current = wheel.querySelector('[data-wheel-value]');
            var next = wheel.querySelector('[data-wheel-next]');
            if (previous) previous.textContent = values[(index - 1 + values.length) % values.length];
            if (current) current.textContent = values[index];
            if (next) next.textContent = values[(index + 1) % values.length];
            wheel.setAttribute('aria-valuetext', values[index]);
            wheel.setAttribute('aria-valuenow', String(index));
        }
        function tactileTick(wheel, amount) {
            wheel.style.setProperty('--dial-nudge', amount > 0 ? '.28rem' : '-.28rem');
            wheel.classList.remove('is-turning');
            void wheel.offsetWidth;
            wheel.classList.add('is-turning');
            window.setTimeout(function () { wheel.classList.remove('is-turning'); }, 140);
            if (navigator.vibrate) navigator.vibrate(7);
        }
        function move(wheel, amount) {
            var values = valuesFor(wheel);
            var index = values.indexOf(wheel.dataset.value);
            index = (index + amount + values.length) % values.length;
            wheel.dataset.value = values[index];
            render(wheel);
            tactileTick(wheel, amount);
            updateAnswer();
        }

        wheels.forEach(function (wheel) {
            var startY = null;
            var pointerId = null;
            var lastWheelAt = 0;
            wheel.dataset.value = valuesFor(wheel)[0];
            render(wheel);
            wheel.querySelectorAll('[data-wheel-move]').forEach(function (button) {
                button.addEventListener('click', function () { move(wheel, Number(button.dataset.wheelMove)); });
            });
            wheel.addEventListener('keydown', function (event) {
                if (event.key === 'ArrowUp') { event.preventDefault(); move(wheel, 1); }
                if (event.key === 'ArrowDown') { event.preventDefault(); move(wheel, -1); }
            });
            wheel.addEventListener('wheel', function (event) {
                event.preventDefault();
                if (Date.now() - lastWheelAt < 85) return;
                lastWheelAt = Date.now();
                move(wheel, event.deltaY < 0 ? 1 : -1);
            }, { passive: false });
            wheel.addEventListener('pointerdown', function (event) {
                if (event.target.closest('[data-wheel-move]')) return;
                startY = event.clientY;
                pointerId = event.pointerId;
                wheel.setPointerCapture(pointerId);
            });
            wheel.addEventListener('pointerup', function (event) {
                if (startY === null || event.pointerId !== pointerId) return;
                var delta = event.clientY - startY;
                if (Math.abs(delta) > 18) move(wheel, delta < 0 ? 1 : -1);
                startY = null;
                pointerId = null;
            });
            wheel.addEventListener('pointercancel', function () { startY = null; pointerId = null; });
        });
        updateAnswer();
        announce(shell, '다이얼을 위아래로 밀거나 화살표 키로 한 칸씩 돌리세요.');
    }

    function initKeypad(shell) {
        var display = shell.querySelector('[data-keypad-display]');
        if (!display) return;
        var length = Number(shell.dataset.lockLength || 4);
        var value = '';
        function update() {
            display.textContent = value.padEnd(length, '·');
            setAnswer(shell, value);
            display.setAttribute('aria-label', value ? '입력값 ' + value : '입력값 없음');
        }
        function input(key) {
            if (key === 'clear') value = '';
            else if (key === 'delete') value = value.slice(0, -1);
            else if (/^\d$/.test(key) && value.length < length) value += key;
            update();
        }
        shell.querySelectorAll('[data-keypad-key]').forEach(function (button) {
            button.addEventListener('click', function () {
                button.classList.add('is-pressed');
                window.setTimeout(function () { button.classList.remove('is-pressed'); }, 110);
                if (navigator.vibrate) navigator.vibrate(8);
                input(button.dataset.keypadKey);
            });
        });
        shell.addEventListener('keydown', function (event) {
            if (/^\d$/.test(event.key)) { event.preventDefault(); input(event.key); }
            if (event.key === 'Backspace') { event.preventDefault(); input('delete'); }
            if (event.key === 'Escape') { event.preventDefault(); input('clear'); }
        });
        update();
    }

    function tokenLabel(value) {
        var labels = { UP: '↑', RIGHT: '→', DOWN: '↓', LEFT: '←', RED: '빨강', BLUE: '파랑', GREEN: '초록', YELLOW: '노랑', PURPLE: '보라', ORANGE: '주황' };
        return labels[value] || value;
    }

    function renderSequence(shell, sequence) {
        var output = shell.querySelector('[data-sequence]');
        if (!output) return;
        while (output.firstChild) output.removeChild(output.firstChild);
        if (!sequence.length) {
            var empty = document.createElement('span');
            empty.className = 'muted';
            empty.textContent = '아직 입력하지 않았어요';
            output.appendChild(empty);
            return;
        }
        sequence.forEach(function (value) {
            var token = document.createElement('span');
            token.className = 'sequence-token';
            token.textContent = tokenLabel(value);
            output.appendChild(token);
        });
    }

    function initSequence(shell, selector, attribute) {
        var keys = shell.querySelectorAll(selector);
        if (!keys.length) return;
        var limit = Number(shell.dataset.lockLength || 4);
        var sequence = [];
        function add(value) {
            if (sequence.length >= limit) sequence = [];
            sequence.push(value);
            renderSequence(shell, sequence);
            setAnswer(shell, sequence.join(','));
        }
        keys.forEach(function (button) {
            button.addEventListener('click', function () { add(button.dataset[attribute]); });
        });
        var clear = shell.querySelector('[data-sequence-clear]');
        if (clear) clear.addEventListener('click', function () { sequence = []; renderSequence(shell, sequence); setAnswer(shell, ''); });
        renderSequence(shell, sequence);
        return add;
    }

    function initDirections(shell) {
        var add = initSequence(shell, '[data-direction]', 'direction');
        if (!add) return;
        var pad = shell.querySelector('[data-direction-pad]');
        var start = null;
        pad.addEventListener('touchstart', function (event) {
            var touch = event.changedTouches[0];
            start = { x: touch.clientX, y: touch.clientY };
        }, { passive: true });
        pad.addEventListener('touchend', function (event) {
            if (!start) return;
            var touch = event.changedTouches[0];
            var dx = touch.clientX - start.x;
            var dy = touch.clientY - start.y;
            if (Math.max(Math.abs(dx), Math.abs(dy)) > 28) add(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'RIGHT' : 'LEFT') : (dy > 0 ? 'DOWN' : 'UP'));
            start = null;
        }, { passive: true });
        shell.addEventListener('keydown', function (event) {
            var map = { ArrowUp: 'UP', w: 'UP', ArrowRight: 'RIGHT', d: 'RIGHT', ArrowDown: 'DOWN', s: 'DOWN', ArrowLeft: 'LEFT', a: 'LEFT' };
            if (map[event.key]) { event.preventDefault(); add(map[event.key]); }
        });
    }

    function initChoices(shell) {
        shell.querySelectorAll('[data-choice]').forEach(function (button) {
            button.addEventListener('click', function () {
                setAnswer(shell, button.dataset.choice);
            });
        });
    }

    function initTextAnswer(shell) {
        var source = shell.querySelector('[data-answer-source]');
        if (!source) return;
        function update() { setAnswer(shell, source.value); }
        source.addEventListener('input', update);
        var pasteButton = shell.querySelector('[data-paste-answer]');
        if (pasteButton) pasteButton.addEventListener('click', function () {
            if (navigator.clipboard && window.isSecureContext && navigator.clipboard.readText) {
                navigator.clipboard.readText().then(function (value) {
                    source.value = value;
                    update();
                    source.focus();
                    announce(shell, '클립보드의 비밀번호를 도어락에 붙여넣었습니다.');
                }).catch(function () {
                    source.focus();
                    announce(shell, '입력 화면을 길게 눌러 직접 붙여넣어 주세요.');
                });
            } else {
                source.focus();
                announce(shell, '입력 화면을 길게 눌러 직접 붙여넣어 주세요.');
            }
        });
        update();
    }

    function initCopyableItems() {
        document.querySelectorAll('[data-copy-text]').forEach(function (button) {
            button.addEventListener('click', function () {
                var value = button.dataset.copyValue || '';
                var status = button.parentElement.querySelector('[data-copy-status]');
                function done() {
                    if (status) status.textContent = '복사했어! 중간에 흘리지 마.';
                }
                if (navigator.clipboard && window.isSecureContext) {
                    navigator.clipboard.writeText(value).then(done).catch(function () { fallbackCopy(value, done); });
                } else {
                    fallbackCopy(value, done);
                }
            });
        });
    }

    function fallbackCopy(value, onSuccess) {
        var field = document.createElement('textarea');
        field.value = value;
        field.setAttribute('readonly', '');
        field.style.position = 'fixed';
        field.style.opacity = '0';
        document.body.appendChild(field);
        field.select();
        try {
            if (document.execCommand('copy')) onSuccess();
        } finally {
            document.body.removeChild(field);
        }
    }

    function initToolDrawer() {
        var drawer = document.querySelector('[data-tool-drawer]');
        var overlay = document.querySelector('[data-tool-overlay]');
        if (!drawer || !overlay) return;
        var previousFocus = null;
        var activeTool = null;

        function tabs() { return Array.prototype.slice.call(drawer.querySelectorAll('[data-tool-tab]')); }
        function panels() { return Array.prototype.slice.call(drawer.querySelectorAll('[data-tool-panel]')); }
        function focusable() {
            return Array.prototype.slice.call(drawer.querySelectorAll('button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])'))
                .filter(function (node) { return !node.hidden && node.offsetParent !== null; });
        }
        function activate(name) {
            var found = false;
            tabs().forEach(function (tab) {
                var selected = tab.dataset.toolTab === name;
                tab.setAttribute('aria-selected', String(selected));
                tab.tabIndex = selected ? 0 : -1;
                if (selected) found = true;
            });
            if (!found) name = 'inventory';
            panels().forEach(function (panel) { panel.hidden = panel.dataset.toolPanel !== name; });
            if (activeTool === 'scanner' && name !== 'scanner') document.dispatchEvent(new CustomEvent('findguni:scanner-close'));
            activeTool = name;
            if (name === 'scanner') document.dispatchEvent(new CustomEvent('findguni:scanner-open'));
        }
        function open(name, trigger) {
            previousFocus = trigger || document.activeElement;
            drawer.hidden = false;
            overlay.hidden = false;
            document.body.classList.add('tool-drawer-open');
            activate(name);
            window.requestAnimationFrame(function () {
                drawer.classList.add('is-open');
                overlay.classList.add('is-open');
                var close = drawer.querySelector('[data-tool-close]');
                if (close) close.focus();
            });
        }
        function close() {
            if (activeTool === 'scanner') document.dispatchEvent(new CustomEvent('findguni:scanner-close'));
            activeTool = null;
            drawer.classList.remove('is-open');
            overlay.classList.remove('is-open');
            drawer.hidden = true;
            overlay.hidden = true;
            document.body.classList.remove('tool-drawer-open');
            if (previousFocus && typeof previousFocus.focus === 'function') previousFocus.focus();
        }

        document.querySelectorAll('[data-tool-open]').forEach(function (button) {
            button.addEventListener('click', function () { open(button.dataset.toolOpen, button); });
        });
        tabs().forEach(function (tab) {
            tab.addEventListener('click', function () { activate(tab.dataset.toolTab); });
            tab.addEventListener('keydown', function (event) {
                var allTabs = tabs();
                var index = allTabs.indexOf(tab);
                var next = null;
                if (event.key === 'ArrowRight') next = (index + 1) % allTabs.length;
                if (event.key === 'ArrowLeft') next = (index - 1 + allTabs.length) % allTabs.length;
                if (next !== null) {
                    event.preventDefault();
                    activate(allTabs[next].dataset.toolTab);
                    allTabs[next].focus();
                }
            });
        });
        var closeButton = drawer.querySelector('[data-tool-close]');
        if (closeButton) closeButton.addEventListener('click', close);
        overlay.addEventListener('click', close);
        drawer.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') { event.preventDefault(); close(); return; }
            if (event.key !== 'Tab') return;
            var nodes = focusable();
            if (!nodes.length) return;
            var first = nodes[0];
            var last = nodes[nodes.length - 1];
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
        });
    }

    function initHintCooldown() {
        var button = document.querySelector('[data-hint-wait-seconds]');
        if (!button) return;
        var initialSeconds = Number(button.dataset.hintWaitSeconds || 0);
        if (initialSeconds <= 0) return;
        var readyAt = Date.now() + initialSeconds * 1000;
        var status = document.querySelector('[data-hint-policy-status]');
        var readyLabel = button.dataset.hintReadyLabel || '힌트 보기';

        function update() {
            var seconds = Math.max(0, Math.ceil((readyAt - Date.now()) / 1000));
            if (seconds === 0) {
                button.disabled = false;
                button.textContent = readyLabel;
                if (status) status.textContent = '지금 다음 힌트를 볼 수 있습니다.';
                return false;
            }
            button.disabled = true;
            button.textContent = seconds + '초 후 사용';
            if (status) status.textContent = seconds + '초 후 다음 힌트를 볼 수 있습니다.';
            return true;
        }

        if (!update()) return;
        var timer = window.setInterval(function () {
            if (!update()) window.clearInterval(timer);
        }, 250);
    }

    ready(function () {
        document.querySelectorAll('[data-puzzle]').forEach(function (shell) {
            initWheels(shell);
            initKeypad(shell);
            initDirections(shell);
            initSequence(shell, '[data-color]', 'color');
            initChoices(shell);
            initTextAnswer(shell);
        });
        initCopyableItems();
        initToolDrawer();
        initHintCooldown();
    });
}());
