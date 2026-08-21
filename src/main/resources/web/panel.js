/*
 * AethelCore paneli — semadan uretilen arayuz.
 *
 * Bu dosya HICBIR bolumun adini bilmez: bolumler, alanlar ve tipler
 * /api/schema ucundan gelir. Yeni bir modul alani eklemek icin yalnizca
 * PanelSchema.java'ya satir eklenir; burasi degismez.
 */
(() => {
  const $ = (sel, root = document) => root.querySelector(sel);
  const el = (tag, attrs = {}, ...kids) => {
    const node = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
      if (k === 'class') node.className = v;
      else if (k === 'text') node.textContent = v;
      else if (v !== null && v !== undefined) node.setAttribute(k, v);
    }
    kids.filter(Boolean).forEach(kid => node.append(kid));
    return node;
  };
  const get = async path => {
    const response = await fetch(path);
    if (!response.ok) throw new Error(await response.text());
    return response.json();
  };
  const send = async (path, body) => {
    const response = await fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    if (!response.ok) throw new Error(await response.text());
    return response.json();
  };

  const accentToken = () =>
    getComputedStyle(document.documentElement).getPropertyValue('--color-accent').trim();

  const state = { schema: null, section: null, records: [], current: null };

  const toast = message => {
    const box = $('#toast');
    box.textContent = message;
    box.hidden = false;
    clearTimeout(box.dataset.timer);
    box.dataset.timer = setTimeout(() => { box.hidden = true; }, 3200);
  };

  /* ---- kenar cubugu ---- */
  const renderSidebar = () => {
    const nav = $('#sections');
    nav.textContent = '';
    let group = null;
    for (const section of state.schema.sections) {
      if (section.group !== group) {
        group = section.group;
        nav.append(el('div', { class: 'side-group', text: group }));
      }
      const link = el('a', { href: '#' + section.id, text: section.label });
      link.dataset.id = section.id;
      nav.append(link);
    }
  };

  /* ---- kayit listesi ---- */
  const renderList = () => {
    const list = $('#records');
    list.textContent = '';
    state.records.forEach((record, index) => {
      const button = el('button', { type: 'button' },
        el('span', { text: String(record.id ?? record.name ?? '(adsiz)') }));
      const sub = record.__file || record.__ns;
      if (sub) button.append(el('small', { text: sub }));
      button.onclick = () => select(index);
      list.append(button);
    });
    if (!state.section.readOnly && state.section.kind === 'FILE') {
      const add = el('button', { type: 'button' }, el('span', { text: '+ Yeni kayit' }));
      add.onclick = () => {
        state.records.push({ id: 'yeni', __ns: state.schema.namespaces[0] || '' });
        renderList();
        select(state.records.length - 1);
      };
      list.append(add);
    }
    highlight(list, state.current);
  };

  const highlight = (root, index) => {
    [...root.children].forEach((child, i) =>
      child.classList.toggle('active', i === index));
  };

  /* ---- alan girisleri ---- */
  const control = (field, value) => {
    const id = 'f_' + field.key.replace(/[^a-z0-9]/gi, '_');
    let input;
    if (field.type === 'bool') {
      input = el('input', { id, type: 'checkbox', class: 'checkbox' });
      input.checked = value === true || value === 'true';
    } else if (field.type === 'sel') {
      input = el('select', { id, class: 'input' });
      for (const option of field.options) {
        input.append(el('option', { value: option, text: option }));
      }
      input.value = value ?? field.options[0] ?? '';
    } else if (field.type === 'area' || field.type === 'list') {
      input = el('textarea', { id, class: 'input' });
      input.value = Array.isArray(value) ? value.join('\n') : (value ?? '');
    } else {
      input = el('input', {
        id, class: 'input',
        type: field.type === 'num' ? 'number' : field.type === 'color' ? 'color' : 'text',
        step: field.type === 'num' ? 'any' : null
      });
      // Renk alaninin varsayilani sisteminin kendi vurgu tokenindan okunur;
      // panelde hicbir yerde ham renk degeri yazili degildir.
      input.value = value ?? (field.type === 'color' ? accentToken() : '');
    }
    input.dataset.key = field.key;
    input.dataset.type = field.type;
    if (state.section.readOnly) input.disabled = true;

    const wrap = el('div', { class: 'field' },
      el('label', { for: id, text: field.label }), input);
    if (field.hint) wrap.append(el('small', { class: 'hint', text: field.hint }));
    return wrap;
  };

  const renderEditor = () => {
    const form = $('#editor');
    form.textContent = '';
    const record = state.current === null ? null : state.records[state.current];
    if (!record) {
      form.append(el('p', { class: 'text-muted', text: 'Soldan bir kayit sec.' }));
      return;
    }
    for (const group of state.section.groups) {
      const fields = el('div', { class: 'fields' });
      group.fields.forEach(field => fields.append(control(field, record[field.key])));
      form.append(el('fieldset', {}, el('legend', { text: group.label }), fields));
    }
    if (state.section.readOnly) {
      form.append(el('p', { class: 'readonly-note',
        text: 'Bu bolum yalnizca okunur; kayitlar denetim icin degistirilemez.' }));
      return;
    }
    const save = el('button', { class: 'btn btn-primary', type: 'button', text: 'Kaydet' });
    const remove = el('button', { class: 'btn btn-secondary', type: 'button', text: 'Sil' });
    save.onclick = () => submit('save');
    remove.onclick = () => { if (confirm('Kayit silinsin mi?')) submit('delete'); };
    form.append(el('div', { class: 'actions' }, save,
      state.section.kind === 'FILE' ? remove : null,
      el('span', { class: 'status', text: state.section.file })));
  };

  /* ---- okuma / yazma ---- */
  const collect = () => {
    const record = { ...state.records[state.current] };
    for (const input of $('#editor').querySelectorAll('[data-key]')) {
      const key = input.dataset.key;
      const type = input.dataset.type;
      if (type === 'bool') record[key] = input.checked;
      else if (type === 'num') record[key] = input.value === '' ? null : Number(input.value);
      else if (type === 'list') {
        record[key] = input.value.split('\n').map(line => line.trim()).filter(Boolean);
      } else record[key] = input.value;
    }
    return record;
  };

  const submit = async action => {
    try {
      const record = collect();
      await send(`/api/section/${state.section.id}/${action}`, record);
      toast(action === 'delete' ? 'Kayit silindi.' : 'Kaydedildi.');
      await open(state.section.id, action === 'delete' ? null : state.current);
    } catch (error) {
      toast('Basarisiz: ' + error.message);
    }
  };

  const select = index => {
    state.current = index;
    highlight($('#records'), index);
    renderEditor();
  };

  const open = async (id, keepIndex = null) => {
    const section = state.schema.sections.find(s => s.id === id);
    if (!section) return;
    state.section = section;
    $('#title').textContent = section.label;
    [...$('#sections').children].forEach(child =>
      child.classList && child.dataset && child.classList.toggle('active', child.dataset.id === id));
    state.records = await get('/api/section/' + id);
    state.current = keepIndex !== null && keepIndex < state.records.length
      ? keepIndex : (state.records.length ? 0 : null);
    renderList();
    renderEditor();
  };

  const boot = async () => {
    state.schema = await get('/api/schema');
    renderSidebar();
    const initial = location.hash.slice(1) || state.schema.sections[0].id;
    window.addEventListener('hashchange', () => open(location.hash.slice(1)));
    await open(initial);
  };

  boot().catch(error => toast('Panel yuklenemedi: ' + error.message));
})();
