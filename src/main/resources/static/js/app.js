(function () {
    'use strict';

    function onReady(callback) {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', callback, { once: true });
        } else {
            callback();
        }
    }

    function initNavigation() {
        document.querySelectorAll('[data-nav-toggle]').forEach(function (toggle) {
            var target = document.getElementById(toggle.getAttribute('aria-controls'));
            if (!target) return;
            toggle.addEventListener('click', function () {
                var open = toggle.getAttribute('aria-expanded') === 'true';
                toggle.setAttribute('aria-expanded', String(!open));
                target.classList.toggle('is-open', !open);
            });
        });
    }

    function initDismissibleAlerts() {
        document.querySelectorAll('[data-dismiss]').forEach(function (button) {
            button.addEventListener('click', function () {
                var alert = button.closest('.alert');
                if (alert) alert.remove();
            });
        });
    }

    function initConfirmations() {
        document.querySelectorAll('[data-confirm]').forEach(function (control) {
            control.addEventListener('click', function (event) {
                if (!window.confirm(control.dataset.confirm)) event.preventDefault();
            });
        });
    }

    function initPrintButtons() {
        document.querySelectorAll('[data-print-page]').forEach(function (button) {
            button.addEventListener('click', function () { window.print(); });
        });
    }

    function initPasswordToggles() {
        document.querySelectorAll('[data-password-toggle]').forEach(function (button) {
            var input = document.getElementById(button.getAttribute('aria-controls'));
            if (!input) return;
            button.addEventListener('click', function () {
                var visible = input.type === 'text';
                input.type = visible ? 'password' : 'text';
                button.textContent = visible ? '보기' : '숨김';
                button.setAttribute('aria-pressed', String(!visible));
            });
        });
    }

    function initBuilderTabs() {
        var tabList = document.querySelector('[data-builder-tabs]');
        if (!tabList) return;
        var tabs = Array.prototype.slice.call(tabList.querySelectorAll('[role="tab"]'));
        var panels = tabs.map(function (tab) {
            return document.getElementById(tab.getAttribute('aria-controls'));
        });

        function activate(index, focus) {
            tabs.forEach(function (tab, tabIndex) {
                var active = tabIndex === index;
                tab.setAttribute('aria-selected', String(active));
                tab.tabIndex = active ? 0 : -1;
                if (panels[tabIndex]) panels[tabIndex].hidden = !active;
            });
            if (focus) tabs[index].focus();
            var key = tabs[index].dataset.tab;
            var layout = document.querySelector('.builder-layout');
            if (layout && key) layout.dataset.activePanel = key;
            if (key && window.history.replaceState) {
                window.history.replaceState(null, '', '#' + key);
            }
        }

        tabs.forEach(function (tab, index) {
            tab.addEventListener('click', function () { activate(index, false); });
            tab.addEventListener('keydown', function (event) {
                var next = null;
                if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
                if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
                if (next !== null) {
                    event.preventDefault();
                    activate(next, true);
                }
            });
        });

        var requestedTab = new URLSearchParams(window.location.search).get('tab');
        var initial = tabs.findIndex(function (tab) {
            return tab.dataset.tab === requestedTab || '#' + tab.dataset.tab === window.location.hash;
        });
        activate(initial >= 0 ? initial : 0, false);
    }

    function initStageWorkspaces() {
        document.querySelectorAll('[data-stage-workspace]').forEach(function (workspace) {
            var controls = Array.prototype.slice.call(workspace.querySelectorAll('[data-stage-select]'));
            var panels = Array.prototype.slice.call(workspace.querySelectorAll('[data-stage-panel]'));
            if (!controls.length || !panels.length) return;

            function activate(stageId, focusEditor, updateAddress) {
                var matched = panels.some(function (panel) { return panel.dataset.stageId === stageId; });
                if (!matched) stageId = controls[0].dataset.stageId;
                controls.forEach(function (control) {
                    var active = control.dataset.stageId === stageId;
                    control.classList.toggle('is-active', active);
                    control.setAttribute('aria-current', active ? 'true' : 'false');
                });
                panels.forEach(function (panel) { panel.open = panel.dataset.stageId === stageId; });

                if (updateAddress && window.history.replaceState) {
                    var url = new URL(window.location.href);
                    url.searchParams.set('tab', 'stages');
                    url.searchParams.set('edit', stageId);
                    url.hash = 'stages';
                    window.history.replaceState(null, '', url.pathname + url.search + url.hash);
                }
                if (focusEditor && window.matchMedia('(max-width: 899px)').matches) {
                    var selectedPanel = panels.find(function (panel) { return panel.dataset.stageId === stageId; });
                    if (selectedPanel) window.requestAnimationFrame(function () {
                        selectedPanel.scrollIntoView({ block: 'start', behavior: 'smooth' });
                    });
                }
            }

            controls.forEach(function (control, index) {
                control.addEventListener('click', function (event) {
                    event.preventDefault();
                    activate(control.dataset.stageId, true, true);
                });
                control.addEventListener('keydown', function (event) {
                    var next = null;
                    if (event.key === 'ArrowDown' || event.key === 'ArrowRight') next = (index + 1) % controls.length;
                    if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') next = (index - 1 + controls.length) % controls.length;
                    if (next !== null) {
                        event.preventDefault();
                        controls[next].focus();
                        activate(controls[next].dataset.stageId, false, true);
                    }
                });
            });

            var requested = new URLSearchParams(window.location.search).get('edit');
            var serverSelected = panels.find(function (panel) { return panel.open; });
            activate(requested || (serverSelected && serverSelected.dataset.stageId) || controls[0].dataset.stageId, false, false);
        });
    }

    function readHex(value) {
        var match = String(value || '').trim().match(/^#([0-9a-fA-F]{6})$/);
        return match ? '#' + match[1].toUpperCase() : null;
    }

    function contrastRatio(first, second) {
        function luminance(hex) {
            var channels = [1, 3, 5].map(function (index) {
                var value = parseInt(hex.slice(index, index + 2), 16) / 255;
                return value <= .03928 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
            });
            return .2126 * channels[0] + .7152 * channels[1] + .0722 * channels[2];
        }
        var firstLuminance = luminance(first);
        var secondLuminance = luminance(second);
        return (Math.max(firstLuminance, secondLuminance) + .05) / (Math.min(firstLuminance, secondLuminance) + .05);
    }

    function rgbToHex(value) {
        var match = String(value || '').match(/rgba?\(\s*(\d+)\D+(\d+)\D+(\d+)/i);
        if (!match) return null;
        return '#' + [match[1], match[2], match[3]].map(function (channel) {
            return Math.max(0, Math.min(255, Number(channel))).toString(16).padStart(2, '0');
        }).join('').toUpperCase();
    }

    function updatePreviewContrast() {
        var preview = document.querySelector('[data-theme-preview]');
        var status = document.querySelector('[data-preview-contrast]');
        if (!preview || !status) return;
        var style = window.getComputedStyle(preview);
        var accent = readHex(style.getPropertyValue('--accent'));
        var background = readHex(style.getPropertyValue('--game-bg'));
        var textColor = readHex(style.getPropertyValue('--text')) || rgbToHex(style.color);
        if (!accent || !background || !textColor) return;
        var buttonRatio = contrastRatio(accent, '#FFFFFF');
        var textRatio = contrastRatio(background, textColor);
        var minimum = Math.min(buttonRatio, textRatio);
        status.dataset.state = minimum >= 4.5 ? 'good' : minimum >= 3 ? 'warning' : 'danger';
        status.textContent = minimum >= 4.5
            ? '✓ 읽기 편한 색상 대비입니다. (최소 ' + minimum.toFixed(1) + ':1)'
            : '⚠ 색상 대비가 낮습니다. 강조색이나 배경색을 조정해 주세요. (최소 ' + minimum.toFixed(1) + ':1)';
    }

    function initThemePreview() {
        var select = document.querySelector('[data-theme-select]');
        var preview = document.querySelector('[data-theme-preview]');
        var choices = Array.prototype.slice.call(document.querySelectorAll('[data-theme-choice]'));
        var name = document.querySelector('[data-preview-theme-name]');
        var applyPalette = document.querySelector('[data-theme-palette-apply]');
        if (!select || !preview) return;
        function update() {
            var theme = select.value || 'MIDNIGHT';
            preview.dataset.theme = theme;
            choices.forEach(function (choice) {
                choice.setAttribute('aria-pressed', String(choice.dataset.themeChoice === theme));
            });
            if (name) {
                var selected = select.options[select.selectedIndex];
                name.textContent = selected ? selected.textContent.split('·')[0].trim() : theme;
            }
            updatePreviewContrast();
        }
        choices.forEach(function (choice) {
            choice.addEventListener('click', function () {
                select.value = choice.dataset.themeChoice;
                select.dispatchEvent(new Event('change', { bubbles: true }));
            });
        });
        if (applyPalette) {
            applyPalette.addEventListener('click', function () {
                var selectedChoice = choices.find(function (choice) { return choice.dataset.themeChoice === select.value; });
                if (!selectedChoice) return;
                var palette = {
                    '--accent': selectedChoice.dataset.presetAccent,
                    '--accent-secondary': selectedChoice.dataset.presetSecondary,
                    '--game-bg': selectedChoice.dataset.presetBackground
                };
                document.querySelectorAll('[data-color-pair]').forEach(function (group) {
                    var input = group.querySelector('input[type="text"]');
                    var value = palette[group.dataset.cssVariable];
                    if (!input || !value) return;
                    input.value = value;
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                });
            });
        }
        select.addEventListener('change', update);
        update();
    }

    function initCoverPreview() {
        var input = document.querySelector('[data-cover-input]');
        var preview = document.querySelector('[data-cover-preview]');
        if (!input || !preview) return;
        function update() {
            var value = input.value.trim();
            if (value && /^(https?:\/\/|\/)/.test(value)) {
                preview.style.backgroundImage = 'linear-gradient(0deg, rgba(0,0,0,.3), rgba(0,0,0,.05)), url("' + value.replace(/["\\]/g, '') + '")';
                preview.style.backgroundSize = 'cover';
                preview.style.backgroundPosition = 'center';
            } else {
                preview.style.removeProperty('background-image');
            }
        }
        input.addEventListener('change', update);
        update();
    }

    function initCopyButtons() {
        document.querySelectorAll('[data-copy]').forEach(function (button) {
            button.addEventListener('click', function () {
                var target = document.getElementById(button.dataset.copy);
                if (!target) return;
                var text = target.textContent.trim();
                if (!navigator.clipboard) return;
                navigator.clipboard.writeText(text).then(function () {
                    var original = button.textContent;
                    button.textContent = '복사됨';
                    window.setTimeout(function () { button.textContent = original; }, 1400);
                });
            });
        });
    }

    function initTypeFields() {
        document.querySelectorAll('[data-puzzle-type]').forEach(function (select) {
            var form = select.closest('form');
            if (!form) return;
            function update() {
                var type = select.value;
                form.querySelectorAll('[data-types]').forEach(function (group) {
                    var types = group.dataset.types.split(',');
                    var visible = types.indexOf(type) !== -1;
                    group.hidden = !visible;
                    group.querySelectorAll('input,select,textarea,button').forEach(function (control) {
                        control.disabled = !visible;
                    });
                });
            }
            select.addEventListener('change', update);
            update();
        });
    }

    function initOptionRouting() {
        document.querySelectorAll('[data-option-routing]').forEach(function (editor) {
            var source = editor.querySelector('[data-option-source]');
            var routesInput = editor.querySelector('[data-option-routes]');
            var list = editor.querySelector('[data-option-route-list]');
            var template = editor.querySelector('[data-option-route-template]');
            if (!source || !routesInput || !list || !template) return;
            var routes = {};
            try { routes = JSON.parse(routesInput.value || '{}') || {}; }
            catch (error) { routes = {}; }

            function options() {
                var seen = {};
                return source.value.replace(/\r/g, '').split(/\n|,/).map(function (value) {
                    return value.trim();
                }).filter(function (value) {
                    if (!value || seen[value]) return false;
                    seen[value] = true;
                    return true;
                }).slice(0, 20);
            }

            function sync() {
                var nextRoutes = {};
                list.querySelectorAll('[data-option-route-select]').forEach(function (select) {
                    if (select.value) nextRoutes[select.dataset.option] = select.value;
                });
                routes = nextRoutes;
                routesInput.value = JSON.stringify(routes);
            }

            function render() {
                list.querySelectorAll('[data-option-route-select]').forEach(function (select) {
                    if (select.value) routes[select.dataset.option] = select.value;
                });
                list.replaceChildren();
                options().forEach(function (option) {
                    var row = template.content.firstElementChild.cloneNode(true);
                    var label = row.querySelector('[data-option-route-label]');
                    var select = row.querySelector('[data-option-route-select]');
                    label.textContent = '"' + option + '" 선택 시';
                    select.dataset.option = option;
                    select.value = routes[option] || '';
                    select.addEventListener('change', sync);
                    list.appendChild(row);
                });
                sync();
            }

            source.addEventListener('input', render);
            render();
        });
    }

    function initCounters() {
        document.querySelectorAll('[data-count]').forEach(function (field) {
            var output = document.getElementById(field.dataset.count);
            if (!output) return;
            function update() { output.textContent = String(field.value.length); }
            field.addEventListener('input', update);
            update();
        });
    }

    function initTemplateFilters() {
        var toolbar = document.querySelector('[data-template-filters]');
        var gallery = document.querySelector('[data-template-gallery]');
        if (!toolbar || !gallery) return;
        var cards = Array.prototype.slice.call(gallery.querySelectorAll('[data-template-card]'));
        var status = document.querySelector('[data-template-status]');
        toolbar.querySelectorAll('[data-template-filter]').forEach(function (button) {
            button.addEventListener('click', function () {
                var filter = button.dataset.templateFilter;
                var visible = 0;
                toolbar.querySelectorAll('[data-template-filter]').forEach(function (chip) {
                    chip.setAttribute('aria-pressed', String(chip === button));
                });
                cards.forEach(function (card) {
                    var categories = (card.dataset.category || '').split(/\s+/);
                    var show = filter === 'all' || categories.indexOf(filter) >= 0;
                    card.hidden = !show;
                    if (show) visible += 1;
                });
                if (status) status.textContent = visible + '개 템플릿이 표시됩니다.';
            });
        });
    }

    function initEmojiPickers() {
        document.querySelectorAll('[data-emoji-value]').forEach(function (button) {
            button.addEventListener('click', function () {
                var input = document.getElementById(button.dataset.emojiTarget);
                if (!input) return;
                input.value = button.dataset.emojiValue;
                input.dispatchEvent(new Event('input', { bubbles: true }));
                input.focus();
            });
        });
    }

    function initColorPairs() {
        document.querySelectorAll('[data-color-pair]').forEach(function (group) {
            var color = group.querySelector('input[type="color"]');
            var textInput = group.querySelector('input[type="text"]');
            var preview = document.querySelector('[data-theme-preview]');
            var error = group.querySelector('[data-color-error]');
            if (!color || !textInput) return;
            var cssVariable = group.dataset.cssVariable;
            function apply(value) {
                var safeValue = readHex(value);
                var valid = Boolean(safeValue);
                textInput.setAttribute('aria-invalid', String(!valid));
                if (error) error.hidden = valid;
                if (!valid) return;
                color.value = safeValue;
                textInput.value = safeValue;
                if (preview && cssVariable) preview.style.setProperty(cssVariable, safeValue);
                document.querySelectorAll('[data-preview-color="' + cssVariable + '"]').forEach(function (swatch) {
                    swatch.style.backgroundColor = safeValue;
                });
                updatePreviewContrast();
            }
            color.addEventListener('input', function () { apply(color.value); });
            textInput.addEventListener('input', function () { apply(textInput.value); });
            apply(textInput.value || color.value);
        });
    }

    function initLiveTextPreview() {
        document.querySelectorAll('[data-preview-text]').forEach(function (input) {
            var outputs = input.dataset.previewText.split(/\s+/).map(function (id) { return document.getElementById(id); }).filter(Boolean);
            if (!outputs.length) return;
            function update() {
                var value = input.value.trim() || input.dataset.previewFallback || '';
                outputs.forEach(function (output) { output.textContent = value; });
            }
            input.addEventListener('input', update);
            update();
        });
    }

    function initPreviewValues() {
        document.querySelectorAll('[data-preview-option]').forEach(function (select) {
            var output = document.getElementById(select.dataset.previewOption);
            if (!output) return;
            function update() {
                var option = select.options[select.selectedIndex];
                output.textContent = option ? option.textContent : '';
            }
            select.addEventListener('change', update);
            update();
        });
        document.querySelectorAll('[data-preview-value]').forEach(function (input) {
            var output = document.getElementById(input.dataset.previewValue);
            if (!output) return;
            function update() {
                output.textContent = (input.value || '—') + (input.dataset.previewSuffix || '');
            }
            input.addEventListener('input', update);
            update();
        });
    }

    function initPreviewModes() {
        var buttons = Array.prototype.slice.call(document.querySelectorAll('[data-preview-mode]'));
        var panels = Array.prototype.slice.call(document.querySelectorAll('[data-preview-panel]'));
        if (!buttons.length || !panels.length) return;
        function activate(mode) {
            buttons.forEach(function (button) {
                button.setAttribute('aria-selected', String(button.dataset.previewMode === mode));
            });
            panels.forEach(function (panel) { panel.hidden = panel.dataset.previewPanel !== mode; });
        }
        buttons.forEach(function (button) {
            button.addEventListener('click', function () { activate(button.dataset.previewMode); });
        });
        activate('landing');
    }

    function initPhotoPreviews() {
        document.querySelectorAll('[data-photo-input]').forEach(function (input) {
            var image = document.getElementById(input.dataset.photoInput);
            if (!image) return;
            var objectUrl = null;
            input.addEventListener('change', function () {
                var file = input.files && input.files[0];
                if (!file || !file.type.startsWith('image/')) return;
                if (objectUrl) URL.revokeObjectURL(objectUrl);
                objectUrl = URL.createObjectURL(file);
                image.src = objectUrl;
                image.hidden = false;
                if (image.parentElement) image.parentElement.querySelectorAll('span').forEach(function (placeholder) { placeholder.hidden = true; });
                if (input.dataset.photoUploadFor) {
                    document.querySelectorAll('[data-photo-remove-for]').forEach(function (remove) {
                        if (remove.dataset.photoRemoveFor === input.dataset.photoUploadFor) remove.checked = false;
                    });
                }
            });
        });
    }

    function initBuilderJumps() {
        document.querySelectorAll('[data-builder-jump]').forEach(function (button) {
            button.addEventListener('click', function () {
                var tab = document.querySelector('[data-tab="' + button.dataset.builderJump + '"]');
                if (tab) tab.click();
            });
        });
    }

    function initToggleFallbacks() {
        document.querySelectorAll('[data-toggle-checkbox]').forEach(function (checkbox) {
            var label = checkbox.closest('.tool-toggle');
            var fallback = label && label.querySelector('[data-toggle-fallback]');
            var previews = checkbox.dataset.toolPreview
                ? checkbox.dataset.toolPreview.split(/\s+/).map(function (id) { return document.getElementById(id); }).filter(Boolean)
                : [];
            if (!fallback) return;
            function update() {
                fallback.disabled = checkbox.checked;
                previews.forEach(function (preview) { preview.hidden = !checkbox.checked; });
            }
            checkbox.addEventListener('change', update);
            update();
        });
    }

    function initRangeOutputs() {
        document.querySelectorAll('[data-volume-range]').forEach(function (input) {
            var field = input.closest('.field');
            var output = field && field.querySelector('[data-range-output]');
            if (!output) return;
            function update() {
                var value = Math.max(0, Math.min(1, Number(input.value || 0)));
                output.textContent = Math.round(value * 100) + '%';
            }
            input.addEventListener('input', update);
            update();
        });
        document.querySelectorAll('[data-story-speed-range]').forEach(function (input) {
            var field = input.closest('.field');
            var output = field && field.querySelector('[data-story-speed-output]');
            if (!output) return;
            function update() { output.textContent = String(input.value || 32) + 'ms/글자'; }
            input.addEventListener('input', update);
            update();
        });
    }

    function initOpenversePickers() {
        function safeUrl(value) {
            if (!value) return '';
            try {
                var parsed = new URL(value, window.location.origin);
                if (parsed.origin === window.location.origin) return parsed.pathname + parsed.search + parsed.hash;
                return parsed.protocol === 'https:' ? parsed.href : '';
            } catch (error) {
                return '';
            }
        }

        function firstValue(source, names) {
            for (var index = 0; index < names.length; index += 1) {
                var value = source && source[names[index]];
                if (value !== undefined && value !== null && String(value).trim()) return String(value).trim();
            }
            return '';
        }

        document.querySelectorAll('[data-audio-picker]').forEach(function (picker) {
            var instanceId = picker.dataset.audioPicker;
            var endpoint = picker.dataset.audioSearchEndpoint;
            var kind = picker.dataset.audioKind;
            var query = picker.querySelector('[data-openverse-query]');
            var search = picker.querySelector('[data-openverse-search]');
            var status = picker.querySelector('[data-openverse-status]');
            var results = picker.querySelector('[data-openverse-results]');
            var selected = picker.querySelector('[data-audio-selected]');
            var selectedAudio = picker.querySelector('[data-selected-preview]');
            var selectedTitle = picker.querySelector('[data-selected-title]');
            var selectedCreator = picker.querySelector('[data-selected-creator]');
            var selectedLicense = picker.querySelector('[data-selected-license]');
            var selectedSource = picker.querySelector('[data-selected-source]');
            var fields = {};
            var requestController = null;

            picker.querySelectorAll('[data-audio-field]').forEach(function (field) {
                fields[field.dataset.audioField] = field;
            });

            function setStatus(message, state) {
                if (!status) return;
                status.textContent = message;
                if (state) status.dataset.state = state;
                else status.removeAttribute('data-state');
            }

            function clearResults() {
                if (!results) return;
                while (results.firstChild) results.removeChild(results.firstChild);
            }

            function setLink(link, href, label) {
                if (!link) return;
                var safeHref = safeUrl(href);
                link.hidden = !safeHref;
                if (safeHref) {
                    link.href = safeHref;
                    if (label) link.textContent = label;
                } else {
                    link.removeAttribute('href');
                }
            }

            function clearSelection() {
                Object.keys(fields).forEach(function (key) { fields[key].value = ''; });
                if (selectedAudio) {
                    selectedAudio.pause();
                    selectedAudio.removeAttribute('src');
                    selectedAudio.load();
                }
                if (selected) selected.hidden = true;
            }

            function selectAudio(item) {
                var audioUrl = safeUrl(firstValue(item, ['audioUrl', 'audio_url', 'url']));
                if (!audioUrl) {
                    setStatus('이 결과의 재생 주소를 사용할 수 없습니다.', 'error');
                    return;
                }
                var title = firstValue(item, ['title', 'name']) || '제목 없는 오디오';
                var creator = firstValue(item, ['creator', 'creatorName', 'creator_name']) || '창작자 정보 없음';
                var license = firstValue(item, ['license', 'licenseName', 'license_name']) || '라이선스 정보 없음';
                var licenseUrl = safeUrl(firstValue(item, ['licenseUrl', 'license_url']));
                var sourceUrl = safeUrl(firstValue(item, ['sourceUrl', 'source_url', 'foreignLandingUrl', 'foreign_landing_url']));
                var values = { url: audioUrl, title: title, creator: creator, license: license, licenseUrl: licenseUrl, sourceUrl: sourceUrl };
                Object.keys(values).forEach(function (key) { if (fields[key]) fields[key].value = values[key]; });
                if (selectedTitle) selectedTitle.textContent = title;
                if (selectedCreator) selectedCreator.textContent = creator;
                if (selectedAudio) { selectedAudio.src = audioUrl; selectedAudio.load(); }
                setLink(selectedLicense, licenseUrl, license);
                setLink(selectedSource, sourceUrl, '원문 확인');
                if (selected) { selected.hidden = false; selected.tabIndex = -1; selected.focus(); }
                document.querySelectorAll('[data-audio-upload-for]').forEach(function (upload) {
                    if (upload.dataset.audioUploadFor === instanceId) upload.value = '';
                });
                document.querySelectorAll('[data-audio-remove-for]').forEach(function (remove) {
                    if (remove.dataset.audioRemoveFor === instanceId) remove.checked = false;
                });
                setStatus('오디오를 선택했습니다. 저장 버튼을 눌러 반영하세요.', 'success');
            }

            function resultCard(item) {
                var card = document.createElement('article');
                var copy = document.createElement('div');
                var title = document.createElement('strong');
                var creator = document.createElement('p');
                var meta = document.createElement('div');
                var audio = document.createElement('audio');
                var actions = document.createElement('div');
                var choose = document.createElement('button');
                var audioUrl = safeUrl(firstValue(item, ['audioUrl', 'audio_url', 'url']));
                var licenseUrl = safeUrl(firstValue(item, ['licenseUrl', 'license_url']));
                var sourceUrl = safeUrl(firstValue(item, ['sourceUrl', 'source_url', 'foreignLandingUrl', 'foreign_landing_url']));
                var license = firstValue(item, ['license', 'licenseName', 'license_name']) || '라이선스 미표기';

                card.className = 'audio-result-card';
                copy.className = 'audio-result-card__copy';
                title.textContent = firstValue(item, ['title', 'name']) || '제목 없는 오디오';
                creator.textContent = firstValue(item, ['creator', 'creatorName', 'creator_name']) || '창작자 정보 없음';
                meta.className = 'audio-result-card__meta';
                audio.controls = true;
                audio.preload = 'none';
                audio.src = audioUrl;
                audio.setAttribute('aria-label', title.textContent + ' 미리듣기');
                actions.className = 'audio-result-card__actions';
                choose.className = 'btn btn--small';
                choose.type = 'button';
                choose.textContent = '이 오디오 선택';
                choose.disabled = !audioUrl;
                choose.addEventListener('click', function () { selectAudio(item); });

                if (licenseUrl) {
                    var licenseLink = document.createElement('a');
                    licenseLink.href = licenseUrl;
                    licenseLink.target = '_blank';
                    licenseLink.rel = 'noopener noreferrer';
                    licenseLink.textContent = license;
                    meta.appendChild(licenseLink);
                } else {
                    var licenseText = document.createElement('span');
                    licenseText.textContent = license;
                    meta.appendChild(licenseText);
                }
                if (sourceUrl) {
                    var sourceLink = document.createElement('a');
                    sourceLink.href = sourceUrl;
                    sourceLink.target = '_blank';
                    sourceLink.rel = 'noopener noreferrer';
                    sourceLink.textContent = '원문';
                    meta.appendChild(sourceLink);
                }
                copy.appendChild(title);
                copy.appendChild(creator);
                copy.appendChild(meta);
                actions.appendChild(choose);
                card.appendChild(copy);
                card.appendChild(audio);
                card.appendChild(actions);
                return card;
            }

            function runSearch() {
                var term = query ? query.value.trim() : '';
                if (!term) {
                    setStatus('검색어를 먼저 입력하세요.', 'error');
                    if (query) query.focus();
                    return;
                }
                if (!endpoint) {
                    setStatus('오디오 검색 주소를 찾을 수 없습니다.', 'error');
                    return;
                }
                if (requestController) requestController.abort();
                requestController = typeof AbortController === 'function' ? new AbortController() : null;
                clearResults();
                picker.setAttribute('aria-busy', 'true');
                search.disabled = true;
                setStatus('Openverse에서 오디오를 찾고 있습니다.', 'loading');
                var separator = endpoint.indexOf('?') >= 0 ? '&' : '?';
                var url = endpoint + separator + new URLSearchParams({ q: term, kind: kind }).toString();
                fetch(url, { credentials: 'same-origin', headers: { Accept: 'application/json' }, signal: requestController ? requestController.signal : undefined })
                    .then(function (response) {
                        return response.json().catch(function () { return {}; }).then(function (payload) {
                            if (!response.ok) throw new Error(payload.message || 'search_failed');
                            return payload;
                        });
                    })
                    .then(function (payload) {
                        var items = Array.isArray(payload) ? payload : (payload.results || payload.items || []);
                        if (!Array.isArray(items) || !items.length) {
                            setStatus('검색 결과가 없습니다. 다른 단어로 찾아보세요.', 'empty');
                            return;
                        }
                        items.forEach(function (item) { results.appendChild(resultCard(item)); });
                        setStatus(items.length + '개의 결과를 찾았습니다. 미리 듣고 원문 라이선스를 확인하세요.', 'success');
                    })
                    .catch(function (error) {
                        if (error && error.name === 'AbortError') return;
                        var message = error && error.message && error.message !== 'search_failed' ? error.message : '검색 중 문제가 생겼습니다. 잠시 후 다시 시도하세요.';
                        setStatus(message, 'error');
                    })
                    .finally(function () {
                        picker.removeAttribute('aria-busy');
                        search.disabled = false;
                    });
            }

            if (search) search.addEventListener('click', runSearch);
            if (query) query.addEventListener('keydown', function (event) {
                if (event.key === 'Enter') { event.preventDefault(); runSearch(); }
            });
            document.querySelectorAll('[data-audio-upload-for]').forEach(function (upload) {
                if (upload.dataset.audioUploadFor !== instanceId) return;
                upload.addEventListener('change', function () { if (upload.files && upload.files.length) clearSelection(); });
            });
            document.querySelectorAll('[data-audio-remove-for]').forEach(function (remove) {
                if (remove.dataset.audioRemoveFor !== instanceId) return;
                remove.addEventListener('change', function () { if (remove.checked) clearSelection(); });
            });
        });
    }

    onReady(function () {
        initNavigation();
        initDismissibleAlerts();
        initConfirmations();
        initPrintButtons();
        initPasswordToggles();
        initBuilderTabs();
        initStageWorkspaces();
        initThemePreview();
        initCoverPreview();
        initCopyButtons();
        initTypeFields();
        initOptionRouting();
        initCounters();
        initTemplateFilters();
        initEmojiPickers();
        initColorPairs();
        initLiveTextPreview();
        initPreviewValues();
        initPreviewModes();
        initPhotoPreviews();
        initBuilderJumps();
        initToggleFallbacks();
        initRangeOutputs();
        initOpenversePickers();
    });
}());
