const INTEREST_OPTIONS = [
    'Music', 'Hiking', 'Tech', 'Art', 'Travel', 'Food',
    'Sports', 'Gaming', 'Books', 'Film', 'Photography', 'Coding',
    'Yoga', 'Coffee', 'Dancing', 'Nature'
  ];
  const MAX_PHOTO_BYTES = 50000;

  let state = {
    screen: 'setup',
    tab: 'nearby',
    profile: {
      display_name: '',
      bio: '',
      age: null,
      gender: '',
      looking_for: '',
      interests: [],
      photo_base64: null,
    },
    nearby: [],
    messages: [],
    sentMessages: [],
    lastMessageMs: 0,
    selectedInterests: new Set(),
    ttl: 300,
    activeChat: null,
  };

  function render() {
    const app = document.getElementById('app');
    if (state.screen === 'setup') {
      app.innerHTML = renderSetup();
    } else {
      app.innerHTML = renderActive();
      if (state.tab === 'nearby') loadNearby();
      else loadMessages();
    }
    bindEvents();
  }

  function renderSetup() {
    return `
      <div class="setup">
        <h2>Join Nearby</h2>
        <p>Share your profile with people around you. Nothing is stored on servers.</p>

        <div class="warning">
          ⚠️ Your profile will be visible to other Mycelium users
          nearby. Only share what you are comfortable with.
          You can stop at any time.
        </div>

        <div class="field">
          <label>Display name (optional)</label>
          <input type="text" id="f-name" placeholder="Alex or a nickname"
                 value="${esc(state.profile.display_name)}" maxlength="40">
        </div>

        <div class="field">
          <label>Bio (optional)</label>
          <textarea id="f-bio" placeholder="Say something about yourself…"
                    maxlength="120">${esc(state.profile.bio)}</textarea>
        </div>

        <div class="field">
          <label>Age (optional)</label>
          <input type="number" id="f-age" min="18" max="99"
                 value="${state.profile.age || ''}" placeholder="18+">
        </div>

        <div class="field">
          <label>Gender (optional)</label>
          <input type="text" id="f-gender" placeholder="How you identify"
                 value="${esc(state.profile.gender)}" maxlength="40">
        </div>

        <div class="field">
          <label>Looking for (optional)</label>
          <input type="text" id="f-looking" placeholder="Friendship, dates, …"
                 value="${esc(state.profile.looking_for)}" maxlength="60">
        </div>

        <div class="field">
          <label>Profile photo (optional, PNG/JPG, max 50KB)</label>
          <input type="file" id="f-photo" accept="image/png,image/jpeg">
          <div class="photo-preview ${state.profile.photo_base64 ? 'visible' : ''}" id="photo-preview">
            ${state.profile.photo_base64
              ? `<img src="data:image/png;base64,${state.profile.photo_base64}">`
              : ''}
          </div>
        </div>

        <div class="field">
          <label>Interests</label>
          <div class="tags">
            ${INTEREST_OPTIONS.map(i => `
              <span class="tag ${state.selectedInterests.has(i) ? 'selected' : ''}"
                    data-interest="${i}">${i}</span>
            `).join('')}
          </div>
        </div>

        <div class="field">
          <label>Visible for</label>
          <select id="f-ttl">
            <option value="300">5 minutes</option>
            <option value="1800">30 minutes</option>
            <option value="3600">1 hour</option>
            <option value="14400">4 hours</option>
          </select>
        </div>

        <button class="btn" id="start-btn">Go visible</button>
      </div>
    `;
  }

  function renderActive() {
    if (state.activeChat) return renderChatPanel();

    const count = state.nearby.length;
    document.getElementById('count-badge').textContent = `${count} nearby`;
    const unread = state.messages.length;

    return `
      <div class="active-banner">
        <span>🟢 You are visible nearby</span>
        <button class="stop-btn" id="stop-btn">Stop</button>
      </div>
      <div class="tabs">
        <button class="tab ${state.tab === 'nearby' ? 'active' : ''}" data-tab="nearby">Nearby</button>
        <button class="tab ${state.tab === 'messages' ? 'active' : ''}" data-tab="messages">
          Messages${unread ? ` (${unread})` : ''}
        </button>
      </div>
      <div id="tab-content">
        ${state.tab === 'nearby' ? renderNearbyList() : renderMessagesList()}
      </div>
    `;
  }

  function renderNearbyList() {
    const count = state.nearby.length;
    if (count === 0) {
      return `<div class="profiles"><div class="empty">
        <p style="font-size:32px">🔍</p>
        <p>Looking for people nearby…</p>
        <p style="font-size:12px;margin-top:8px;">Others need to have Nearby active too.</p>
      </div></div>`;
    }
    return `<div class="profiles">${state.nearby.map(renderProfile).join('')}</div>`;
  }

  function renderMessagesList() {
    const threads = buildThreads();
    if (threads.length === 0) {
      return `<div class="messages"><div class="empty">
        <p style="font-size:32px">💬</p>
        <p>No messages yet</p>
        <p style="font-size:12px;margin-top:8px;">Connect with someone nearby to start chatting.</p>
      </div></div>`;
    }
    return `<div class="messages">${threads.map(t => `
      <div class="thread-card" data-pubkey="${t.pubkey}">
        <div class="name">${esc(t.name)}</div>
        <div class="thread-preview">${esc(t.preview)}</div>
      </div>
    `).join('')}</div>`;
  }

  function renderChatPanel() {
    const chat = state.activeChat;
    const msgs = [...chat.incoming, ...chat.outgoing].sort((a, b) => a.at - b.at);
    return `
      <div class="chat-panel open">
        <div class="chat-header">
          <button class="chat-back" id="chat-back">←</button>
          <div class="name">${esc(chat.name)}</div>
        </div>
        <div class="chat-messages" id="chat-messages">
          ${msgs.map(m => `
            <div class="bubble ${m.mine ? 'me' : 'them'}">${esc(m.body)}</div>
          `).join('')}
        </div>
        <div class="chat-input-row">
          <input type="text" id="chat-input" placeholder="Message…" maxlength="500">
          <button id="chat-send">Send</button>
        </div>
      </div>
    `;
  }

  function matchStatusLabel(p) {
    if (p.is_mutual) return '<div class="match-status">✓ Mutual match — you can message</div>';
    if (p.interest_sent && p.interest_received) return '<div class="match-status">✓ Mutual match — you can message</div>';
    if (p.interest_sent) return '<div class="match-status pending">Waiting for them to connect back…</div>';
    if (p.interest_received) return '<div class="match-status">They connected — connect back to match</div>';
    return '';
  }

  function renderProfile(p) {
    const meta = [
      p.age ? `${p.age}` : null,
      p.gender || null,
      p.looking_for ? `Looking for: ${p.looking_for}` : null,
    ].filter(Boolean).join(' · ');

    let actionBtn = '';
    if (p.is_mutual) {
      actionBtn = `<button class="action-btn" data-action="message" data-pubkey="${p.enc_pubkey_hex}">💬 Message</button>`;
    } else if (p.interest_sent) {
      actionBtn = `<button class="action-btn secondary" disabled>Waiting for match…</button>`;
    } else if (p.interest_received) {
      actionBtn = `<button class="action-btn" data-action="connect" data-pubkey="${p.enc_pubkey_hex}">Connect back</button>`;
    } else {
      actionBtn = `<button class="action-btn" data-action="connect" data-pubkey="${p.enc_pubkey_hex}">Connect</button>`;
    }

    return `
      <div class="profile-card">
        <div class="profile-header">
          <div class="avatar">
            ${p.photo_base64
              ? `<img src="data:image/png;base64,${p.photo_base64}">`
              : '🧑'}
          </div>
          <div>
            <div class="name">${esc(p.display_name || 'Anonymous')}</div>
            ${meta ? `<div class="meta">${esc(meta)}</div>` : ''}
          </div>
        </div>
        ${matchStatusLabel(p)}
        ${p.bio ? `<div class="bio">${esc(p.bio)}</div>` : ''}
        ${p.interests && p.interests.length > 0 ? `
          <div class="interests">
            ${p.interests.map(i => `<span class="interest">${esc(i)}</span>`).join('')}
          </div>
        ` : ''}
        ${actionBtn}
      </div>
    `;
  }

  function buildThreads() {
    const byPubkey = {};
    state.messages.forEach(m => {
      if (!byPubkey[m.from_enc_pubkey_hex]) {
        byPubkey[m.from_enc_pubkey_hex] = { incoming: [], name: 'Anonymous' };
      }
      byPubkey[m.from_enc_pubkey_hex].incoming.push({
        body: m.body, at: m.received_at_ms, mine: false,
      });
    });
    state.sentMessages.forEach(m => {
      if (!byPubkey[m.pubkey]) byPubkey[m.pubkey] = { incoming: [], name: m.name };
      byPubkey[m.pubkey].outgoing = byPubkey[m.pubkey].outgoing || [];
      byPubkey[m.pubkey].outgoing.push({ body: m.body, at: m.at, mine: true });
    });
    state.nearby.forEach(p => {
      if (byPubkey[p.enc_pubkey_hex]) {
        byPubkey[p.enc_pubkey_hex].name = p.display_name || 'Anonymous';
      }
    });
    return Object.entries(byPubkey).map(([pubkey, t]) => {
      const all = [...(t.incoming || []), ...(t.outgoing || [])].sort((a, b) => b.at - a.at);
      return {
        pubkey,
        name: t.name,
        preview: all[0]?.body || '',
        incoming: t.incoming || [],
        outgoing: t.outgoing || [],
      };
    });
  }

  function esc(s) {
    if (!s) return '';
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function bindEvents() {
    document.querySelectorAll('.tag[data-interest]').forEach(tag => {
      tag.addEventListener('click', () => {
        const i = tag.dataset.interest;
        if (state.selectedInterests.has(i)) {
          state.selectedInterests.delete(i);
          tag.classList.remove('selected');
        } else {
          state.selectedInterests.add(i);
          tag.classList.add('selected');
        }
        state.profile.interests = [...state.selectedInterests];
      });
    });

    const photoInput = document.getElementById('f-photo');
    if (photoInput) {
      photoInput.addEventListener('change', async (e) => {
        const file = e.target.files?.[0];
        if (!file) return;
        if (file.size > MAX_PHOTO_BYTES) {
          alert('Photo must be 50KB or smaller.');
          e.target.value = '';
          return;
        }
        const reader = new FileReader();
        reader.onload = () => {
          const dataUrl = reader.result;
          const base64 = String(dataUrl).split(',')[1];
          state.profile.photo_base64 = base64;
          const preview = document.getElementById('photo-preview');
          if (preview) {
            preview.classList.add('visible');
            preview.innerHTML = `<img src="${dataUrl}">`;
          }
        };
        reader.readAsDataURL(file);
      });
    }

    const startBtn = document.getElementById('start-btn');
    if (startBtn) {
      startBtn.addEventListener('click', async () => {
        state.profile.display_name = document.getElementById('f-name')?.value?.trim() || '';
        state.profile.bio = document.getElementById('f-bio')?.value?.trim() || '';
        const age = document.getElementById('f-age')?.value;
        state.profile.age = age ? parseInt(age, 10) : null;
        state.profile.gender = document.getElementById('f-gender')?.value?.trim() || '';
        state.profile.looking_for = document.getElementById('f-looking')?.value?.trim() || '';
        state.ttl = parseInt(document.getElementById('f-ttl')?.value || '300', 10);

        const payload = { ...state.profile };
        if (!payload.gender) delete payload.gender;
        if (!payload.display_name) payload.display_name = '';
        if (!payload.bio) payload.bio = '';
        if (!payload.looking_for) payload.looking_for = '';

        try {
          await window.mycelium?.startProximity?.(payload, state.ttl);
          state.screen = 'active';
          state.lastMessageMs = 0;
          render();
        } catch (e) {
          alert('Could not start: ' + e.message);
        }
      });
    }

    const stopBtn = document.getElementById('stop-btn');
    if (stopBtn) {
      stopBtn.addEventListener('click', async () => {
        await window.mycelium?.stopProximity?.();
        state.screen = 'setup';
        state.nearby = [];
        state.messages = [];
        state.sentMessages = [];
        state.activeChat = null;
        render();
      });
    }

    document.querySelectorAll('.tab[data-tab]').forEach(tab => {
      tab.addEventListener('click', () => {
        state.tab = tab.dataset.tab;
        state.activeChat = null;
        render();
      });
    });

    document.querySelectorAll('[data-action="connect"]').forEach(btn => {
      btn.addEventListener('click', async () => {
        const pubkey = btn.dataset.pubkey;
        btn.disabled = true;
        try {
          const result = await window.mycelium?.connectProximity?.(pubkey);
          await loadNearby();
          if (state.nearby.find(p => p.enc_pubkey_hex === pubkey)?.is_mutual) {
            alert("It's a match! You can message each other.");
          }
        } catch (e) {
          alert('Could not connect: ' + e.message);
          btn.disabled = false;
        }
      });
    });

    document.querySelectorAll('[data-action="message"]').forEach(btn => {
      btn.addEventListener('click', () => openChat(btn.dataset.pubkey));
    });

    document.querySelectorAll('.thread-card[data-pubkey]').forEach(card => {
      card.addEventListener('click', () => openChat(card.dataset.pubkey));
    });

    const chatBack = document.getElementById('chat-back');
    if (chatBack) {
      chatBack.addEventListener('click', () => {
        state.activeChat = null;
        render();
      });
    }

    const chatSend = document.getElementById('chat-send');
    if (chatSend) {
      chatSend.addEventListener('click', sendChatMessage);
      document.getElementById('chat-input')?.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') sendChatMessage();
      });
    }
  }

  function openChat(pubkey) {
    const profile = state.nearby.find(p => p.enc_pubkey_hex === pubkey);
    const threads = buildThreads();
    const thread = threads.find(t => t.pubkey === pubkey);
    state.activeChat = {
      pubkey,
      name: profile?.display_name || thread?.name || 'Anonymous',
      incoming: thread?.incoming || [],
      outgoing: thread?.outgoing || [],
    };
    render();
    setTimeout(() => {
      const el = document.getElementById('chat-messages');
      if (el) el.scrollTop = el.scrollHeight;
    }, 50);
  }

  async function sendChatMessage() {
    const input = document.getElementById('chat-input');
    const msg = input?.value?.trim();
    if (!msg || !state.activeChat) return;
    const pubkey = state.activeChat.pubkey;
    try {
      await window.mycelium?.sendProximityMessage?.(pubkey, msg);
      const entry = { body: msg, at: Date.now(), mine: true };
      state.activeChat.outgoing.push(entry);
      state.sentMessages.push({
        pubkey,
        name: state.activeChat.name,
        body: msg,
        at: Date.now(),
      });
      input.value = '';
      render();
    } catch (e) {
      alert('Failed to send: ' + e.message);
    }
  }

  async function loadNearby() {
    try {
      const profiles = await window.mycelium?.nearbyProfiles?.() || [];
      state.nearby = profiles.filter(p => !p.has_expired);
      if (state.screen === 'active' && !state.activeChat) {
        document.getElementById('count-badge').textContent =
          `${state.nearby.length} nearby`;
        const content = document.getElementById('tab-content');
        if (content && state.tab === 'nearby') {
          content.innerHTML = renderNearbyList();
          bindEvents();
        }
      }
    } catch (e) {
      console.error('loadNearby error:', e);
    }
  }

  async function loadMessages() {
    try {
      const msgs = await window.mycelium?.proximityMessages?.(state.lastMessageMs) || [];
      if (msgs.length > 0) {
        state.messages.push(...msgs);
        state.lastMessageMs = Math.max(
          state.lastMessageMs,
          ...msgs.map(m => m.received_at_ms)
        );
      }
      if (state.activeChat) {
        const thread = buildThreads().find(t => t.pubkey === state.activeChat.pubkey);
        if (thread) {
          state.activeChat.incoming = thread.incoming;
          state.activeChat.outgoing = thread.outgoing;
        }
      }
      if (state.screen === 'active' && !state.activeChat) {
        const content = document.getElementById('tab-content');
        if (content && state.tab === 'messages') {
          content.innerHTML = renderMessagesList();
          bindEvents();
        }
        document.querySelectorAll('.tab[data-tab="messages"]').forEach(tab => {
          tab.textContent = state.messages.length
            ? `Messages (${state.messages.length})`
            : 'Messages';
        });
      }
    } catch (e) {
      console.error('loadMessages error:', e);
    }
  }

  setInterval(() => {
    if (state.screen === 'active') {
      loadNearby();
      loadMessages();
    }
  }, 5000);

  render();
