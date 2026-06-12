const SCOPE = 'mycelium/meshaid/v1';
  const TYPE_ICONS = { help:'🆘', available:'✅', resource:'💧', blocked:'🚫', resolved:'✓', location:'📍' };
  const TYPE_LABELS = { help:'Help needed', available:'Help available', resource:'Resource', blocked:'Blocked', resolved:'Resolved', location:'Location' };
  const STORAGE_KEY = 'meshaid_posts';
  let posts = [], activeFilter = 'all', selectedType = 'help';

  function esc(s) { return String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }

  async function loadPosts() {
    try {
      const stored = await window.mycelium?.storage.get(STORAGE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored);
        posts = parsed.filter(p => p.expires_at > Date.now());
      }
      const bulletins = await window.mycelium?.getBulletins(SCOPE) || [];
      bulletins.forEach(b => {
        try {
          const post = JSON.parse(b.body);
          if (post.meshaid_type && !posts.find(p => p.id === post.id)) {
            posts.push({ ...post, from: b.from_display_name, expires_at: b.expires_at_ms });
          }
        } catch {}
      });
      renderPosts();
    } catch (e) { console.error('loadPosts:', e); }
  }

  function renderPosts() {
    const list = document.getElementById('posts-list');
    const filtered = activeFilter === 'all' ? posts : posts.filter(p => p.meshaid_type === activeFilter);
    document.getElementById('post-count').textContent = `${filtered.length} posts`;
    if (!filtered.length) {
      list.innerHTML = `<div class="empty"><p style="font-size:36px">📡</p><p>${activeFilter==='all'?'No posts yet. Be the first to post.':'No posts of this type.'}</p></div>`;
      return;
    }
    const sorted = [...filtered].sort((a,b) => {
      if (a.meshaid_type==='resolved' && b.meshaid_type!=='resolved') return 1;
      if (b.meshaid_type==='resolved' && a.meshaid_type!=='resolved') return -1;
      return b.created_at - a.created_at;
    });
    list.innerHTML = sorted.map(p => {
      const age = Math.round((Date.now()-p.created_at)/60000);
      const ageStr = age < 60 ? `${age}m ago` : `${Math.round(age/60)}h ago`;
      return `<div class="post ${p.meshaid_type}">
        <div class="post-header"><span class="post-type">${TYPE_ICONS[p.meshaid_type]||'📌'}</span>
        <div><div style="font-size:12px;font-weight:500">${TYPE_LABELS[p.meshaid_type]||p.meshaid_type}</div>
        <div class="post-meta">${esc(p.from||'Unknown')} · ${ageStr}</div></div></div>
        <div class="post-title">${esc(p.title)}</div>
        ${p.body?`<div class="post-body">${esc(p.body)}</div>`:''}
        ${p.location?`<div class="post-body"><a href="geo:${p.location.lat},${p.location.lon}" style="color:#1D9E75">📍 ${p.location.lat.toFixed(4)}, ${p.location.lon.toFixed(4)}</a></div>`:''}
        ${p.meshaid_type!=='resolved'?`<div class="post-actions"><button class="btn-sm resolve" data-resolve="${p.id}">✓ Mark resolved</button></div>`:''}
      </div>`;
    }).join('');
    document.querySelectorAll('[data-resolve]').forEach(btn => btn.addEventListener('click', () => resolvePost(btn.dataset.resolve)));
  }

  async function submitPost() {
    const title = document.getElementById('f-title').value.trim();
    if (!title) { alert('Please enter a title'); return; }
    const ttl = parseInt(document.getElementById('f-ttl').value, 10);
    let location = null;
    if (document.getElementById('f-location').value === 'yes' && navigator.geolocation) {
      try {
        location = await new Promise((res, rej) => navigator.geolocation.getCurrentPosition(
          p => res({ lat: p.coords.latitude, lon: p.coords.longitude }), rej, { timeout: 5000 }));
      } catch {}
    }
    const post = { id: crypto.randomUUID(), meshaid_type: selectedType, title,
      body: document.getElementById('f-body').value.trim(), location,
      created_at: Date.now(), expires_at: Date.now() + ttl * 1000 };
    posts.push({ ...post, from: 'You' });
    await window.mycelium?.storage.set(STORAGE_KEY, JSON.stringify(posts));
    await window.mycelium?.postBulletin(SCOPE, post.title, JSON.stringify(post), ttl);
    document.getElementById('modal').style.display = 'none';
    document.getElementById('f-title').value = '';
    document.getElementById('f-body').value = '';
    renderPosts();
  }

  async function resolvePost(postId) {
    const post = posts.find(p => p.id === postId);
    if (!post) return;
    post.meshaid_type = 'resolved';
    await window.mycelium?.storage.set(STORAGE_KEY, JSON.stringify(posts));
    await window.mycelium?.postBulletin(SCOPE, `RESOLVED: ${post.title}`, JSON.stringify({ ...post, meshaid_type: 'resolved' }), 3600);
    renderPosts();
  }

  document.getElementById('new-post-btn').addEventListener('click', () => { document.getElementById('modal').style.display = 'flex'; });
  document.getElementById('cancel-btn').addEventListener('click', () => { document.getElementById('modal').style.display = 'none'; });
  document.getElementById('submit-post').addEventListener('click', submitPost);
  document.querySelectorAll('.type-option').forEach(opt => opt.addEventListener('click', () => {
    document.querySelectorAll('.type-option').forEach(o => o.classList.remove('selected'));
    opt.classList.add('selected'); selectedType = opt.dataset.type;
  }));
  document.querySelectorAll('.type-tab').forEach(tab => tab.addEventListener('click', () => {
    document.querySelectorAll('.type-tab').forEach(t => t.classList.remove('active'));
    tab.classList.add('active'); activeFilter = tab.dataset.filter; renderPosts();
  }));
  loadPosts();
  window.mycelium?.registerBulletinRefresh?.(loadPosts);
  setInterval(loadPosts, 15000);
