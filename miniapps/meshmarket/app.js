const CATEGORIES = [
    { id: 'food', label: '🍎 Food' },
    { id: 'tools', label: '🔧 Tools' },
    { id: 'shelter', label: '🏠 Shelter' },
    { id: 'clothing', label: '👕 Clothing' },
    { id: 'medical', label: '💊 Medical' },
    { id: 'services', label: '🤝 Services' },
    { id: 'other', label: '📦 Other' },
  ];
  const STORAGE_KEY = 'meshmarket_listings';
  let category = 'food';
  let listings = [];
  let stockById = {};
  let myPeerId = '';

  function esc(s) { return String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
  function scopeFor(cat) { return `market/${cat}`; }

  async function fetchStock(listingId) {
    try {
      const s = await window.mycena?.marketplaceStock?.get(listingId);
      if (s) stockById[listingId] = s;
      return s;
    } catch { return null; }
  }

  async function loadStockForListings() {
    await Promise.all(listings.map(l => fetchStock(l.id)));
  }

  function stockLabel(listingId) {
    const s = stockById[listingId];
    if (!s || s.status === 'unknown') return '';
    if (s.status === 'conflict') {
      return `<span class="stock conflict">⚠ Stock conflict (oversold ${s.oversold_by || '?'})</span>`;
    }
    if (s.qty === 0) return `<span class="stock out">Out of stock</span>`;
    return `<span class="stock">${s.qty} available</span>`;
  }

  function isOwnListing(l) {
    return l.seller_peer && myPeerId && l.seller_peer === myPeerId;
  }

  async function init() {
    try {
      const id = await window.mycena?.getIdentity?.();
      myPeerId = id?.peer_id || '';
    } catch {}
    renderCats();
    await loadListings();
  }

  function renderCats() {
    document.getElementById('cats').innerHTML = CATEGORIES.map(c =>
      `<button class="cat ${c.id===category?'active':''}" data-cat="${c.id}">${c.label}</button>`
    ).join('');
    document.querySelectorAll('.cat').forEach(btn => btn.addEventListener('click', () => {
      category = btn.dataset.cat; renderCats(); loadListings();
    }));
  }

  async function loadListings() {
    const scope = scopeFor(category);
    try {
      const stored = await window.mycena?.storage.get(STORAGE_KEY + ':' + category);
      listings = stored ? JSON.parse(stored).filter(l => l.expires_at > Date.now()) : [];
      const bulletins = await window.mycena?.getBulletins(scope) || [];
      bulletins.forEach(b => {
        try {
          const l = JSON.parse(b.body);
          if (l.meshmarket && l.category === category && !listings.find(x => x.id === l.id)) {
            listings.push({ ...l, seller_name: b.from_display_name, expires_at: b.expires_at_ms });
          }
        } catch {}
      });
      listings.sort((a,b) => b.created_at - a.created_at);
      await loadStockForListings();
      await window.mycena?.storage.set(STORAGE_KEY + ':' + category, JSON.stringify(listings));
    } catch (e) { console.error(e); }
    renderListings();
  }

  function renderListings() {
    document.getElementById('count').textContent = `${listings.length} listings`;
    const el = document.getElementById('listings');
    if (!listings.length) {
      el.innerHTML = `<div class="empty"><p style="font-size:36px">🛒</p><p>No listings in this category yet.</p></div>`;
      return;
    }
    el.innerHTML = listings.map(l => {
      const priceLabel = l.price_type === 'trade'
        ? 'Trade / barter<span class="trade-badge">swap</span>'
        : `${l.price_muon || 0} μMXC`;
      const stock = stockLabel(l.id);
      const own = isOwnListing(l);
      const s = stockById[l.id];
      const canBuy = !own && s && s.status === 'available' && s.qty > 0;
      const conflict = s && s.status === 'conflict';
      return `<div class="card">
        ${l.photo_base64 ? `<img class="card-img" src="data:image/png;base64,${l.photo_base64}">` : ''}
        <div class="card-body">
          <div style="font-weight:500;font-size:15px">${esc(l.title)}</div>
          <div class="price">${priceLabel}</div>
          ${stock ? `<div>${stock}</div>` : ''}
          <div class="meta">${esc(l.seller_name || 'Seller')} · ${new Date(l.created_at).toLocaleDateString()}</div>
          ${l.description ? `<div class="desc">${esc(l.description)}</div>` : ''}
          <div class="actions">
            <button class="btn" data-msg="${l.id}">💬 Contact</button>
            ${own && conflict
              ? `<button class="btn primary" data-resolve="${l.id}">Resolve stock</button>`
              : ''}
            ${canBuy && l.price_type === 'mxc' && l.price_muon > 0
              ? `<button class="btn primary" data-pay="${l.id}">Pay ${l.price_muon} μ</button>` : ''}
            ${canBuy && l.price_type === 'trade'
              ? `<button class="btn primary" data-reserve="${l.id}">Reserve</button>` : ''}
          </div>
        </div>
      </div>`;
    }).join('');
    document.querySelectorAll('[data-msg]').forEach(btn => btn.addEventListener('click', () => contactSeller(btn.dataset.msg)));
    document.querySelectorAll('[data-pay]').forEach(btn => btn.addEventListener('click', () => paySeller(btn.dataset.pay)));
    document.querySelectorAll('[data-reserve]').forEach(btn => btn.addEventListener('click', () => reserveListing(btn.dataset.reserve)));
    document.querySelectorAll('[data-resolve]').forEach(btn => btn.addEventListener('click', () => resolveStock(btn.dataset.resolve)));
  }

  async function contactSeller(listingId) {
    const l = listings.find(x => x.id === listingId);
    if (!l) return;
    const peer = l.seller_peer || prompt('Seller peer ID to message:');
    if (!peer) return;
    const msg = prompt(`Message about "${l.title}":`);
    if (!msg) return;
    try {
      await window.mycena?.sendMessage(peer, `[MeshMarket] ${msg}`);
      alert('Message sent!');
    } catch (e) { alert('Failed: ' + e.message); }
  }

  async function recordSale(listingId, qty = 1) {
    const result = await window.mycena?.marketplaceStock?.recordSale(listingId, qty);
    stockById[listingId] = result;
    if (result?.status === 'conflict') {
      throw new Error(`Stock conflict — oversold by ${result.oversold_by}`);
    }
    return result;
  }

  async function paySeller(listingId) {
    const l = listings.find(x => x.id === listingId);
    if (!l || !l.price_muon) return;
    if (!l.payment_address) {
      alert('Seller has no payment address on file. Use Contact instead.');
      return;
    }
    try {
      await recordSale(listingId, 1);
      await window.mycena?.requestPayment(l.price_muon, `MeshMarket: ${l.title}`);
      alert('Payment initiated — confirm in the host app.');
      renderListings();
    } catch (e) { alert('Payment failed: ' + e.message); }
  }

  async function reserveListing(listingId) {
    const l = listings.find(x => x.id === listingId);
    if (!l) return;
    if (!confirm(`Reserve "${l.title}"? This records a sale locally and syncs over mesh.`)) return;
    try {
      await recordSale(listingId, 1);
      alert('Reserved — seller will see updated stock when devices sync.');
      renderListings();
    } catch (e) { alert('Failed: ' + e.message); }
  }

  async function resolveStock(listingId) {
    const qty = parseInt(prompt('Set authoritative on-hand quantity:', '0') || '0', 10);
    if (Number.isNaN(qty) || qty < 0) return;
    try {
      const result = await window.mycena?.marketplaceStock?.resolve(listingId, qty);
      stockById[listingId] = result;
      alert('Stock conflict resolved.');
      renderListings();
    } catch (e) { alert('Failed: ' + e.message); }
  }

  document.getElementById('f-price-type').addEventListener('change', (e) => {
    const isMxc = e.target.value === 'mxc';
    document.getElementById('price-field').style.display = isMxc ? 'block' : 'none';
    document.getElementById('pay-addr-field').style.display = isMxc ? 'block' : 'none';
  });

  document.getElementById('fab').addEventListener('click', () => document.getElementById('modal').classList.add('open'));
  document.getElementById('cancel').addEventListener('click', () => document.getElementById('modal').classList.remove('open'));

  document.getElementById('submit').addEventListener('click', async () => {
    const title = document.getElementById('f-title').value.trim();
    if (!title) { alert('Title required'); return; }
    const priceType = document.getElementById('f-price-type').value;
    const ttl = parseInt(document.getElementById('f-ttl').value, 10);
    const initialQty = Math.max(0, parseInt(document.getElementById('f-qty').value || '1', 10));
    let photo_base64 = null;
    const file = document.getElementById('f-photo').files?.[0];
    if (file) {
      if (file.size > 50000) { alert('Photo max 50KB'); return; }
      photo_base64 = await new Promise((res, rej) => {
        const r = new FileReader();
        r.onload = () => res(String(r.result).split(',')[1]);
        r.onerror = rej;
        r.readAsDataURL(file);
      });
    }
    let payment_address = document.getElementById('f-pay-addr').value.trim() || null;
    const listing = {
      id: crypto.randomUUID(),
      meshmarket: true,
      category,
      title,
      description: document.getElementById('f-desc').value.trim(),
      price_type: priceType,
      price_muon: priceType === 'mxc' ? parseInt(document.getElementById('f-price').value || '0', 10) : 0,
      seller_peer: document.getElementById('f-peer').value.trim() || myPeerId,
      seller_name: 'You',
      payment_address,
      photo_base64,
      initial_qty: initialQty,
      created_at: Date.now(),
      expires_at: Date.now() + ttl * 1000,
    };
    listings.unshift(listing);
    await window.mycena?.storage.set(STORAGE_KEY + ':' + category, JSON.stringify(listings));
    await window.mycena?.postBulletin(scopeFor(category), listing.title, JSON.stringify(listing), ttl);
    try {
      const stock = await window.mycena?.marketplaceStock?.init(listing.id, initialQty);
      stockById[listing.id] = stock;
    } catch (e) {
      console.warn('stock init failed', e);
    }
    document.getElementById('modal').classList.remove('open');
    document.getElementById('f-title').value = '';
    document.getElementById('f-desc').value = '';
    renderListings();
  });

  init();
  window.mycena?.registerBulletinRefresh?.(loadListings);
  setInterval(loadListings, 20000);
