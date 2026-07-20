(function () {
    'use strict';

    function ready(callback) {
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', callback, { once: true });
        else callback();
    }

    function reducedMotion() {
        return window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    }

    function clampSpeed(value) {
        var speed = Number(value);
        if (!Number.isFinite(speed)) return 32;
        return Math.max(10, Math.min(120, speed));
    }

    function timedEffect(scene, className) {
        scene.classList.add(className);
        window.setTimeout(function () { scene.classList.remove(className); }, 1500);
    }

    function typewriter(scene, text, speed) {
        var original = text.textContent || '';
        var index = 0;
        var timer = null;
        var content = scene.querySelector('.cinematic-scene__content') || scene;
        var skip = document.createElement('button');

        function finish(focusText) {
            if (timer !== null) window.clearTimeout(timer);
            text.textContent = original;
            text.removeAttribute('aria-busy');
            skip.hidden = true;
            if (focusText) {
                text.tabIndex = -1;
                text.focus();
            }
        }

        function writeNext() {
            index += 1;
            text.textContent = original.slice(0, index);
            if (index < original.length) timer = window.setTimeout(writeNext, speed);
            else finish(false);
        }

        skip.className = 'story-skip';
        skip.type = 'button';
        skip.textContent = '이야기 연출 건너뛰기';
        skip.addEventListener('click', function () { finish(true); });
        text.textContent = '';
        text.setAttribute('aria-busy', 'true');
        content.appendChild(skip);
        if (original.length) timer = window.setTimeout(writeNext, speed);
        else finish(false);
    }

    function initStoryScene(scene) {
        var text = scene.querySelector('[data-story-text]');
        var effect = String(scene.dataset.storyEffect || 'NONE').toUpperCase();
        if (scene.dataset.vignette === 'true') scene.classList.add('cinematic-scene--vignette');
        if (!text || reducedMotion()) return;

        if (effect === 'TYPEWRITER') typewriter(scene, text, clampSpeed(scene.dataset.storySpeed));
        else if (effect === 'FADE') timedEffect(scene, 'story-effect--fade');
        else if (effect === 'GLITCH') timedEffect(scene, 'story-effect--glitch');
        else if (effect === 'SHAKE') timedEffect(scene, 'story-effect--shake');
        else if (effect === 'FLICKER') timedEffect(scene, 'story-effect--flicker');
        else if (effect === 'SPOTLIGHT') timedEffect(scene, 'story-effect--spotlight');
    }

    ready(function () {
        document.querySelectorAll('[data-story-scene]').forEach(initStoryScene);
    });
}());
