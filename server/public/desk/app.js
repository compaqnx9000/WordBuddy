const state = {
  view: (location.hash || '#/lookup').replace('#/', '') || 'lookup',
  token: localStorage.getItem('wb.token') || '',
  me: null,
  points: null,
  entry: null,
  homophones: [],
  savedWordId: null,
  books: [],
  bookId: Number(localStorage.getItem('wb.book') || 0),
  words: [],
  wordCursor: null,
  wordTotal: 0,
  selected: null,
  revealed: false,
  shorts: [],
  shortIndex: 0,
  showFavorites: false,
  favoriteShorts: [],
  favoriteBusy: false,
  favoriteLoaded: false,
  favoriteSelected: [],
  favoriteRemoving: false,
  favReturn: 'books',
  shows: [],
  showId: '',
  podShelf: true,
  episodeId: '',
  playingShowId: '',
  meTab: 'home',
  gifts: [],
  giftCats: [],
  giftCat: 'recommend',
  ledger: null,
  checkIn: null,
  orders: [],
  pointPacks: null,
  imagePacks: null,
  withdrawCfg: null,
  withdrawals: [],
  invite: null,
  deletion: null,
  accent: localStorage.getItem('wb.accent') || 'us',
  theme: localStorage.getItem('wb.theme') || 'frost',
  font: localStorage.getItem('wb.font') || '1',
  reminder: localStorage.getItem('wb.reminder') === '1',
  imageProvider: localStorage.getItem('wb.imageProvider') || 'pollinations',
  autoSpeak: localStorage.getItem('wb.autoSpeak') !== '0',
  podcastBackground: localStorage.getItem('wb.podcastBg') === '1',
  shortsMeta: localStorage.getItem('wb.shortsMeta') !== '0',
  shortLoop: localStorage.getItem('wb.shortLoop') === '1',
  shortMetaShown: null,
  defaultBook: Number(localStorage.getItem('wb.defaultBook') || 0),
  biometric: localStorage.getItem('wb.biometric') === '1',
  passwordOpen: false,
  cacheLabel: '计算中…',
  cacheBusy: false,
  podcastPausedByHide: false,
  wordOrigin: 0,
  wordFilter: '',
  wordMatchTotal: 0,
  cardPhonics: false,
  cardExample: 0,
  cardRelated: 0,
  cardPlaying: false,
  cardShuffle: false,
  cardSeek: false,
  cardEdit: false,
  cardNotes: [],
  cardFavorited: false,
  cardImage: '',
  cardImageFor: 0,
  cardImageBusy: false,
  cardTimer: 0,
  busy: '',
}

const NAV = [
  ['lookup', '查词', '<circle cx="11" cy="11" r="6.5"/><path d="m20 20-3.6-3.6"/>'],
  ['books', '生词本', '<path d="M5 5.5A2.5 2.5 0 0 1 7.5 3H19v16H7.5A2.5 2.5 0 0 0 5 21.5z"/><path d="M5 5.5V21"/>'],
  ['shorts', '短视频', '<rect x="3.5" y="5" width="17" height="14" rx="2.5"/><path d="m10 9 5 3-5 3z"/>'],
  ['podcast', '播客', '<path d="M12 3a4 4 0 0 0-4 4v5a4 4 0 0 0 8 0V7a4 4 0 0 0-4-4z"/><path d="M6 11a6 6 0 0 0 12 0"/><path d="M12 17v4"/>'],
]

const THEMES = [
  ['cyber', '赛博霓虹', '#00f0ff'],
  ['emerald', '翡翠薄雾', '#2dd4bf'],
  ['electric', '电光潮汐', '#38bdf8'],
  ['solar', '日光金辉', '#f5c542'],
  ['aurora', '极光粉', '#f472b6'],
  ['ember', '余烬珊瑚', '#ff6b6b'],
  ['frost', '霜蓝', '#0e8a96'],
  ['forest', '星野翠绿', '#8cc63f', '/desk/wallpapers/bg_forest_star.jpg'],
  ['sponge', '海绵宝宝', '#8a3e08', '/desk/wallpapers/bg_spongebob.jpg'],
]

const VINYL = {
  'npr-live': ['#0f1a24', '#2e7dff'],
  'lbc-live': ['#1a1010', '#e53935'],
  'cri-live': ['#1c0e10', '#e53935'],
  'china-plus-special': ['#1a1014', '#c62828'],
  'china-plus-headlines': ['#14181c', '#d4a017'],
  allears: ['#1a1218', '#e84393'],
  bbc6min: ['#1b1b1f', '#bb1919'],
  englishpod: ['#12181f', '#0984e3'],
  'bbc-conversations': ['#141820', '#0b7a75'],
  'daily-easy': ['#1a1610', '#f39c12'],
  luke: ['#1a1423', '#6c5ce7'],
  'ielts-energy': ['#16121a', '#a29bfe'],
  reallife: ['#101816', '#00b894'],
  'american-english': ['#12161c', '#74b9ff'],
  'listening-time': ['#1c1510', '#e17055'],
}

function mediaUrl(url) {
  const raw = String(url || '').trim()
  if (!raw) return ''
  try {
    const parsed = new URL(raw, location.origin)
    const ownHost = parsed.hostname === location.hostname
      || parsed.hostname === 'wordbuddy.cc'
      || parsed.hostname === 'www.wordbuddy.cc'
      || parsed.hostname === '47.95.111.238'
    if (ownHost && parsed.pathname.startsWith('/uploads/')) return `${parsed.pathname}${parsed.search}`
  } catch {
    return raw
  }
  return raw
}

function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, (ch) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[ch]))
}

function toast(message) {
  const el = document.getElementById('toast')
  el.textContent = message
  el.hidden = false
  clearTimeout(toast.timer)
  toast.timer = setTimeout(() => { el.hidden = true }, 3200)
}

async function api(path, options = {}) {
  const headers = { Accept: 'application/json', 'X-Device-Platform': 'Web' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (options.auth !== false && state.token) headers.Authorization = `Bearer ${state.token}`
  const response = await fetch(path, {
    method: options.method || 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })
  const data = await response.json().catch(() => ({}))
  if (response.status === 401) {
    logout(false)
    throw new Error(data.error || '请重新登录')
  }
  if (!response.ok) throw new Error(data.error || '请求失败')
  return data
}

function logout(rerender = true) {
  state.token = ''
  state.me = null
  state.points = null
  state.books = []
  state.words = []
  state.selected = null
  state.favoriteShorts = []
  state.favoriteLoaded = false
  state.favoriteBusy = false
  state.favoriteSelected = []
  state.favoriteRemoving = false
  localStorage.removeItem('wb.token')
  paintChrome()
  if (rerender) render()
}

function applyPrefs() {
  document.body.dataset.theme = state.theme
  document.documentElement.style.fontSize = `${Number(state.font) * 16}px`
}

let voiceAudio = null
let voiceSeq = 0

function stopVoice() {
  voiceSeq += 1
  if (voiceAudio) {
    voiceAudio.pause()
    voiceAudio.removeAttribute('src')
    voiceAudio.load()
    voiceAudio = null
  }
  if (window.speechSynthesis) speechSynthesis.cancel()
}

function speakSystem(text, slow) {
  if (!window.speechSynthesis) return
  const seq = voiceSeq
  const pieces = slow ? text.split(/\s+/).filter(Boolean) : [text]
  let index = 0
  const next = () => {
    if (seq !== voiceSeq || index >= pieces.length) return
    const utter = new SpeechSynthesisUtterance(pieces[index])
    utter.lang = state.accent === 'uk' ? 'en-GB' : 'en-US'
    utter.rate = slow ? 0.55 : 0.9
    index += 1
    utter.onend = () => {
      if (seq !== voiceSeq) return
      if (slow && index < pieces.length) window.setTimeout(next, 220)
      else next()
    }
    utter.onerror = () => next()
    speechSynthesis.speak(utter)
  }
  window.setTimeout(next, 40)
}

function speak(text, slow) {
  const line = String(text || '').trim()
  if (!line) return
  stopVoice()
  const seq = voiceSeq
  const rate = slow ? 0.55 : 1
  const audio = new Audio()
  voiceAudio = audio
  audio.referrerPolicy = 'no-referrer'
  audio.preload = 'auto'
  audio.preservesPitch = true
  const type = state.accent === 'uk' ? 'uk' : 'us'
  audio.src = `/voice?text=${encodeURIComponent(line)}&accent=${type}`
  const applyRate = () => {
    if (seq !== voiceSeq) return
    audio.playbackRate = rate
  }
  audio.addEventListener('loadedmetadata', applyRate)
  audio.addEventListener('playing', applyRate)
  let fellBack = false
  const fallback = () => {
    if (fellBack || seq !== voiceSeq) return
    fellBack = true
    speakSystem(line, slow)
  }
  audio.addEventListener('error', fallback)
  audio.play().then(applyRate).catch(fallback)
}

function speakOnce(text, slow) {
  return new Promise((resolve) => {
    const line = String(text || '').trim()
    if (!line || !window.speechSynthesis) return resolve()
    const utter = new SpeechSynthesisUtterance(line)
    utter.lang = state.accent === 'uk' ? 'en-GB' : 'en-US'
    utter.rate = slow ? 0.55 : 0.9
    utter.onend = () => resolve()
    utter.onerror = () => resolve()
    speechSynthesis.speak(utter)
  })
}

async function speakSyllables(parts) {
  const cleaned = (parts || []).map((part) => String(part || '').trim()).filter(Boolean)
  if (!cleaned.length) return
  stopVoice()
  if (cleaned.length === 1) {
    speak(cleaned[0])
    return
  }
  for (let index = 0; index < cleaned.length; index += 1) {
    await speakOnce(cleaned[index])
    if (index < cleaned.length - 1) await new Promise((resolve) => setTimeout(resolve, 320))
  }
  await new Promise((resolve) => setTimeout(resolve, 280))
  speak(cleaned.join(''))
}

function shanghaiToday() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date())
}

function defLines(defs) {
  return (defs || []).map((item) => {
    if (typeof item === 'string') return item
    const pos = item.pos || ''
    const meaning = item.meaning || item.tran || item.zh || ''
    return `${pos} ${meaning}`.trim()
  }).filter(Boolean)
}

function defsHtml(defs) {
  return defLines(defs).map((line) => `<p class="def">${esc(line)}</p>`).join('') || '<p class="muted">暂无释义</p>'
}

function examplesHtml(list) {
  return (list || []).map((item) => {
    const en = item.english || item.en || ''
    const zh = item.chinese || item.zh || ''
    return `<p><b>${esc(en)}</b><br><span class="muted">${esc(zh)}</span></p>`
  }).join('')
}

function analyzePhonics(raw) {
  const source = String(raw || '')
  const lettersOnly = [...source].filter((ch) => /\p{L}/u.test(ch))
  if (!lettersOnly.length) return { syllables: [source], dotted: source }
  const lower = lettersOnly.join('').toLowerCase()
  const vowel = (ch) => 'aeiou'.includes(ch)
  const isVowelLetter = (index) => {
    const ch = lower[index]
    if (vowel(ch)) return true
    return ch === 'y' && index > 0
  }
  const silent = lower.split('').map((_, index) => {
    if (lower[index] !== 'e' || index < 2) return false
    if (vowel(lower[index - 1]) || lower[index - 1] === 'y') return false
    if (!vowel(lower[index - 2]) && lower[index - 2] !== 'y') return false
    if (index + 1 < lower.length && vowel(lower[index + 1])) return false
    return true
  })
  const nuclei = []
  for (let index = 0; index < lower.length; index += 1) {
    if (silent[index] || !isVowelLetter(index)) continue
    if (index > 0 && isVowelLetter(index - 1) && !silent[index - 1]) continue
    nuclei.push(index)
  }
  let lowerSyllables = [lower]
  if (nuclei.length) {
    const starts = [0]
    for (let s = 1; s < nuclei.length; s += 1) {
      const lastBetween = nuclei[s] - 1
      starts[s] = lastBetween > nuclei[s - 1] ? lastBetween : nuclei[s]
    }
    lowerSyllables = nuclei.map((_, s) => {
      const end = s + 1 < nuclei.length ? starts[s + 1] : lower.length
      return lower.slice(starts[s], end)
    }).filter(Boolean)
  }
  let cursor = 0
  const syllables = lowerSyllables.map((syl) => {
    const piece = lettersOnly.slice(cursor, cursor + syl.length).join('')
    cursor += syl.length
    return piece
  })
  return { syllables, dotted: syllables.join('·') }
}

function posClass(pos) {
  const key = String(pos || '').toLowerCase().trim().replace(/\.$/, '')
  if (key.startsWith('n') && !key.startsWith('num')) return 'pos-n'
  if (key.startsWith('adj') || key === 'a') return 'pos-adj'
  if (key.startsWith('v')) return 'pos-v'
  return 'pos-other'
}

function posLabel(pos) {
  const text = String(pos || '').trim()
  if (!text) return ''
  if (text.endsWith('.') || text.startsWith('【')) return text
  return `${text}.`
}

function highlightWord(sentence, word) {
  const text = String(sentence || '')
  const target = String(word || '').trim()
  if (!target) return esc(text)
  const lowerS = text.toLowerCase()
  const lowerW = target.toLowerCase()
  let start = lowerS.indexOf(lowerW)
  while (start >= 0) {
    const end = start + lowerW.length
    const leftOk = start === 0 || !/[a-z]/i.test(text[start - 1])
    const rightOk = end >= text.length || !/[a-z]/i.test(text[end])
    if (leftOk && rightOk) break
    start = lowerS.indexOf(lowerW, start + 1)
  }
  if (start < 0) start = lowerS.indexOf(lowerW)
  if (start < 0) return esc(text)
  const end = start + target.length
  return `${esc(text.slice(0, start))}<b class="head-hit">${esc(text.slice(start, end))}</b>${esc(text.slice(end))}`
}

function iconSpeaker() {
  return '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path fill="currentColor" d="M3 9v6h4l5 4V5L7 9H3zm13.5 3a4.5 4.5 0 0 0-2.5-4.03v8.06A4.5 4.5 0 0 0 16.5 12z"/></svg>'
}

function cardWordHtml(word) {
  const broken = esc(String(word.text || '').replace(/-/g, '-\u200B').replace(/\//g, '/\u200B'))
  const { syllables } = analyzePhonics(word.text)
  const parts = (syllables.length ? syllables : [word.text]).map((syl, index) => (
    `<span class="syl syl-${index % 4}">${esc(syl)}</span>`
  )).join('<span class="syl-dot">·</span>')
  return `<div class="card-word-slot ${state.cardPhonics ? 'phonics' : ''}">
    <div class="card-word">${broken}</div>
    <div class="card-word-wrap">${parts}</div>
  </div>`
}

function cardDefsHtml(defs) {
  const rows = (defs || []).map((item) => {
    if (typeof item === 'string') return { pos: '', meaning: item }
    return { pos: item.pos || '', meaning: item.meaning || item.tran || item.zh || '' }
  }).filter((item) => item.pos || item.meaning)
  if (!rows.length) return '<p class="muted">暂无释义</p>'
  return rows.map((item) => (
    `<div class="def-row"><span class="pos ${posClass(item.pos)}">${esc(posLabel(item.pos))}</span><span>${esc(item.meaning)}</span></div>`
  )).join('')
}

function relatedTabs(word) {
  return [
    ['近义词', word.synonyms || []],
    ['形近词', word.nearWords || []],
    ['反义词', word.antonyms || []],
  ].filter(([, list]) => list.length)
}

function wordTag(word) {
  const pos = (word.definitions || []).map((item) => (typeof item === 'string' ? '' : item.pos || '')).find(Boolean) || ''
  const key = pos.toLowerCase()
  let label = ''
  if (key.startsWith('v')) label = '动词'
  else if (key.startsWith('n') && !key.startsWith('num')) label = '名词'
  else if (key.startsWith('adj') || key === 'a') label = '形容词'
  else if (key.startsWith('adv')) label = '副词'
  else if (key.includes('phrase') || key.startsWith('phr')) label = '短语'
  if (!label) return ''
  if (/\s/.test(word.text || '') && !label.endsWith('短语')) label += '短语'
  return label
}

function studyCardHtml(word) {
  const ipa = [
    word.ipaUk ? `UK [${esc(word.ipaUk)}]` : '',
    word.ipaUs ? `US [${esc(word.ipaUs)}]` : '',
  ].filter(Boolean).join(' ')
  const image = state.cardImageFor === word.id && state.cardImage
    ? `<img src="${state.cardImage}" alt="" />`
    : `<span>${state.cardImageBusy ? '…' : '+'}</span>`
  const tabs = relatedTabs(word)
  const tabIndex = tabs.length ? Math.min(state.cardRelated, tabs.length - 1) : 0
  const related = tabs.length ? `<div class="card-related">
      <div class="seg">${tabs.map(([title], index) => (
        `<button type="button" class="${index === tabIndex ? 'on' : ''}" data-act="card-related" data-i="${index}">${title}</button>`
      )).join('')}</div>
      <div class="related-grid">${tabs[tabIndex][1].map((item) => (
        `<button type="button" class="related-cell" data-act="related-word" data-word="${esc(item)}">${esc(item)}</button>`
      )).join('')}</div>
    </div>` : ''
  const examples = word.examples || []
  const exampleIndex = examples.length ? Math.min(state.cardExample, examples.length - 1) : 0
  const example = examples[exampleIndex]
  const exampleHtml = example ? `<section class="glass-card example-card">
      <div class="example-head">
        <p class="card-kicker">经典例句</p>
        <div class="example-speed">
          <button type="button" class="speed" data-act="speak" data-text="${esc(example.english || example.en || '')}">正常</button>
          <button type="button" class="speed" data-act="speak-slow" data-text="${esc(example.english || example.en || '')}">慢速</button>
        </div>
      </div>
      <div class="example-viewport">${examplePageHtml(word, example)}</div>
      ${examples.length > 1 ? `<div class="example-dots">${examples.map((_, index) => (
        `<button type="button" class="dot ${index === exampleIndex ? 'on' : ''}" data-act="card-example" data-i="${index}" aria-label="例句 ${index + 1}"></button>`
      )).join('')}</div>` : ''}
    </section>` : ''
  const total = state.wordTotal || state.words.length
  const index = cardIndex()
  const locked = selectedNotebook()?.isLocked
  const editSource = locked ? state.cardNotes : word.definitions
  const tag = wordTag(word)
  const collocations = examples.map((item) => item.english || item.en || '').filter(Boolean).slice(0, 3)
  return `<section class="panel study-card">
    <div class="card-scroll">
      <div class="card-head">
        <div class="card-title">
          <div class="card-name-row">
            ${cardWordHtml(word)}
            ${tag ? `<span class="word-tag">${esc(tag)}</span>` : ''}
          </div>
          ${ipa ? `<p class="card-ipa">${ipa}</p>` : ''}
          <div class="card-tools">
            <button type="button" class="speak-orb" data-act="speak" data-text="${esc(word.text)}" aria-label="发音">${iconSpeaker()}</button>
            <div class="seg">
              <button type="button" class="${state.accent === 'us' ? 'on' : ''}" data-act="card-accent" data-id="us">US 美音</button>
              <button type="button" class="${state.accent === 'uk' ? 'on' : ''}" data-act="card-accent" data-id="uk">UK 英音</button>
            </div>
            <div class="seg card-phonics-seg">
              <button type="button" class="${state.cardPhonics ? '' : 'on'}" data-act="card-phonics" data-on="0">正常</button>
              <button type="button" class="${state.cardPhonics ? 'on' : ''}" data-act="card-phonics" data-on="1">自然拼读</button>
            </div>
          </div>
        </div>
        <div class="card-corner">
          <button type="button" class="fav-chip ${state.cardFavorited ? 'saved' : ''}" data-act="card-favorite"><span>${state.cardFavorited ? '★' : '☆'}</span> 收藏到…</button>
          <button type="button" class="mnemonic-plus" data-act="card-image" aria-label="助记配图">${image}</button>
        </div>
      </div>
      <section class="glass-card">
        ${cardDefsHtml(word.definitions)}
        ${collocations.length ? `<p class="colloc"><span>常搭配</span>${esc(collocations.join(' / '))}</p>` : ''}
        <div id="card-notes">${state.cardNotes?.length ? `<p class="card-kicker">我的补充</p>${cardDefsHtml(state.cardNotes)}` : ''}</div>
        <div class="card-edit-row">
          <button type="button" class="edit-meaning" data-act="card-edit">编辑释义</button>
          ${locked ? '' : '<button type="button" class="ghost tiny" data-act="delete-word">删除</button>'}
        </div>
        ${state.cardEdit ? `<form id="edit-word" class="set-form">
          <div class="field"><textarea name="meaning" rows="4">${esc(defLines(editSource).join('\n'))}</textarea></div>
          <button class="primary" type="submit">保存释义</button>
        </form>` : ''}
      </section>
      ${related}
      ${exampleHtml}
    </div>
    <div class="card-bar">
      ${state.cardSeek && total > 1 ? `<input id="card-seek" type="range" min="0" max="${total - 1}" value="${index}" />` : ''}
      <div class="card-transport">
        <div class="card-controls">
          <button type="button" class="${state.cardShuffle ? 'on' : ''}" data-act="card-shuffle" aria-label="随机">⇄</button>
          <button type="button" data-act="prev-word" aria-label="上一张">⏮</button>
          <button type="button" class="play-orb" data-act="card-play" aria-label="${state.cardPlaying ? '暂停' : '播放'}">${state.cardPlaying ? '❚❚' : '▶'}</button>
          <button type="button" data-act="next-word" aria-label="下一张">⏭</button>
          <button type="button" class="${state.autoSpeak ? 'on' : ''}" data-act="card-mute" aria-label="${state.autoSpeak ? '关闭朗读' : '开启朗读'}">${state.autoSpeak ? iconSpeaker() : '🔇'}</button>
        </div>
        <div class="card-meta">
          <span class="card-progress" id="card-page">${index + 1} / ${Number(total).toLocaleString('en-US')}</span>
          <button type="button" class="card-menu ${state.cardSeek ? 'on' : ''}" data-act="card-seek" aria-label="进度">☰</button>
        </div>
      </div>
    </div>
  </section>`
}

function examplePageHtml(word, example) {
  const english = example?.english || example?.en || ''
  const chinese = example?.chinese || example?.zh || ''
  return `<div class="example-page">
    <p class="example-en">${highlightWord(english, word?.text || '')}</p>
    <p class="example-zh">${esc(chinese)}</p>
  </div>`
}

let exampleBusy = false

async function stepExample(delta) {
  const word = state.selected
  const examples = word?.examples || []
  const viewport = document.querySelector('.example-viewport')
  const current = viewport?.querySelector('.example-page')
  if (!word || examples.length < 2 || exampleBusy || !viewport || !current) {
    if (word && examples.length) {
      const next = Math.min(Math.max(state.cardExample + delta, 0), examples.length - 1)
      if (next !== state.cardExample) {
        state.cardExample = next
        render()
      }
    }
    return
  }
  const next = state.cardExample + delta
  if (delta === 0 || next < 0 || next >= examples.length) return
  exampleBusy = true
  const width = viewport.clientWidth
  const holder = document.createElement('div')
  holder.innerHTML = examplePageHtml(word, examples[next])
  const incoming = holder.firstElementChild
  incoming.style.width = `${width}px`
  current.style.width = `${width}px`
  const track = document.createElement('div')
  track.className = 'example-track'
  track.style.width = `${width * 2}px`
  if (delta > 0) {
    track.append(current, incoming)
    track.style.transform = 'translateX(0px)'
  } else {
    track.append(incoming, current)
    track.style.transform = `translateX(${-width}px)`
  }
  viewport.replaceChildren(track)
  const height = Math.max(current.offsetHeight, incoming.offsetHeight)
  viewport.style.height = `${height}px`
  await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
  track.style.transition = 'transform 420ms cubic-bezier(0.22, 0.61, 0.36, 1)'
  track.style.transform = delta > 0 ? `translateX(${-width}px)` : 'translateX(0px)'
  await new Promise((resolve) => {
    const finish = (event) => {
      if (event && event.target !== track) return
      track.removeEventListener('transitionend', finish)
      resolve()
    }
    track.addEventListener('transitionend', finish)
    setTimeout(resolve, 520)
  })
  incoming.style.width = ''
  viewport.style.height = ''
  viewport.replaceChildren(incoming)
  state.cardExample = next
  document.querySelectorAll('.example-dots .dot').forEach((dot, index) => {
    dot.classList.toggle('on', index === next)
  })
  const english = examples[next].english || examples[next].en || ''
  document.querySelectorAll('.example-actions [data-text]').forEach((button) => {
    button.dataset.text = english
  })
  exampleBusy = false
}

let accountMenuOpen = false

function menuIcon(body) {
  return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${body}</svg>`
}

function paintChrome() {
  const nav = document.getElementById('nav')
  nav.innerHTML = NAV.map(([id, label, icon]) => {
    const badge = id === 'books' && state.selected && state.view === 'books'
      ? `<em class="nav-badge">${cardIndex() + 1}</em>` : ''
    return `<button type="button" data-view="${id}" class="${state.view === id ? 'active' : ''}">${menuIcon(icon)}${label}${badge}</button>`
  }).join('')
  const user = state.me?.user
  const foot = document.getElementById('side-user')
  const name = user ? esc(user.nickname || user.phone || '已登录') : '未登录'
  const sub = user ? (state.points == null ? '—' : Number(state.points).toLocaleString('en-US')) : '访客'
  const avatar = user?.avatarUrl
    ? `<img class="side-avatar" src="${esc(mediaUrl(user.avatarUrl))}" alt="" />`
    : `<span class="side-avatar fallback">${esc(user ? (user.nickname || user.phone || '词').slice(0, 1) : '登')}</span>`
  const gear = menuIcon('<path d="M9.6 3.8h4.8l.3 1.8c.5.2 1 .5 1.4.9l1.7-.9 2.4 2.4-.9 1.7c.4.4.7.9.9 1.4l1.8.3v4.8l-1.8.3c-.2.5-.5 1-.9 1.4l.9 1.7-2.4 2.4-1.7-.9c-.4.4-.9.7-1.4.9l-.3 1.8H9.6l-.3-1.8a5.6 5.6 0 0 1-1.4-.9l-1.7.9-2.4-2.4.9-1.7a5.6 5.6 0 0 1-.9-1.4l-1.8-.3V9.4l1.8-.3c.2-.5.5-1 .9-1.4l-.9-1.7 2.4-2.4 1.7.9c.4-.4.9-.7 1.4-.9l.3-1.8Z"/><circle cx="12" cy="12" r="2.3"/>')
  const person = menuIcon('<circle cx="12" cy="8" r="3.1"/><path d="M5.4 19.2a6.6 6.6 0 0 1 13.2 0"/>')
  const bookmark = menuIcon('<path d="M7.2 4.2h9.6a1.2 1.2 0 0 1 1.2 1.2V20l-6-3.4L6 20V5.4a1.2 1.2 0 0 1 1.2-1.2Z"/>')
  const enter = menuIcon('<path d="M14 4.5h3.5A1.5 1.5 0 0 1 19 6v12a1.5 1.5 0 0 1-1.5 1.5H14"/><path d="M3.5 12H13"/><path d="m10 8.5 3.5 3.5L10 15.5"/>')
  const leave = menuIcon('<path d="M10 4.5H6.5A1.5 1.5 0 0 0 5 6v12a1.5 1.5 0 0 0 1.5 1.5H10"/><path d="M10 12h9.5"/><path d="m16.5 8.5 3.5 3.5-3.5 3.5"/>')
  const account = state.token
    ? `<button type="button" class="account-item" data-act="open-profile">${person}个人资料</button>
       <button type="button" class="account-item ${state.view === 'favorites' ? 'on' : ''}" data-act="open-favorites">${bookmark}我的收藏</button>
       <button type="button" class="account-item ${state.view === 'settings' ? 'on' : ''}" data-act="open-settings">${gear}设置</button>
       <div class="account-sep"></div>
       <button type="button" class="account-item" data-act="logout-confirm">${leave}退出登录</button>`
    : `<button type="button" class="account-item" data-act="open-login">${enter}登录 / 注册</button>
       <button type="button" class="account-item ${state.view === 'settings' ? 'on' : ''}" data-act="open-settings">${gear}设置</button>`
  foot.classList.toggle('open', accountMenuOpen)
  foot.innerHTML = `<div class="account-menu">
      <div class="account-head">${avatar}<span><strong>${name}</strong><small>${sub}</small></span></div>
      <div class="account-sep"></div>
      ${account}
    </div>
    <button type="button" class="side-user" data-act="toggle-account">
      ${avatar}
      <span class="side-id"><span class="side-name">${name}</span><span class="side-sub">${user ? '<i class="coin"></i>' : ''}${sub}</span></span>
      ${menuIcon('<path d="m9 6 6 6-6 6"/>').replace('aria-hidden="true"', 'class="side-chevron" aria-hidden="true"')}
    </button>`
}
function setView(view) {
  if (view !== 'books') stopCardPlay()
  if (view === 'me' && state.meTab === 'settings') state.meTab = 'home'
  if (view === 'settings') state.meTab = 'settings'
  if (view === 'podcast') state.podShelf = true
  state.view = view
  history.replaceState(null, '', `#/${view}`)
  render()
  if (view === 'books' && !state.books.length) loadBooks().catch((error) => toast(error.message))
  if (view === 'shorts' && !state.shorts.length) loadShorts(true).catch((error) => toast(error.message))
  if (view === 'favorites') {
    state.favoriteBusy = true
    loadFavoriteShorts().catch((error) => toast(error.message))
  }
  if (view === 'podcast' && !state.shows.length) loadShows().catch((error) => toast(error.message))
  if (view === 'me' || view === 'settings') loadMeTab().catch((error) => toast(error.message))
}

async function refreshMe() {
  if (!state.token) return
  const me = await api('/me')
  if (me.token) {
    state.token = me.token
    localStorage.setItem('wb.token', me.token)
  }
  state.me = me
  const check = await api('/me/checkin')
  state.checkIn = check.checkIn
  state.points = check.checkIn?.totalPoints ?? null
  paintChrome()
}

function render() {
  paintChrome()
  const main = document.getElementById('main')
  main.classList.toggle('shorts-main', state.view === 'shorts')
  main.classList.toggle('books-main', state.view === 'books')
  if (state.view === 'lookup') main.innerHTML = lookupHtml()
  else if (state.view === 'books') main.innerHTML = booksHtml()
  else if (state.view === 'shorts') main.innerHTML = shortsHtml()
  else if (state.view === 'podcast') main.innerHTML = podcastHtml()
  else if (state.view === 'settings') main.innerHTML = `<section class="panel">${settingsHtml()}</section>`
  else if (state.view === 'favorites') main.innerHTML = favoritesHtml()
  else main.innerHTML = meHtml()
  if (state.view === 'podcast') bindPodcastPlayer()
  if (state.view === 'shorts') bindShortVideo()
  if (state.view === 'books') bindWordList()
}

function lookupHtml() {
  const entry = state.entry
  const query = esc(entry?.text || '')
  const search = `<form id="lookup-form" class="quick-search lookup-search">
    <input name="q" type="search" placeholder="查单词或短语，回车" autocomplete="off" value="${query}" />
    <button type="submit">查词</button>
  </form>`
  if (!entry) {
    return `<section class="panel">${search}<h1>查词</h1><p class="muted">输入单词或短语后回车。可以听发音、写入生词本、记谐音、写自己的释义，也可以生成配图。</p></section>`
  }
  const chips = (entry.synonyms || []).map((word) => `<button class="chip" data-act="lookup" data-word="${esc(word)}">${esc(word)}</button>`).join('')
  const near = (entry.nearWords || []).map((word) => `<button class="chip" data-act="lookup" data-word="${esc(word)}">${esc(word)}</button>`).join('')
  const tips = state.homophones.map((tip) => (
    `<div class="row"><div>${esc(tip.body)}</div><div class="muted">赞 ${tip.likeCount || 0}</div>
      <button class="tiny" data-act="like-tip" data-id="${tip.id}">${tip.likedByMe ? '已赞' : '点赞'}</button></div>`
  )).join('')
  return `<section class="panel">
    ${search}
    <h1 class="headword">${esc(entry.text)}</h1>
    <p>${entry.ipaUk ? `<span class="ipa">英 /${esc(entry.ipaUk)}/</span> ` : ''}${entry.ipaUs ? `<span class="ipa">美 /${esc(entry.ipaUs)}/</span>` : ''}</p>
    <div class="actions">
      <button class="primary" data-act="speak" data-text="${esc(entry.text)}">朗读</button>
      <button class="ghost" data-act="speak-slow" data-text="${esc(entry.text)}">慢读</button>
      <button class="ghost" data-act="accent">${state.accent === 'uk' ? '正在用英音' : '正在用美音'}</button>
      <button class="primary" data-act="save-entry">${state.savedWordId ? '移出生词本' : '加入生词本'}</button>
      <button class="ghost" data-act="mnemonic">生成配图</button>
    </div>
    <div id="mnemonic"></div>
    ${defsHtml(entry.definitions)}
    <h3>例句</h3>
    ${examplesHtml(entry.examples) || '<p class="muted">暂无例句</p>'}
    ${chips ? `<h3>近义</h3><div class="chips">${chips}</div>` : ''}
    ${near ? `<h3>相近词</h3><div class="chips">${near}</div>` : ''}
    <h3>谐音助记</h3>
    <form id="tip-form" class="quick-search"><input name="body" maxlength="120" placeholder="写一条谐音，最多 120 字" /><button type="submit">发布</button></form>
    <div class="list">${tips || '<p class="muted">还没有谐音</p>'}</div>
    <h3>我的释义</h3>
    <form id="note-form">
      <div class="field"><textarea name="meaning" rows="3" placeholder="只保存到你的账号，不改公共词库">${esc(defLines(state.notes).join('\n'))}</textarea></div>
      <button class="primary" type="submit">保存释义</button>
    </form>
  </section>`
}

function ms(path) {
  return `<svg class="ms" viewBox="0 -960 960 960" aria-hidden="true"><path fill="currentColor" d="${path}"/></svg>`
}

const MS = {
  pets: 'M180-475q-42 0-71-29t-29-71q0-42 29-71t71-29q42 0 71 29t29 71q0 42-29 71t-71 29Zm109-189q-29-29-29-71t29-71q29-29 71-29t71 29q29 29 29 71t-29 71q-29 29-71 29t-71-29Zm240 0q-29-29-29-71t29-71q29-29 71-29t71 29q29 29 29 71t-29 71q-29 29-71 29t-71-29Zm251 189q-42 0-71-29t-29-71q0-42 29-71t71-29q42 0 71 29t29 71q0 42-29 71t-71 29ZM266-75q-45 0-75.5-34.5T160-191q0-52 35.5-91t70.5-77q29-31 50-67.5t50-68.5q22-26 51-43t63-17q34 0 63 16t51 42q28 32 49.5 69t50.5 69q35 38 70.5 77t35.5 91q0 47-30.5 81.5T694-75q-54 0-107-9t-107-9q-54 0-107 9t-107 9Z',
  star: 'm233-120 65-281L80-590l288-25 112-265 112 265 288 25-218 189 65 281-247-149-247 149Z',
  school: 'M480-120 200-272v-240L40-600l440-240 440 240v320h-80v-276l-80 44v240L480-120Zm0-332 274-148-274-148-274 148 274 148Zm0 241 200-108v-151L480-360 280-470v151l200 108Z',
  military: 'M280-880h400v314q0 23-10 41t-28 29l-142 84 28 92h152l-124 88 48 152-124-94-124 94 48-152-124-88h152l28-92-142-84q-18-11-28-29t-10-41v-314Zm80 80v234l80 48v-282h-80Zm240 0h-80v282l80-48v-234Z',
  premium: 'm387-412 35-114-92-74h114l36-112 36 112h114l-93 74 35 114-92-71-93 71ZM240-40v-309q-38-42-59-96t-21-115q0-134 93-227t227-93q134 0 227 93t93 227q0 61-21 115t-59 96v309l-240-80-240 80Zm410-350q70-70 70-170t-70-170q-70-70-170-70t-170 70q-70 70-70 170t70 170q70 70 170 70t170-70ZM320-159l160-41 160 41v-124q-35 20-75.5 31.5T480-240q-44 0-84.5-11.5T320-283v124Z',
  verified: 'm344-60-76-128-144-32 14-148-98-112 98-112-14-148 144-32 76-128 136 58 136-58 76 128 144 32-14 148 98 112-98 112 14 148-144 32-76 128-136-58-136 58Zm34-102 102-44 104 44 56-96 110-26-10-112 74-84-74-86 10-112-110-24-58-96-102 44-104-44-56 96-110 24 10 112-74 86 74 84-10 114 110 24 58 96Zm-42 142 226-226-56-58-170 170-86-84-56 56 142 142Z',
  flight: 'M120-120v-80h720v80H120Zm70-200L40-570l96-26 112 94 140-37-207-276 116-31 299 251 170-46q32-9 60.5 7.5T864-585q9 32-7.5 60.5T808-487L190-320Z',
  public: 'M324-111.5Q251-143 197-197t-85.5-127Q80-397 80-480t31.5-156Q143-709 197-763t127-85.5Q397-880 480-880t156 31.5Q709-817 763-763t85.5 127Q880-563 880-480t-31.5 156Q817-251 763-197t-127 85.5Q563-80 480-80t-156-31.5ZM440-162v-78q-33 0-56.5-23.5T360-320v-40L168-552q-3 18-5.5 36t-2.5 36q0 121 79.5 212T440-162Zm276-102q41-45 62.5-100.5T800-480q0-98-54.5-179T600-776v16q0 33-23.5 56.5T520-680h-80v80q0 17-11.5 28.5T400-560h-80v80h240q17 0 28.5 11.5T600-440v120h40q26 0 47 15.5t29 40.5Z',
  book: 'M560-564v-68q33-14 67.5-21t72.5-7q26 0 51 4t49 10v64q-24-9-48.5-13.5T700-600q-38 0-73 9.5T560-564Zm0 220v-68q33-14 67.5-21t72.5-7q26 0 51 4t49 10v64q-24-9-48.5-13.5T700-380q-38 0-73 9t-67 27Zm0-110v-68q33-14 67.5-21t72.5-7q26 0 51 4t49 10v64q-24-9-48.5-13.5T700-490q-38 0-73 9.5T560-454ZM260-320q47 0 91.5 10.5T440-278v-394q-41-24-87-36t-93-12q-36 0-71.5 7T120-692v396q35-12 69.5-18t70.5-6Zm260 42q44-21 88.5-31.5T700-320q36 0 70.5 6t69.5 18v-396q-33-14-68.5-21t-71.5-7q-47 0-93 12t-87 36v394Zm-40 118q-48-38-104-59t-116-21q-42 0-82.5 11T100-198q-21 11-40.5-1T40-234v-482q0-11 5.5-21T62-752q46-24 96-36t102-12q58 0 113.5 15T480-740q51-30 106.5-45T700-800q52 0 102 12t96 36q11 5 16.5 15t5.5 21v482q0 23-19.5 35t-40.5 1q-37-20-77.5-31T700-240q-60 0-116 21t-104 59Z',
  folder: 'm504-292 92-70 92 70-34-114 92-74H632l-36-112-36 112H446l92 74-34 114ZM160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h240l80 80h320q33 0 56.5 23.5T880-640v400q0 33-23.5 56.5T800-160H160Z',
  add: 'M440-440H200v-80h240v-240h80v240h240v80H520v240h-80v-240Z',
  up: 'M440-160v-487L216-423l-56-57 320-320 320 320-56 57-224-224v487h-80Z',
  down: 'M440-800v487L216-537l-56 57 320 320 320-320-56-57-224 224v-487h-80Z',
  bookmark: 'M200-120v-640q0-33 23.5-56.5T280-840h280q-20 30-30 57.5T520-720q0 72 45.5 127T680-524q23 3 40 3t40-3v404L480-240 200-120Zm480-480v-80h-80v-80h80v-80h80v80h80v80h-80v80h-80Z',
  upload: 'M440-320v-326L336-542l-56-58 200-200 200 200-56 58-104-104v326h-80ZM240-160q-33 0-56.5-23.5T160-240v-120h80v120h480v-120h80v120q0 33-23.5 56.5T720-160H240Z',
  download: 'M480-320 280-520l56-58 104 104v-326h80v326l104-104 56 58-200 200ZM240-160q-33 0-56.5-23.5T160-240v-120h80v120h480v-120h80v120q0 33-23.5 56.5T720-160H240Z',
  lock: 'M240-80q-33 0-56.5-23.5T160-160v-400q0-33 23.5-56.5T240-640h40v-80q0-83 58.5-141.5T480-920q83 0 141.5 58.5T680-720v80h40q33 0 56.5 23.5T800-560v400q0 33-23.5 56.5T720-80H240Zm296.5-223.5Q560-327 560-360t-23.5-56.5Q513-440 480-440t-56.5 23.5Q400-393 400-360t23.5 56.5Q447-280 480-280t56.5-23.5ZM360-640h240v-80q0-50-35-85t-85-35q-50 0-85 35t-35 85v80Z',
  list: 'M80-200v-80h80v80H80Zm160 0v-80h640v80H240ZM80-440v-80h80v80H80Zm160 0v-80h640v80H240ZM80-680v-80h80v80H80Zm160 0v-80h640v80H240Z',
}

function booksHtml() {
  const books = state.books.map((book) => {
    const current = book.id === state.bookId
    const kind = book.isSystem ? '官方词书' : '我的'
    const status = current ? '当前背诵' : kind
    return `<button type="button" class="book-row ${current ? 'active' : ''}" data-act="open-book" data-id="${book.id}">
      <span class="book-ico">${bookIcon(book)}</span>
      <span class="book-copy"><strong>${esc(book.name)}</strong><small>${Number(book.wordCount || (current ? state.wordTotal : 0) || 0).toLocaleString('en-US')} 词 · ${status}</small></span>
      ${book.isLocked ? `<span class="book-lock">${ms(MS.lock)}</span>` : ''}
    </button>`
  }).join('')
  const words = state.words.map(wordRowHtml).join('')
  const selected = state.selected
  const detail = selected
    ? studyCardHtml(selected)
    : `<section class="panel study-card"><div class="card-scroll"><h2>卡片</h2><p class="muted">从左侧选一个词。</p></div></section>`
  const book = selectedNotebook()
  return `<div class="books-desk">
    <section class="panel">
      <div class="col-head"><h2 class="with-ico">${ms(MS.folder)}词本库</h2><span class="col-count">${state.books.length} 个词本</span></div>
      <div class="book-tools">
        <button class="tiny" data-act="new-book">${ms(MS.add)}新建</button>
        <button class="tiny" data-act="book-up">${ms(MS.up)}上移</button>
        <button class="tiny" data-act="book-down">${ms(MS.down)}下移</button>
        ${book && !book.isLocked ? '<button class="tiny danger" data-act="delete-book">删除</button>' : ''}
      </div>
      <div class="list">${books || '<p class="muted">登录后可以看到自己的生词本。</p>'}</div>
      <div class="book-foot">
        <button class="tiny" data-act="export-books">${ms(MS.upload)}导出</button>
        <button class="tiny" data-act="import-books">${ms(MS.download)}导入</button>
      </div>
    </section>
    <section class="panel">
      <div class="col-head"><h2 class="with-ico">${ms(MS.list)}${esc(book?.name || '单词')}</h2><span class="col-count word-count">${Number(shownWordCount()).toLocaleString('en-US')} 词</span></div>
      <input id="word-filter" class="word-filter" type="search" placeholder="在当前词库中过滤…" value="${esc(state.wordFilter)}" />
      <div class="list" id="word-list">${words || '<p class="muted">这个本子还是空的。</p>'}</div>
    </section>
    ${detail}
  </div>`
}

function bookIcon(book) {
  const name = book.name || ''
  if (name.includes('动物')) return ms(MS.pets)
  if (name.includes('默认')) return ms(MS.star)
  if (name.includes('中考')) return ms(MS.school)
  if (name.includes('高考')) return ms(MS.military)
  if (name.includes('四级')) return ms(MS.premium)
  if (name.includes('六级')) return ms(MS.verified)
  if (name.includes('托福')) return ms(MS.flight)
  if (name.includes('雅思')) return ms(MS.public)
  if (!book.isSystem) return ms(MS.star)
  return ms(MS.book)
}

function selectedNotebook() {
  return state.books.find((book) => book.id === state.bookId) || null
}

function shortSideHtml(clip) {
  const words = (clip.keywords || clip.relatedWords || []).map((word) => (
    `<button class="chip" data-act="lookup" data-word="${esc(word)}">${esc(word)}</button>`
  )).join('')
  return `<h2>${esc(clip.title || '短视频')}</h2>
    <p class="muted">${esc(clip.author || '')}</p>
    <p>${esc(clip.caption || '')}</p>
    <div class="chips">${words}</div>
    <button class="primary" data-act="fav-short">${clip.favorited ? '取消收藏' : '收藏'}</button>
    <p class="muted">向下滚动切换，向上滚动返回。到头之后从头继续。</p>`
}

function favoritesHtml() {
  const back = menuIcon('<path d="m14.5 5-7 7 7 7"/>')
  const title = `<header class="fav-bar"><button type="button" class="fav-back" data-act="fav-back" aria-label="返回">${back}</button><h2>短视频收藏</h2>`
  if (!state.token) {
    return `<section class="fav-shorts">${title}</header><p class="fav-empty">登录后可以看到收藏的短视频。</p><button class="primary" type="button" data-act="open-login">登录 / 注册</button></section>`
  }
  if (!state.favoriteLoaded || (state.favoriteBusy && !state.favoriteShorts.length)) {
    return `<section class="fav-shorts">${title}</header><p class="fav-empty">加载中…</p></section>`
  }
  if (!state.favoriteShorts.length) {
    return `<section class="fav-shorts">${title}</header><p class="fav-empty">还没有收藏的短视频。</p></section>`
  }
  const selected = new Set(state.favoriteSelected.map(String))
  const all = selected.size === state.favoriteShorts.length
  const actions = `<button type="button" class="fav-all" data-act="fav-select-all">${all ? '取消全选' : '全选'}</button>${selected.size ? `<button type="button" class="fav-clear" data-act="fav-clear" ${state.favoriteRemoving ? 'disabled' : ''}>${state.favoriteRemoving ? '取消中' : '全部取消'}</button>` : ''}`
  const checkOn = `<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8.2"></circle><path d="m8.2 12.2 2.5 2.5 5.2-5.4"></path></svg>`
  const drop = `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6.2 3.2h8.4c.8 0 1.4.6 1.4 1.4V21l-5.6-2.7L5 21V4.6c0-.8.6-1.4 1.2-1.4z"/><path d="M14.4 4.8h7v2.3h-7z"/></svg>`
  const cards = state.favoriteShorts.map((clip, index) => {
    const cover = clip.coverUrl
      ? `<img src="${esc(mediaUrl(clip.coverUrl))}" alt="" />`
      : `<video class="fav-preview" src="${esc(mediaUrl(clip.videoUrl))}#t=0.1" muted playsinline preload="metadata"></video>`
    const on = selected.has(String(clip.id))
    return `<article class="fav-card">
      <div class="fav-shot">
        <button type="button" class="fav-cover" data-act="open-fav-short" data-index="${index}">${cover}</button>
        <button type="button" class="fav-check ${on ? 'on' : ''}" data-act="fav-toggle" data-id="${esc(clip.id)}" aria-label="${on ? '取消选择' : '选择'}">${checkOn}</button>
        <button type="button" class="fav-drop" data-act="unfav-short" data-id="${esc(clip.id)}" aria-label="取消收藏" ${state.favoriteRemoving ? 'disabled' : ''}>${drop}</button>
      </div>
      <div class="fav-meta">
        <strong>${esc(clip.title || '短视频')}</strong>
        <small>${esc(clip.author ? `@${clip.author}` : '')}</small>
      </div>
    </article>`
  }).join('')
  return `<section class="fav-shorts">${title}${actions}</header><div class="fav-grid">${cards}</div></section>`
}

function paintFavoriteChrome() {
  const root = document.querySelector('.fav-shorts')
  if (!root) return
  const selected = new Set(state.favoriteSelected.map(String))
  const all = state.favoriteShorts.length > 0 && selected.size === state.favoriteShorts.length
  const allBtn = root.querySelector('[data-act="fav-select-all"]')
  if (allBtn) allBtn.textContent = all ? '取消全选' : '全选'
  let clearBtn = root.querySelector('[data-act="fav-clear"]')
  if (selected.size) {
    if (!clearBtn && allBtn) {
      clearBtn = document.createElement('button')
      clearBtn.type = 'button'
      clearBtn.className = 'fav-clear'
      clearBtn.dataset.act = 'fav-clear'
      allBtn.after(clearBtn)
    }
    if (clearBtn) {
      clearBtn.textContent = state.favoriteRemoving ? '取消中' : '全部取消'
      clearBtn.disabled = state.favoriteRemoving
    }
  } else if (clearBtn) clearBtn.remove()
  root.querySelectorAll('.fav-check').forEach((button) => {
    const on = selected.has(String(button.dataset.id))
    button.classList.toggle('on', on)
    button.setAttribute('aria-label', on ? '取消选择' : '选择')
  })
}

function shortsHtml() {
  const clip = state.shorts[state.shortIndex]
  if (!clip) return `<section class="panel"><h1>短视频</h1><p class="muted">${state.busy || '正在准备片单…'}</p></section>`
  return `<section class="shorts-stage">
    <div class="tabs">
      <button class="chip ${state.showFavorites ? '' : 'on'}" data-act="shorts-feed">推荐</button>
      <button class="chip ${state.showFavorites ? 'on' : ''}" data-act="shorts-favs">我的收藏</button>
    </div>
    <div class="shorts-frame ${shortMetaVisible() ? '' : 'meta-off'}">
      <div class="video-wrap">
        <button class="ghost tiny short-meta-btn" type="button" data-act="toggle-short-meta">${shortMetaVisible() ? '隐藏文案' : '显示文案'}</button>
        <video id="short-video" src="${esc(mediaUrl(clip.videoUrl))}" ${clip.coverUrl ? `poster="${esc(mediaUrl(clip.coverUrl))}"` : ''} ${state.shortLoop ? 'loop' : ''} autoplay playsinline></video>
      </div>
      ${shortMetaVisible() ? `<aside class="shorts-side">${shortSideHtml(clip)}</aside>` : ''}
    </div>
  </section>`
}

function shortMetaVisible() {
  if (state.shortMetaShown == null) return state.shortsMeta
  return state.shortMetaShown
}

function applyShortMeta() {
  const frame = document.querySelector('.shorts-frame')
  if (!frame) return
  const show = shortMetaVisible()
  frame.classList.toggle('meta-off', !show)
  let side = frame.querySelector('.shorts-side')
  if (show) {
    if (!side) {
      side = document.createElement('aside')
      side.className = 'shorts-side'
      frame.appendChild(side)
    }
    const clip = state.shorts[state.shortIndex]
    if (clip) side.innerHTML = shortSideHtml(clip)
  } else if (side) {
    side.remove()
  }
  const button = frame.querySelector('[data-act="toggle-short-meta"]')
  if (button) button.textContent = show ? '隐藏文案' : '显示文案'
}

function vinylColors(show) {
  return VINYL[show?.id] || ['#161616', '#c4a574']
}

function audioPlaying() {
  const audio = document.getElementById('player')
  return Boolean(audio && audio.src && !audio.paused)
}

function clock(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) return '0:00'
  const whole = Math.floor(seconds)
  const min = Math.floor(whole / 60)
  const sec = String(whole % 60).padStart(2, '0')
  return `${min}:${sec}`
}

function vinylStage(show, large) {
  const [vinyl, label] = vinylColors(show)
  const playing = audioPlaying() && state.playingShowId === show.id
  const title = esc((show.title || '').slice(0, large ? 12 : 10))
  return `<div class="vinyl-stage ${large ? 'large' : ''} ${playing ? 'playing' : ''}" data-vinyl="${esc(show.id)}">
    <div class="vinyl-disc" style="--vinyl:${vinyl};--label:${label}"><span class="vinyl-label-text">${title}</span></div>
    ${large ? `<svg class="tonearm" viewBox="0 0 100 100" aria-hidden="true"><g class="arm-g"><circle cx="86" cy="14" r="3.5" fill="#c0c4cc"/><line x1="86" y1="14" x2="55" y2="52" stroke="#b8bcc4" stroke-width="2.2" stroke-linecap="round"/><circle cx="55" cy="52" r="2.8" fill="#e8eaed"/><line x1="55" y1="52" x2="51" y2="55" stroke="#ffc107" stroke-width="1.1" stroke-linecap="round"/></g></svg>` : ''}
  </div>`
}

function podcastHtml() {
  if (!state.shows.length) {
    return `<section class="pod-shelf-page"><h2>播客唱片架</h2><p class="muted">节目加载中</p></section>`
  }
  const show = state.shows.find((item) => item.id === state.showId)
  if (state.podShelf || !show) {
    const shows = state.shows.map((item) => (
      `<button class="pod-card" type="button" data-act="open-show" data-id="${esc(item.id)}">
        ${vinylStage(item, false)}
        <strong>${esc(item.title)}</strong>
        <span class="muted">${esc(item.host)}</span>
      </button>`
    )).join('')
    return `<section class="pod-shelf-page">
      <h2>播客唱片架</h2>
      <div class="pod-shelf">${shows}</div>
    </section>`
  }
  const playing = audioPlaying()
  const episode = show.episodes?.find((item) => item.id === state.episodeId)
  const episodes = (show.episodes || []).map((item) => (
    `<div class="row ${item.id === state.episodeId ? 'active' : ''}" data-act="play-ep" data-id="${esc(item.id)}">
      <strong>${esc(item.title)}</strong>
      <div class="muted">${esc(item.durationLabel || '')}</div>
    </div>`
  )).join('')
  const back = menuIcon('<path d="m14.5 5-7 7 7 7"/>')
  return `<section class="pod-detail">
    <header class="fav-bar">
      <button type="button" class="fav-back" data-act="pod-back" aria-label="返回">${back}</button>
      <h2>${esc(show.title)}</h2>
    </header>
    <div class="panel pod-player">
      <div class="pod-deck">
        ${vinylStage(show, true)}
        <h2>${esc(episode?.title || show.title || '选一个节目')}</h2>
        <p class="muted">${esc(show.blurb || '英文电台和播客，点一集即可播放。')}</p>
        <div class="pod-progress">
          <span id="pod-elapsed">0:00</span>
          <input id="pod-seek" type="range" min="0" max="1000" value="0" aria-label="播放进度" />
          <span id="pod-total">0:00</span>
        </div>
        <div class="pod-transport">
          <button class="ghost" data-act="pod-prev" type="button">上一集</button>
          <button class="primary pod-play" data-act="audio-toggle" type="button">${playing && state.playingShowId === show.id ? '暂停' : '播放'}</button>
          <button class="ghost" data-act="pod-next" type="button">下一集</button>
        </div>
      </div>
      <h3>曲目</h3>
      <div class="list">${episodes || '<p class="muted">这个节目还没有曲目。</p>'}</div>
    </div>
  </section>`
}

function meHtml() {
  const tabs = [
    ['home', '概览'],
    ['checkin', '签到'],
    ['ledger', '明细'],
    ['mall', '兑礼'],
    ['orders', '订单'],
    ['buy', '充值'],
    ['withdraw', '提现'],
    ['invite', '邀请'],
    ['account', '账号'],
  ]
  const tabHtml = tabs.map(([id, label]) => (
    `<button class="chip ${state.meTab === id ? 'on' : ''}" data-act="me-tab" data-id="${id}">${label}</button>`
  )).join('')
  return `<div class="me-grid">
    <section class="panel"><h2>我的</h2><div class="list">${tabHtml}</div></section>
    <section class="panel" id="me-panel">${mePanelHtml()}</section>
  </div>`
}

function mePanelHtml() {
  if (!state.token) return '<h2>请先登录</h2><p>可以用手机号、账号密码、微信、支付宝或 Apple 登录。</p><button class="primary" data-act="open-login">登录 / 注册</button>'
  const user = state.me?.user || {}
  if (state.meTab === 'home') {
    return `<h2>${esc(user.nickname || '词搭子')}</h2>
      <p class="muted">${esc(user.phone || '未绑定手机')} · 等级 ${user.level || state.me?.level || 1}</p>
      <p>${esc(user.signature || '')}</p>
      <p>搭币 ${state.points ?? '—'}</p>
      <p class="muted">激励视频只在 Android 里播放。网页不代领搭币，避免没看广告就加分。</p>
      <div class="actions">
        <a href="/agreement.html">用户协议</a>
        <a href="/privacy.html">隐私政策</a>
        <a href="/about.html">关于</a>
      </div>`
  }
  if (state.meTab === 'checkin') return checkinHtml()
  if (state.meTab === 'ledger') return ledgerHtml()
  if (state.meTab === 'mall') return mallHtml()
  if (state.meTab === 'orders') return ordersHtml()
  if (state.meTab === 'buy') return buyHtml()
  if (state.meTab === 'withdraw') return withdrawHtml()
  if (state.meTab === 'invite') return inviteHtml()
  if (state.meTab === 'account') return accountHtml(user)
  return settingsHtml()
}

function checkinHtml() {
  const check = state.checkIn
  if (!check) return '<p class="muted">签到加载中</p>'
  const today = shanghaiToday()
  const [year, month] = today.split('-').map(Number)
  const count = new Date(year, month, 0).getDate()
  const done = new Set(check.recentDates || [])
  let cells = ''
  for (let day = 1; day <= count; day += 1) {
    const ymd = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
    const marked = done.has(ymd)
    const isToday = ymd === today
    cells += `<div class="day ${marked ? 'done' : ''} ${isToday ? 'today' : ''}">${day}${!marked && ymd < today ? `<br><button class="tiny" data-act="makeup" data-date="${ymd}">补</button>` : ''}</div>`
  }
  return `<h2>签到</h2>
    <p>连续 ${check.streakDays || 0} 天 · 今日 ${check.checkedInToday ? '已签' : `可领 ${check.todayReward || ''} 搭币`}</p>
    <button class="primary" data-act="checkin" ${check.checkedInToday ? 'disabled' : ''}>签到</button>
    <div class="calendar" style="margin-top:14px">${cells}</div>
    <p class="muted">补签按当天规则单独计算，不走激励视频的分数。</p>`
}

function ledgerHtml() {
  const page = state.ledger
  if (!page) return '<p class="muted">明细加载中</p>'
  const rows = (page.items || []).map((item) => (
    `<div class="row"><strong>${item.delta > 0 ? '+' : ''}${item.delta}</strong> ${esc(item.reason || '')} <span class="muted">${esc(item.detail || '')} ${esc(String(item.created_at || '').slice(0, 16))}</span></div>`
  )).join('')
  return `<h2>搭币明细</h2><p>余额 ${page.balance ?? state.points ?? '—'}</p><div class="list">${rows || '<p class="muted">还没有记录</p>'}</div>
    <div class="actions"><button class="ghost" data-act="ledger-prev">上一页</button><button class="ghost" data-act="ledger-next">下一页</button></div>`
}

function mallHtml() {
  const cats = state.giftCats.map((cat) => (
    `<button class="chip ${state.giftCat === cat.id ? 'on' : ''}" data-act="gift-cat" data-id="${esc(cat.id)}">${esc(cat.name)}</button>`
  )).join('')
  const cards = state.gifts.map((gift) => (
    `<article class="gift">
      ${gift.coverImage ? `<img class="cover" alt="" src="${esc(gift.coverImage)}" />` : `<div style="font-size:32px">${esc(gift.coverEmoji || '🎁')}</div>`}
      <h3>${esc(gift.title)}</h3>
      <p class="muted">${esc(gift.subtitle || '')}</p>
      <p>${gift.cashFen > 0 ? `${gift.pointsCost} 搭币 + ${gift.cashYuan} 元` : `${gift.pointsCost} 搭币`}</p>
      <button class="primary" data-act="redeem" data-id="${gift.id}">兑换</button>
    </article>`
  )).join('')
  return `<h2>兑礼</h2><div class="chips">${cats}</div><div class="grid">${cards || '<p class="muted">这个栏目还没有礼品</p>'}</div>`
}

function ordersHtml() {
  const rows = state.orders.map((order) => (
    `<div class="row"><strong>${esc(order.giftTitle || '礼品')}</strong><div class="muted">${esc(order.status || '')} · ${order.pointsSpent || 0} 搭币 · ${esc(String(order.createdAt || '').slice(0, 16))}</div></div>`
  )).join('')
  return `<h2>兑换订单</h2><div class="list">${rows || '<p class="muted">还没有订单</p>'}</div>`
}

function buyHtml() {
  const points = (state.pointPacks?.items || []).map((item) => packButton(item, 'points')).join('')
  const images = (state.imagePacks?.items || []).map((item) => packButton(item, 'images')).join('')
  const orders = (state.pointOrders || []).map((order) => (
    `<div class="row">${esc(order.packageId)} · ${esc(order.statusLabel || order.status)} · ¥${esc(order.amountYuan || '')}</div>`
  )).join('')
  return `<h2>充值</h2>
    <p class="muted">支付参数是手机 App 的微信/支付宝 SDK。网页可以下单；沙箱环境可以模拟支付，正式支付请在 Android 完成。</p>
    <h3>搭币</h3><div class="grid">${points || '<p class="muted">暂无套餐</p>'}</div>
    <h3>配图次数</h3><div class="grid">${images || '<p class="muted">暂无套餐</p>'}</div>
    <h3>最近订单</h3><div class="list">${orders}</div>`
}

function packButton(item, kind) {
  return `<article class="gift"><h3>${esc(item.title || item.name || item.id)}</h3><p class="muted">${esc(item.subtitle || '')}</p><p>¥${((item.priceFen || 0) / 100).toFixed(2)}</p><button class="primary" data-act="buy-pack" data-id="${esc(item.id)}" data-kind="${kind}">购买</button></article>`
}

function withdrawHtml() {
  const cfg = state.withdrawCfg
  if (!cfg) return '<p class="muted">提现加载中</p>'
  const tiers = (cfg.tiers || []).map((tier) => (
    `<button class="chip" data-act="withdraw" data-fen="${tier.amountFen}">${esc(tier.label)} · ${tier.pointsCost} 搭币</button>`
  )).join('')
  const rows = state.withdrawals.map((item) => (
    `<div class="row">${esc(item.statusLabel || item.status || '')} · ¥${esc(item.amountYuan || '')} · ${esc(item.channel || '')}</div>`
  )).join('')
  return `<h2>提现</h2><p>${esc(cfg.note || '')}</p><div class="chips">${tiers}</div>
    <p class="muted">支付宝提现前需要已绑定支付宝。看完激励广告这个条件由 Android 完成，网页如果被拒绝，请先在手机上按提示操作。</p>
    <div class="list">${rows || '<p class="muted">还没有提现记录</p>'}</div>`
}

function inviteHtml() {
  const info = state.invite
  if (!info) return '<p class="muted">邀请信息加载中</p>'
  return `<h2>邀请</h2>
    <p>邀请码 <strong>${esc(info.inviteCode || '')}</strong></p>
    <p>${esc(info.inviteUrl || '')}</p>
    <p class="muted">已邀请 ${info.invitedCount || 0} 人 · 获得 ${info.invitePointsEarned || 0} 搭币</p>
    <button class="ghost" data-act="copy-invite">复制邀请文案</button>
    ${info.canBindInvite ? `<form id="bind-invite" class="quick-search" style="margin-top:12px"><input name="code" placeholder="填写别人的邀请码" /><button type="submit">绑定</button></form>` : ''}`
}

function accountHtml(user) {
  const conditions = (state.deletion?.conditions || []).map((item) => (
    `<div class="row">${item.ok ? '已满足' : '未满足'} · ${esc(item.title)}<div class="muted">${esc(item.detail || '')}</div></div>`
  )).join('')
  return `<h2>账号</h2>
    <form id="profile-form">
      <div class="field"><label>昵称</label><input name="nickname" value="${esc(user.nickname || '')}" /></div>
      <div class="field"><label>签名</label><input name="signature" value="${esc(user.signature || '')}" /></div>
      <div class="field"><label>性别</label><select name="gender"><option ${user.gender === '男' ? 'selected' : ''}>男</option><option ${user.gender === '女' ? 'selected' : ''}>女</option><option ${user.gender === '未知' || !user.gender ? 'selected' : ''}>未知</option></select></div>
      <div class="field"><label>地区</label><input name="region" value="${esc(user.region || '')}" /></div>
      <div class="field"><label>邮箱</label><input name="email" value="${esc(user.email || '')}" /></div>
      <div class="field"><label>收货姓名</label><input name="shippingName" value="${esc(user.shippingName || '')}" /></div>
      <div class="field"><label>收货手机</label><input name="shippingPhone" value="${esc(user.shippingPhone || '')}" /></div>
      <div class="field"><label>收货地址</label><input name="shippingDetail" value="${esc(user.shippingDetail || '')}" /></div>
      <button class="primary" type="submit">保存资料</button>
    </form>
    <div class="field" style="margin-top:16px"><label>头像</label><input id="avatar-file" type="file" accept="image/*" /></div>
    <h3>修改密码</h3>
    <form id="password-form">
      <div class="field"><input name="oldPassword" type="password" placeholder="当前密码" /></div>
      <div class="field"><input name="newPassword" type="password" placeholder="新密码，6 到 32 位" /></div>
      <button class="primary" type="submit">修改密码</button>
    </form>
    <h3>更换手机号</h3>
    <form id="phone-form">
      <div class="field"><input name="password" type="password" placeholder="当前密码" /></div>
      <div class="field"><input name="newPhone" placeholder="新手机号" /></div>
      <div class="field"><input name="code" placeholder="新手机收到的验证码" /></div>
      <button class="ghost" type="button" data-act="send-phone-code">发送验证码</button>
      <button class="primary" type="submit">更换</button>
    </form>
    <h3>注销账号</h3>
    ${state.deletion?.pending ? `<p>冷静期至 ${esc(state.deletion.dueAtLabel || '')}</p><button class="ghost" data-act="cancel-deletion">撤销注销</button>` : `<div class="list">${conditions}</div>
      <form id="deletion-form">
        <div class="field"><input name="reason" maxlength="40" placeholder="原因，可选" /></div>
        <label><input type="checkbox" name="agreed" /> 我已阅读并同意账号注销须知</label><br>
        <label><input type="checkbox" name="force" /> 强行注销</label>
        <div class="field"><input name="code" placeholder="短信验证码" /></div>
        <button class="ghost" type="button" data-act="send-deletion-code">发送验证码</button>
        <button class="danger" type="submit">提交注销</button>
      </form>`}`
}

function setSwitch(on) {
  return `<span class="switch ${on ? 'on' : ''}" aria-hidden="true"></span>`
}

function setToggle(key, title, subtitle, on) {
  return `<button class="set-row" type="button" data-act="pref" data-key="${key}">
    <span><strong>${title}</strong><small>${subtitle}</small></span>
    ${setSwitch(on)}
  </button>`
}

function favoriteNotebooks() {
  return state.books
    .filter((book) => book.kind === 'user')
    .slice()
    .sort((a, b) => (b.createdAtMillis || 0) - (a.createdAtMillis || 0) || Number(b.id) - Number(a.id))
}

function saveBookId() {
  const books = favoriteNotebooks()
  if (state.defaultBook && (!books.length || books.some((book) => book.id === state.defaultBook))) return state.defaultBook
  return state.me?.vocabNotebookId || books[0]?.id || 0
}

function settingsHtml() {
  const current = THEMES.find(([id]) => id === state.theme) || THEMES[0]
  const themes = THEMES.map(([id, label, color, image]) => {
    const face = image ? `background-image:url('${image}')` : `background:${color}`
    return `<button class="theme-swatch ${state.theme === id ? 'on' : ''}" data-act="theme" data-id="${id}" style="--swatch:${color}">
      <span class="theme-dot" style="${face}"></span>
      <span class="theme-name">${label}</span>
    </button>`
  }).join('')
  const providers = [
    ['pollinations', 'Pollinations'],
    ['siliconflow', '硅基流动'],
  ].map(([id, label]) => (
    `<button class="chip ${state.imageProvider === id ? 'on' : ''}" data-act="set-provider" data-id="${id}">${label}</button>`
  )).join('')
  const chosenBook = saveBookId()
  const notebooks = favoriteNotebooks().map((book) => (
    `<button class="chip ${book.id === chosenBook ? 'on' : ''}" data-act="set-default-book" data-id="${book.id}">${esc(book.name)}</button>`
  )).join('')
  const deletionPending = Boolean(state.deletion?.pending)
  return `<h2>设置</h2>
    <p class="muted">配置你的沉浸式学习体验。</p>
    <section class="set-card">
      <h3>外观主题</h3>
      <p class="muted">当前主题：<strong style="color:${current[2]}">${current[1]}</strong></p>
      <div class="theme-legend">${themes}</div>
      <h3>发音</h3>
      <div class="chips">
        <button class="chip ${state.accent === 'uk' ? 'on' : ''}" data-act="set-accent" data-id="uk">英音</button>
        <button class="chip ${state.accent === 'us' ? 'on' : ''}" data-act="set-accent" data-id="us">美音</button>
      </div>
      <h3>字号</h3>
      <div class="chips">
        ${[['0.9', '小'], ['1', '标准'], ['1.12', '大'], ['1.25', '特大']].map(([id, label]) => `<button class="chip ${state.font === id ? 'on' : ''}" data-act="font" data-id="${id}">${label}</button>`).join('')}
      </div>
    </section>
    <section class="set-card">
      <h3>偏好设置</h3>
      <p class="set-label">文生图接口</p>
      <div class="chips">${providers}</div>
      <div class="set-divider"></div>
      ${setToggle('autoSpeak', '自动朗读', '翻到生词时自动播放发音', state.autoSpeak)}
      <div class="set-divider"></div>
      ${setToggle('reminder', '每日提醒', '每天 20:00 提醒你坚持背单词。网页保持打开并允许通知后才会响。', state.reminder)}
      <div class="set-divider"></div>
      ${setToggle('podcastBackground', '息屏后仍可后台播放', '关闭后锁屏即暂停播客/电台，默认关闭', state.podcastBackground)}
      <div class="set-divider"></div>
      ${setToggle('shortsMeta', '短视频默认显示文案', '关闭后播放时默认藏文案，仍可单条点开', state.shortsMeta)}
      <div class="set-divider"></div>
      ${setToggle('shortLoop', '循环播放当前短视频', '停留在同一条时播完再从头播，默认关闭', state.shortLoop)}
      ${notebooks ? `<div class="set-divider"></div>
        <p class="set-label">默认收藏生词本</p>
        <p class="muted">首页查词点星星时，词条会保存到所选生词本</p>
        <div class="chips">${notebooks}</div>` : ''}
    </section>
    <section class="set-card">
      <h3>存储与缓存</h3>
      <button class="set-row" type="button" data-act="clear-cache">
        <span><strong>清除媒体缓存</strong><small id="cache-label">${state.cacheBusy ? '正在清除…' : `短视频 / 播客本地缓存 · 当前 ${esc(state.cacheLabel)}`}</small></span>
        <span class="chevron">›</span>
      </button>
    </section>
    <section class="set-card">
      <h3>账号与安全</h3>
      <button class="set-row" type="button" data-act="open-password">
        <span><strong>修改密码</strong><small>用当前密码设置新密码</small></span>
        <span class="chevron">›</span>
      </button>
      ${state.passwordOpen ? `<form id="password-form" class="set-form">
        <div class="field"><input name="oldPassword" type="password" placeholder="当前密码" /></div>
        <div class="field"><input name="newPassword" type="password" placeholder="新密码，6 到 32 位" /></div>
        <button class="primary" type="submit">修改密码</button>
      </form>` : ''}
      <div class="set-divider"></div>
      ${setToggle('biometric', '指纹解锁', '下次打开应用时验证指纹；密码/验证码登录后不会再要求', state.biometric)}
      <div class="set-divider"></div>
      <button class="set-row" type="button" data-act="open-deletion">
        <span><strong class="danger-text">注销账号</strong><small>${deletionPending ? '注销冷静期中，可随时撤销' : '阅读须知并验证后进入7天冷静期'}</small></span>
        <span class="chevron">›</span>
      </button>
    </section>
    <section class="set-card set-actions">
      <button class="set-center" type="button" data-act="switch-account">切换账号</button>
      <div class="set-divider"></div>
      <button class="set-center danger-text" type="button" data-act="logout-confirm">退出登录</button>
    </section>`
}

async function lookup(word) {
  const q = String(word || '').trim()
  if (!q) return
  state.view = 'lookup'
  history.replaceState(null, '', '#/lookup')
  state.entry = await api(`/dict?q=${encodeURIComponent(q)}`, { auth: false })
  state.homophones = []
  state.notes = []
  state.savedWordId = null
  render()
  try {
    const tips = await api(`/homophones?word=${encodeURIComponent(state.entry.text)}`)
    state.homophones = tips.items || []
  } catch { /* optional */ }
  if (state.token) {
    try {
      const notes = await api(`/word-notes?word=${encodeURIComponent(state.entry.text)}`)
      state.notes = notes.definitions || []
    } catch { /* optional */ }
    const bookId = saveBookId()
    if (bookId) {
      try {
        const pos = await api(`/notebooks/${bookId}/position?text=${encodeURIComponent(state.entry.text)}`)
        state.savedWordId = pos.found ? pos.wordId || pos.id : null
      } catch { /* optional */ }
    }
  }
  render()
}

async function loadBooks() {
  const data = state.token ? await api('/notebooks') : await api('/catalogs', { auth: false })
  state.books = data.items || []
  if (!state.books.some((book) => book.id === state.bookId)) {
    state.bookId = state.books[0]?.id || 0
  }
  render()
  if (state.bookId) await loadWords(true)
}

let wordsLoading = false
let wordsLoadGen = 0

function wordMatchesFilter(text, filter) {
  const q = String(filter || '').trim().toLowerCase()
  if (!q) return true
  return String(text || '').toLowerCase().split(/[\s-]+/).some((part) => part.startsWith(q))
}

function wordRowHtml(word) {
  const on = state.selected?.id === word.id
  const hidden = !wordMatchesFilter(word.text, state.wordFilter)
  const mark = on ? `<span class="word-now">当前第${cardIndex() + 1}词</span>` : ''
  return `<div class="word-row${on ? ' active' : ''}" ${hidden ? 'hidden' : ''} data-act="open-word" data-id="${word.id}"><span>${esc(word.text)}</span>${mark}</div>`
}

function appendWordRows(items) {
  const list = document.getElementById('word-list')
  if (!list || !items.length) return
  const empty = list.querySelector('p.muted')
  if (empty) empty.remove()
  list.insertAdjacentHTML('beforeend', items.map(wordRowHtml).join(''))
}

function bindWordList() {
  const list = document.getElementById('word-list')
  if (!list) return
  list.addEventListener('scroll', () => maybeLoadMoreWords(), { passive: true })
}

/** Android loads the next page once the list is within 8 rows of the end. */
function maybeLoadMoreWords() {
  if (state.view !== 'books' || wordsLoading || !state.wordCursor) return
  const list = document.getElementById('word-list')
  if (!list) return
  const rows = list.querySelectorAll('.word-row')
  if (!rows.length) return
  const mark = rows[Math.max(0, rows.length - 9)]
  if (mark.getBoundingClientRect().top <= list.getBoundingClientRect().bottom) loadWords(false)
}

function shownWordCount() {
  if (state.wordFilter.trim()) return state.wordMatchTotal || 0
  return state.wordTotal || state.words.length
}

function paintWordList() {
  const list = document.getElementById('word-list')
  if (!list) return false
  list.innerHTML = state.words.map(wordRowHtml).join('') || `<p class="muted">${state.wordFilter.trim() ? '没有匹配的词。' : '这个本子还是空的。'}</p>`
  list.scrollTop = 0
  const count = document.querySelector('.word-count')
  if (count) count.textContent = `${Number(shownWordCount()).toLocaleString('en-US')} 词`
  return true
}

async function loadWords(reset, options = {}) {
  if (!state.bookId) return
  if (!reset && (wordsLoading || !state.wordCursor)) return
  const gen = ++wordsLoadGen
  if (reset) {
    state.words = []
    state.wordCursor = null
    state.wordOrigin = 0
    if (!options.keepSelection) state.selected = null
  }
  wordsLoading = true
  try {
    const params = new URLSearchParams({ limit: '80' })
    const q = state.wordFilter.trim()
    if (q) params.set('q', q)
    if (state.wordCursor) params.set('cursor', state.wordCursor)
    const data = await api(`/notebooks/${state.bookId}/words?${params}`, { auth: Boolean(state.token) })
    if (gen !== wordsLoadGen) return
    const items = data.items || []
    state.words = state.words.concat(items)
    state.wordCursor = data.nextCursor
    if (reset) {
      const total = data.total || items.length
      if (q) state.wordMatchTotal = total
      else if (data.total) {
        state.wordTotal = data.total
        state.wordMatchTotal = data.total
        const current = state.books.find((book) => book.id === state.bookId)
        if (current) current.wordCount = data.total
      }
    }
    if (!state.selected && state.words[0] && !q) state.selected = state.words[0]
    const list = document.getElementById('word-list')
    if (options.listOnly && paintWordList()) {
      /* keep the search box focused */
    } else if (!reset && state.view === 'books' && list) appendWordRows(items)
    else render()
  } finally {
    if (gen === wordsLoadGen) wordsLoading = false
  }
  if (gen === wordsLoadGen && state.view === 'books') maybeLoadMoreWords()
}

async function loadFavoriteShorts() {
  if (!state.token) {
    state.favoriteBusy = false
    state.favoriteLoaded = true
    openLogin()
    return
  }
  state.favoriteBusy = true
  try {
    const items = []
    let page = 1
    while (page <= 20) {
      const data = await api(`/me/short-favorites?page=${page}&pageSize=60`)
      const batch = data.items || []
      items.push(...batch)
      if (batch.length < 60) break
      page += 1
    }
    state.favoriteShorts = items
    state.favoriteLoaded = true
    const ids = new Set(items.map((item) => String(item.id)))
    state.favoriteSelected = state.favoriteSelected.filter((id) => ids.has(String(id)))
  } finally {
    state.favoriteBusy = false
    if (state.view === 'favorites') render()
  }
}

async function loadShorts(reset) {
  state.busy = '加载中'
  if (reset) {
    state.shorts = []
    state.shortIndex = 0
  }
  if (state.showFavorites) {
    const data = await api('/me/short-favorites')
    state.shorts = data.items || []
  } else {
    const exclude = state.shorts.map((clip) => clip.id).join(',')
    const data = await api(`/shorts/feed?limit=12${exclude ? `&exclude=${encodeURIComponent(exclude)}` : ''}`)
    const seen = new Set(state.shorts.map((clip) => String(clip.id)))
    for (const clip of data.items || []) {
      if (!seen.has(String(clip.id))) state.shorts.push(clip)
    }
  }
  state.busy = ''
  render()
  bindShortVideo()
}

const shortWarm = new Map()

function bindShortVideo() {
  const video = document.getElementById('short-video')
  if (!video) return
  video.controls = false
  video.removeAttribute('controls')
  video.loop = Boolean(state.shortLoop)
  video.onended = () => {
    if (state.shortLoop) {
      video.currentTime = 0
      video.play().catch(() => {})
      return
    }
    stepShort(1)
  }
  warmNeighborShorts()
}

function shortPageVideo(clip, id) {
  const video = document.createElement('video')
  video.className = 'short-page'
  if (id) video.id = id
  video.src = mediaUrl(clip.videoUrl)
  if (clip.coverUrl) video.poster = mediaUrl(clip.coverUrl)
  video.controls = false
  video.autoplay = false
  video.playsInline = true
  video.preload = 'auto'
  video.dataset.shortId = String(clip.id)
  return video
}

function paintPoster(video) {
  if (!video.videoWidth || !video.videoHeight) return false
  const canvas = document.createElement('canvas')
  canvas.width = video.videoWidth
  canvas.height = video.videoHeight
  try {
    canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height)
    video.poster = canvas.toDataURL('image/jpeg', 0.72)
    return true
  } catch {
    return Boolean(video.poster)
  }
}

function waitFrame(video, timeout = 1200) {
  return new Promise((resolve) => {
    let done = false
    let timer = 0
    const finish = (ok) => {
      if (done) return
      done = true
      clearTimeout(timer)
      video.dataset.primed = ok ? '1' : 'fail'
      resolve()
    }
    const grab = () => {
      if (done) return
      try { video.pause() } catch { /* already stopped */ }
      finish(paintPoster(video) || Boolean(video.poster))
    }
    if (video.readyState >= 2 && video.videoWidth) requestAnimationFrame(grab)
    else {
      video.addEventListener('loadeddata', () => requestAnimationFrame(grab), { once: true })
      video.addEventListener('error', () => finish(false), { once: true })
    }
    video.muted = true
    const play = video.play()
    if (play && play.catch) play.catch(() => {})
    timer = setTimeout(grab, timeout)
  })
}

function ensurePrimed(video) {
  if (!video) return Promise.resolve()
  if (video.dataset.primed === '1' || video.dataset.primed === 'fail') return Promise.resolve()
  if (video._prime) return video._prime
  video.muted = true
  video._prime = waitFrame(video)
  return video._prime
}

function warmHost() {
  let host = document.getElementById('short-warm')
  if (!host) {
    host = document.createElement('div')
    host.id = 'short-warm'
    host.setAttribute('aria-hidden', 'true')
    document.body.appendChild(host)
  }
  return host
}

function discardShortVideo(video) {
  if (!video) return
  video.onended = null
  video.pause()
  video.removeAttribute('src')
  try { video.load() } catch { /* element is already gone */ }
  video.remove()
}

function warmNeighborShorts() {
  const count = state.shorts.length
  if (count < 2 || state.view !== 'shorts') return
  const host = warmHost()
  const clips = [
    state.shorts[(state.shortIndex + 1) % count],
    state.shorts[(state.shortIndex - 1 + count) % count],
  ]
  const keep = new Set(clips.map((clip) => String(clip.id)))
  for (const [id, video] of shortWarm) {
    if (keep.has(id)) {
      if (video.parentElement !== host) host.appendChild(video)
      continue
    }
    discardShortVideo(video)
    shortWarm.delete(id)
  }
  for (const clip of clips) {
    const id = String(clip.id)
    if (shortWarm.has(id)) continue
    const video = shortPageVideo(clip, '')
    video.muted = true
    video.classList.add('short-preload')
    video.setAttribute('aria-hidden', 'true')
    host.appendChild(video)
    shortWarm.set(id, video)
    ensurePrimed(video)
  }
}

async function stepShort(delta) {
  if (!state.shorts.length || state.shortBusy) return
  const count = state.shorts.length
  let next = state.shortIndex + delta
  if (next < 0) next = count - 1
  else if (next >= count) next = 0
  if (next === state.shortIndex) return
  const wrap = document.querySelector('.shorts-frame .video-wrap')
  const current = document.getElementById('short-video')
  const clip = state.shorts[next]
  if (!wrap || !current || !clip) {
    state.shortIndex = next
    render()
    bindShortVideo()
    return
  }
  state.shortBusy = true
  state.shortMetaShown = null
  applyShortMeta()
  wrap.classList.add('is-paging')
  current.controls = false
  const height = wrap.clientHeight
  let incoming = shortWarm.get(String(clip.id)) || null
  if (incoming) shortWarm.delete(String(clip.id))
  else {
    incoming = shortPageVideo(clip, '')
    incoming.muted = true
    warmHost().appendChild(incoming)
  }
  await ensurePrimed(incoming)
  incoming.classList.remove('short-preload')
  incoming.removeAttribute('aria-hidden')
  incoming.controls = false
  incoming.muted = false
  incoming.id = 'short-video-next'
  current.style.height = `${height}px`
  incoming.style.height = `${height}px`
  incoming.style.position = 'relative'
  const track = document.createElement('div')
  track.className = 'short-track'
  track.style.height = `${height * 2}px`
  track.style.alignSelf = 'flex-start'
  wrap.style.alignItems = 'flex-start'
  if (delta > 0) {
    track.append(current, incoming)
    track.style.transform = 'translateY(0)'
  } else {
    track.append(incoming, current)
    track.style.transform = `translateY(${-height}px)`
  }
  wrap.replaceChildren(track)
  const side = document.querySelector('.shorts-side')
  if (side) side.innerHTML = shortSideHtml(clip)
  if (incoming.dataset.primed !== '1') {
    try { incoming.currentTime = 0 } catch { /* start when the file allows it */ }
  }
  current.pause()
  incoming.play().catch(() => {})
  await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
  track.style.transition = 'transform 420ms cubic-bezier(0.22, 0.61, 0.36, 1)'
  track.style.transform = delta > 0 ? `translateY(${-height}px)` : 'translateY(0px)'
  await new Promise((resolve) => {
    const finish = (event) => {
      if (event && event.target !== track) return
      track.removeEventListener('transitionend', finish)
      resolve()
    }
    track.addEventListener('transitionend', finish)
    setTimeout(resolve, 520)
  })
  current.onended = null
  incoming.onended = null
  state.shortIndex = next
  incoming.id = 'short-video'
  incoming.controls = false
  incoming.removeAttribute('controls')
  incoming.muted = false
  incoming.style.height = ''
  incoming.style.position = ''
  wrap.classList.remove('is-paging')
  wrap.style.alignItems = ''
  wrap.replaceChildren(incoming)
  discardShortVideo(current)
  state.shortMetaShown = null
  state.shortBusy = false
  bindShortVideo()
  applyShortMeta()
}

async function loadShows() {
  const response = await fetch('/desk/podcasts.json')
  state.shows = await response.json()
  render()
}

async function loadMeTab() {
  if (!state.token) {
    render()
    return
  }
  if (!state.me) await refreshMe()
  if (state.meTab === 'checkin') state.checkIn = (await api('/me/checkin')).checkIn
  if (state.meTab === 'ledger') state.ledger = await api(`/me/points-ledger?page=${state.ledgerPage || 1}`)
  if (state.meTab === 'mall') {
    if (!state.giftCats.length) state.giftCats = (await api('/gifts/categories', { auth: false })).items || []
    state.gifts = (await api(`/gifts?category=${encodeURIComponent(state.giftCat)}`)).items || []
  }
  if (state.meTab === 'orders') state.orders = (await api('/me/gift-orders')).items || []
  if (state.meTab === 'buy') {
    state.pointPacks = await api('/point-packages', { auth: false })
    state.imagePacks = await api('/image-credit-packages', { auth: false })
    state.pointOrders = (await api('/me/point-orders')).items || []
  }
  if (state.meTab === 'withdraw') {
    state.withdrawCfg = (await api('/me/withdrawals/config')).config
    state.withdrawals = (await api('/me/withdrawals')).items || []
  }
  if (state.meTab === 'invite') state.invite = await api('/me/invite')
  if (state.meTab === 'account' || state.meTab === 'settings') {
    try { state.deletion = await api('/me/deletion') } catch { state.deletion = null }
  }
  if (state.meTab === 'settings' && !state.books.length) {
    try {
      const data = await api('/notebooks')
      state.books = data.items || []
    } catch { /* picker stays empty */ }
  }
  state.points = state.checkIn?.totalPoints ?? state.points
  render()
  if (state.meTab === 'settings') refreshCacheLabel()
}

let authPanel = 'phone'
let authQr = 'wechat'
let authQrSeq = 0
let authQrTimer = 0
let authCodeUntil = 0
let authCodeTimer = 0
let authDraft = { phone: '', code: '' }

function stopAuthQr() {
  authQrSeq += 1
  clearTimeout(authQrTimer)
}

function closeModal() {
  stopAuthQr()
  clearInterval(authCodeTimer)
  const modal = document.getElementById('modal')
  modal.hidden = true
  modal.dataset.strict = ''
  document.querySelector('.app')?.removeAttribute('inert')
}

function openLogin() {
  authPanel = 'phone'
  authQr = 'wechat'
  authDraft = { phone: '', code: '' }
  const modal = document.getElementById('modal')
  modal.hidden = false
  modal.innerHTML = `<form class="dialog auth-dialog" id="login-form" autocomplete="on">
    <button class="auth-x" type="button" data-act="close-modal" aria-label="关闭">×</button>
    <div class="auth-head">
      <h2>完成登录/注册，开始背单词</h2>
      <p>新用户验证手机号后设置密码，即可使用词搭子。</p>
    </div>
    <div class="auth-body">
      <div class="auth-top">
        <div class="auth-tabs" id="auth-tabs"></div>
      </div>
      <div class="auth-columns">
        <div id="auth-main"></div>
        <div class="auth-qr-col">
          <div class="auth-qr-switch" role="tablist" aria-label="扫码登录">
            <button type="button" data-act="login-qr" data-qr="wechat" id="auth-qr-wechat">微信</button>
            <button type="button" data-act="login-qr" data-qr="alipay" id="auth-qr-alipay">支付宝</button>
          </div>
          <button class="auth-qr-box" id="auth-qr-box" type="button" data-act="refresh-qr" aria-label="刷新二维码"></button>
        </div>
      </div>
    </div>
  </form>`
  paintAuthMain()
  startAuthQr()
}

function authCodeLabel() {
  const left = Math.ceil((authCodeUntil - Date.now()) / 1000)
  return left > 0 ? `${left}s` : '获取验证码'
}

function tickAuthCode() {
  const button = document.querySelector('#login-form [data-act="send-login-code"]')
  if (button) {
    const left = Math.ceil((authCodeUntil - Date.now()) / 1000)
    button.disabled = left > 0
    button.textContent = authCodeLabel()
  }
  if (authCodeUntil <= Date.now()) clearInterval(authCodeTimer)
}

function eyeButton() {
  return `<button class="auth-eye" type="button" data-act="toggle-pass" aria-label="显示密码">
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" fill="none" stroke="currentColor" stroke-width="1.7"/><circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" stroke-width="1.7"/></svg>
  </button>`
}

function appleButton() {
  return `<button class="auth-apple" type="button" data-act="apple-login">
    <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M16.4 12.6c0-2.2 1.8-3.3 1.9-3.4-1-1.5-2.6-1.7-3.2-1.7-1.3-.1-2.6.8-3.3.8s-1.7-.8-2.9-.7c-1.5.1-2.8.9-3.6 2.2-1.5 2.7-.4 6.6 1.1 8.8.7 1.1 1.6 2.3 2.7 2.2 1.1 0 1.5-.7 2.8-.7s1.7.7 2.9.7 1.9-1.1 2.6-2.2c.8-1.2 1.2-2.4 1.2-2.4s-2.2-.9-2.2-3.6Zm-2.1-6.6c.6-.7 1-1.7.9-2.7-0.9 0-1.9.6-2.5 1.3-.6.6-1.1 1.6-.9 2.6 1 .1 1.9-.5 2.5-1.2Z"/></svg>
    Apple 账号登录
  </button>`
}

function paintAuthMain() {
  const slot = document.getElementById('auth-main')
  const tabs = document.getElementById('auth-tabs')
  if (!slot || !tabs) return
  tabs.innerHTML = `<button type="button" data-act="login-tab" data-tab="phone" class="${authPanel === 'phone' || authPanel === 'setup' ? 'on' : ''}">手机号登录</button>
    <button type="button" data-act="login-tab" data-tab="account" class="${authPanel === 'account' || authPanel === 'reset' ? 'on' : ''}">账号登录</button>`
  let body = ''
  if (authPanel === 'account') {
    body = `<input class="auth-input" name="account" placeholder="请输入手机号" autocomplete="username" />
      <div class="auth-pass">
        <input name="password" type="password" placeholder="请输入密码" autocomplete="current-password" />
        ${eyeButton()}
      </div>
      <button class="auth-submit" type="submit">登录</button>
      <button class="auth-link" type="button" data-act="forgot-password">忘记密码?</button>`
  } else if (authPanel === 'reset') {
    body = `<p class="auth-note">用短信验证码重置密码</p>
      <div class="auth-phone"><span>+86</span><input name="phone" inputmode="numeric" maxlength="11" placeholder="请输入手机号" autocomplete="tel" /></div>
      <div class="auth-code"><input name="code" inputmode="numeric" maxlength="6" placeholder="请输入验证码" autocomplete="one-time-code" /><button type="button" data-act="send-login-code">${authCodeLabel()}</button></div>
      <div class="auth-pass"><input name="password" type="password" placeholder="新密码，6 到 32 位" autocomplete="new-password" />${eyeButton()}</div>
      <input class="auth-input" name="confirm" type="password" placeholder="再输入一次新密码" autocomplete="new-password" />
      <button class="auth-submit" type="submit">重置并登录</button>
      <label class="auth-agree"><input name="agree" type="checkbox" /><span>我已阅读并同意 <a href="/agreement.html" target="_blank" rel="noopener">《用户协议》</a> 和 <a href="/privacy.html" target="_blank" rel="noopener">《隐私政策》</a></span></label>
      <button class="auth-link" type="button" data-act="login-tab" data-tab="account">返回账号登录</button>`
  } else if (authPanel === 'setup') {
    body = `<p class="auth-note">新用户，请设置登录密码</p>
      <p class="auth-phone-fixed">+86 ${esc(authDraft.phone)}</p>
      <div class="auth-pass"><input name="password" type="password" placeholder="设置密码，6 到 32 位" autocomplete="new-password" />${eyeButton()}</div>
      <input class="auth-input" name="confirm" type="password" placeholder="再输入一次密码" autocomplete="new-password" />
      <input class="auth-input" name="inviteCode" maxlength="32" placeholder="邀请码，可选" />
      <button class="auth-submit" type="submit">完成注册</button>`
  } else {
    body = `<div class="auth-phone"><span>+86</span><input name="phone" inputmode="numeric" maxlength="11" placeholder="请输入手机号" autocomplete="tel" /></div>
      <div class="auth-code"><input name="code" inputmode="numeric" maxlength="6" placeholder="请输入验证码" autocomplete="one-time-code" /><button type="button" data-act="send-login-code">${authCodeLabel()}</button></div>
      <button class="auth-submit" type="submit">登录 / 注册</button>
      <label class="auth-agree"><input name="agree" type="checkbox" /><span>我已阅读并同意 <a href="/agreement.html" target="_blank" rel="noopener">《用户协议》</a> 和 <a href="/privacy.html" target="_blank" rel="noopener">《隐私政策》</a>，未注册的手机号将自动创建账号</span></label>
      ${appleButton()}`
  }
  slot.innerHTML = body
  paintAuthQrSwitch()
  tickAuthCode()
}

function paintAuthQrSwitch() {
  document.getElementById('auth-qr-wechat')?.classList.toggle('on', authQr === 'wechat')
  document.getElementById('auth-qr-alipay')?.classList.toggle('on', authQr === 'alipay')
}

async function startAuthQr() {
  const seq = ++authQrSeq
  clearTimeout(authQrTimer)
  const box = document.getElementById('auth-qr-box')
  if (!box) return
  box.innerHTML = '<span class="auth-qr-wait">正在生成二维码</span>'
  let data
  try {
    data = await api('/auth/qr/start', { method: 'POST', auth: false, body: { channel: authQr } })
  } catch (error) {
    if (seq !== authQrSeq) return
    box.innerHTML = `<span class="auth-qr-wait">${esc(error.message || '二维码生成失败')}</span>`
    return
  }
  if (seq !== authQrSeq) return
  if (!data.ready || !String(data.svg || '').trim().startsWith('<svg')) {
    box.innerHTML = `<span class="auth-qr-wait">${esc(data.error || '扫码登录尚未配置')}</span>`
    return
  }
  box.innerHTML = data.svg
  const poll = async () => {
    if (seq !== authQrSeq) return
    try {
      const status = await api(`/auth/qr/status?ticket=${encodeURIComponent(data.ticket)}`, { auth: false })
      if (seq !== authQrSeq) return
      if (status.status === 'done' && status.token) {
        await acceptLogin(status.token)
        return
      }
      if (status.status === 'expired') {
        startAuthQr()
        return
      }
    } catch { /* keep waiting */ }
    if (seq === authQrSeq) authQrTimer = setTimeout(poll, 2000)
  }
  authQrTimer = setTimeout(poll, 2000)
}

async function startAppleLogin() {
  const agreed = document.querySelector('#login-form [name=agree]')?.checked
  if (!agreed) throw new Error('请先阅读并同意《用户协议》和《隐私政策》')
  const data = await api('/auth/apple/web', { method: 'POST', auth: false, body: {} })
  if (!data.url) throw new Error('Apple 登录没有返回地址')
  location.href = data.url
}

async function onAct(act, node) {
  if (act === 'lookup') return lookup(node.dataset.word)
  if (act === 'speak') return speak(node.dataset.text, false)
  if (act === 'speak-slow') return speak(node.dataset.text, true)
  if (act === 'accent') {
    state.accent = state.accent === 'uk' ? 'us' : 'uk'
    localStorage.setItem('wb.accent', state.accent)
    return render()
  }
  if (act === 'save-entry') return toggleSave()
  if (act === 'mnemonic') return makeImage()
  if (act === 'like-tip') {
    await api(`/homophones/${node.dataset.id}/like`, { method: 'POST', body: {} })
    return lookup(state.entry.text)
  }
  if (act === 'open-book') {
    state.wordFilter = ''
    state.bookId = Number(node.dataset.id)
    localStorage.setItem('wb.book', String(state.bookId))
    return loadWords(true)
  }
  if (act === 'open-word') {
    const word = state.words.find((item) => item.id === Number(node.dataset.id)) || null
    if (state.token && word) {
      api('/me/study-scene', { method: 'PUT', body: { notebookId: state.bookId, wordId: word.id, wordText: word.text } }).catch(() => {})
    }
    return selectCard(word)
  }
  if (act === 'card-phonics') {
    state.cardPhonics = node.dataset.on === '1'
    const slot = document.querySelector('.card-word-slot')
    if (slot) {
      slot.classList.toggle('phonics', state.cardPhonics)
      node.parentElement?.querySelectorAll('[data-act="card-phonics"]').forEach((button) => {
        button.classList.toggle('on', (button.dataset.on === '1') === state.cardPhonics)
      })
    } else {
      render()
    }
    if (state.cardPhonics && state.selected) speakSyllables(analyzePhonics(state.selected.text).syllables)
    return
  }
  if (act === 'card-accent') {
    state.accent = node.dataset.id === 'uk' ? 'uk' : 'us'
    localStorage.setItem('wb.accent', state.accent)
    render()
    if (state.selected) speak(state.selected.text)
    return
  }
  if (act === 'card-related') {
    state.cardRelated = Number(node.dataset.i) || 0
    return render()
  }
  if (act === 'card-example') return stepExample((Number(node.dataset.i) || 0) - state.cardExample)
  if (act === 'card-edit') {
    state.cardEdit = !state.cardEdit
    return render()
  }
  if (act === 'card-favorite') return openFavoritePicker()
  if (act === 'favorite-to') return favoriteTo(node.dataset.id)
  if (act === 'favorite-current') {
    if (!state.token) return openLogin()
    const entry = state.relatedEntry
    const bookId = saveBookId()
    if (!entry || !bookId) return
    const data = await api(`/notebooks/${bookId}/words`, { method: 'POST', body: entryPayload(entry) })
    closeModal()
    toast(data.created === false ? '生词本里已经有了' : '已收藏')
    return refreshCardSaved()
  }
  if (act === 'related-word') return openRelated(node.dataset.word)
  if (act === 'card-image') return makeCardImage()
  if (act === 'card-play') return toggleCardPlay()
  if (act === 'card-shuffle') {
    state.cardShuffle = !state.cardShuffle
    return render()
  }
  if (act === 'card-seek') {
    state.cardSeek = !state.cardSeek
    return render()
  }
  if (act === 'card-mute') return togglePref('autoSpeak')
  if (act === 'flip') { state.revealed = !state.revealed; return render() }
  if (act === 'prev-word' || act === 'next-word') return stepWord(act === 'next-word' ? 1 : -1)
  if (act === 'delete-word') return deleteWord()
  if (act === 'new-book') return createBook()
  if (act === 'delete-book') return deleteBook()
  if (act === 'delete-book-confirm') return confirmDeleteBook()
  if (act === 'book-up' || act === 'book-down') return moveBook(act === 'book-up' ? -1 : 1)
  if (act === 'copy-book') return copyBook()
  if (act === 'export-books') return exportBooks()
  if (act === 'import-books') return importBooks()
  if (act === 'shorts-feed') { state.showFavorites = false; return loadShorts(true) }
  if (act === 'shorts-favs') { state.showFavorites = true; return loadShorts(true) }
  if (act === 'fav-short') return favoriteShort()
  if (act === 'open-show') {
    state.showId = node.dataset.id
    state.podShelf = false
    const picked = state.shows.find((item) => item.id === state.showId)
    const belongs = picked?.episodes?.some((item) => item.id === state.episodeId)
    if (!belongs) state.episodeId = picked?.episodes?.[0]?.id || ''
    return render()
  }
  if (act === 'pod-back') {
    state.podShelf = true
    return render()
  }
  if (act === 'play-ep') return playEpisode(node.dataset.id)
  if (act === 'pod-prev') return stepEpisode(-1)
  if (act === 'pod-next') return stepEpisode(1)
  if (act === 'audio-toggle') {
    const audio = document.getElementById('player')
    if (!audio.src || state.playingShowId !== state.showId) {
      const show = state.shows.find((item) => item.id === state.showId)
      const episode = show?.episodes?.find((item) => item.id === state.episodeId) || show?.episodes?.[0]
      if (episode) return playEpisode(episode.id)
    }
    if (audio.paused) audio.play().catch((error) => toast(error.message))
    else audio.pause()
    return
  }
  if (act === 'me-tab') {
    state.meTab = node.dataset.id
    return loadMeTab()
  }
  if (act === 'open-login') return openLogin()
  if (act === 'open-settings') return setView('settings')
  if (act === 'open-profile') {
    if (!state.token) return openLogin()
    state.meTab = 'account'
    return setView('me')
  }
  if (act === 'open-favorites') {
    if (!state.token) return openLogin()
    if (state.view !== 'favorites') state.favReturn = state.view || 'books'
    return setView('favorites')
  }
  if (act === 'fav-back') return setView(state.favReturn || 'books')
  if (act === 'fav-toggle') {
    if (state.favoriteRemoving) return
    const id = String(node.dataset.id || '')
    if (!id) return
    const selected = new Set(state.favoriteSelected.map(String))
    if (selected.has(id)) selected.delete(id)
    else selected.add(id)
    state.favoriteSelected = [...selected]
    paintFavoriteChrome()
    return
  }
  if (act === 'fav-select-all') {
    if (state.favoriteRemoving) return
    const all = state.favoriteSelected.length === state.favoriteShorts.length
    state.favoriteSelected = all ? [] : state.favoriteShorts.map((clip) => String(clip.id))
    paintFavoriteChrome()
    return
  }
  if (act === 'fav-clear') return unfavoriteListedShorts(state.favoriteSelected)
  if (act === 'open-fav-short') {
    const index = Number(node.dataset.index) || 0
    if (!state.favoriteShorts.length) return
    state.shorts = state.favoriteShorts.slice()
    state.shortIndex = Math.min(index, state.shorts.length - 1)
    state.showFavorites = true
    return setView('shorts')
  }
  if (act === 'unfav-short') return unfavoriteListedShort(node.dataset.id)
  if (act === 'close-modal') return closeModal()
  if (act === 'login-tab') {
    authPanel = node.dataset.tab === 'account' ? 'account' : 'phone'
    paintAuthMain()
    return
  }
  if (act === 'login-qr') {
    const next = node.dataset.qr === 'alipay' ? 'alipay' : 'wechat'
    if (next === authQr) return
    authQr = next
    paintAuthQrSwitch()
    return startAuthQr()
  }
  if (act === 'refresh-qr') return startAuthQr()
  if (act === 'forgot-password') {
    authPanel = 'reset'
    paintAuthMain()
    return
  }
  if (act === 'toggle-pass') {
    const input = node.parentElement?.querySelector('input')
    if (!input) return
    input.type = input.type === 'password' ? 'text' : 'password'
    return
  }
  if (act === 'apple-login') return startAppleLogin()
  if (act === 'send-login-code') return sendLoginCode()
  if (act === 'checkin') return doCheckin()
  if (act === 'makeup') return doMakeup(node.dataset.date)
  if (act === 'ledger-prev' || act === 'ledger-next') {
    const page = state.ledger?.page || 1
    state.ledgerPage = Math.max(1, page + (act === 'ledger-next' ? 1 : -1))
    return loadMeTab()
  }
  if (act === 'gift-cat') { state.giftCat = node.dataset.id; return loadMeTab() }
  if (act === 'redeem') return redeemGift(Number(node.dataset.id))
  if (act === 'buy-pack') return buyPack(node.dataset.id)
  if (act === 'withdraw') return withdraw(Number(node.dataset.fen))
  if (act === 'copy-invite') return copyInvite()
  if (act === 'send-phone-code') return sendCode(document.querySelector('#phone-form [name=newPhone]')?.value)
  if (act === 'send-deletion-code') {
    await api('/me/deletion/send-code', { method: 'POST', body: { force: document.querySelector('#deletion-form [name=force]')?.checked === true } })
    toast('验证码已发送')
    return
  }
  if (act === 'cancel-deletion') {
    await api('/me/deletion/cancel', { method: 'POST', body: {} })
    toast('已撤销注销')
    return loadMeTab()
  }
  if (act === 'theme') {
    state.theme = node.dataset.id
    localStorage.setItem('wb.theme', state.theme)
    applyPrefs()
    return render()
  }
  if (act === 'set-accent') {
    state.accent = node.dataset.id
    localStorage.setItem('wb.accent', state.accent)
    return render()
  }
  if (act === 'font') {
    state.font = node.dataset.id
    localStorage.setItem('wb.font', state.font)
    applyPrefs()
    return render()
  }
  if (act === 'toggle-reminder') return toggleReminder()
  if (act === 'pref') return togglePref(node.dataset.key)
  if (act === 'set-provider') {
    state.imageProvider = node.dataset.id === 'siliconflow' ? 'siliconflow' : 'pollinations'
    localStorage.setItem('wb.imageProvider', state.imageProvider)
    return render()
  }
  if (act === 'set-default-book') {
    state.defaultBook = Number(node.dataset.id) || 0
    localStorage.setItem('wb.defaultBook', String(state.defaultBook))
    render()
    return refreshSavedFlag()
  }
  if (act === 'clear-cache') return clearMediaCache()
  if (act === 'open-password') {
    state.passwordOpen = !state.passwordOpen
    return render()
  }
  if (act === 'open-deletion') {
    state.meTab = 'account'
    return loadMeTab()
  }
  if (act === 'toggle-short-meta') {
    state.shortMetaShown = !shortMetaVisible()
    applyShortMeta()
    return
  }
  if (act === 'switch-account') {
    logout(false)
    openLogin()
    return render()
  }
  if (act === 'logout-confirm') {
    if (!confirm('退出后将清除本机登录状态与词库缓存，可继续以游客身份使用。再次使用需重新登录。')) return
    return logout()
  }
}

async function toggleSave() {
  if (!state.token) return openLogin()
  const bookId = saveBookId()
  if (!bookId || !state.entry) return
  const bookName = favoriteNotebooks().find((book) => book.id === bookId)?.name || '默认生词本'
  if (state.savedWordId) {
    await api(`/words/${state.savedWordId}`, { method: 'DELETE' })
    state.savedWordId = null
    toast(`已移出${bookName}`)
  } else {
    const data = await api(`/notebooks/${bookId}/words`, { method: 'POST', body: entryPayload(state.entry) })
    state.savedWordId = data.item?.id || null
    toast(data.created === false ? '生词本里已经有了' : `已加入${bookName}`)
  }
  render()
}

function entryPayload(entry) {
  return {
    text: entry.text,
    isPhrase: entry.isPhrase,
    ipaUk: entry.ipaUk,
    ipaUs: entry.ipaUs,
    definitions: entry.definitions || [],
    examples: entry.examples || [],
    nearWords: entry.nearWords || [],
    synonyms: entry.synonyms || [],
    antonyms: entry.antonyms || [],
  }
}

async function makeImage() {
  if (!state.entry) return
  if (!state.token) return openLogin()
  toast('正在生成配图，会按规则扣搭币或配图次数')
  const meaning = defLines(state.entry.definitions)[0] || ''
  const data = await api('/mnemonic-images', {
    method: 'POST',
    body: { word: state.entry.text, meaningHint: meaning, billing: 'points', provider: state.imageProvider },
  })
  const box = document.getElementById('mnemonic')
  if (box && data.imageBase64) {
    box.innerHTML = `<img class="cover" alt="${esc(state.entry.text)}" src="data:image/png;base64,${data.imageBase64}" />`
  }
  if (data.balance != null) state.points = data.balance
  paintChrome()
  toast(data.cached ? '用了已有配图' : `已生成，花费 ${data.pointsSpent || 0} 搭币`)
}

function stepWord(delta) {
  if (!state.selected) return Promise.resolve()
  const total = state.wordTotal || state.words.length
  if (!total) return Promise.resolve()
  if (state.cardShuffle && total > 1) {
    let next = Math.floor(Math.random() * total)
    if (next === cardIndex()) next = (next + (delta || 1) + total) % total
    return jumpWord(next)
  }
  let target = cardIndex() + delta
  if (target < 0) target = total - 1
  if (target >= total) target = 0
  const local = target - (state.wordOrigin || 0)
  if (local >= 0 && local < state.words.length) return selectCard(state.words[local])
  return jumpWord(target)
}

function cardIndex() {
  if (!state.selected) return 0
  const local = state.words.findIndex((word) => word.id === state.selected.id)
  return (state.wordOrigin || 0) + Math.max(0, local)
}

function selectCard(word) {
  if (!word) return Promise.resolve()
  const list = document.getElementById('word-list')
  const keepScroll = Boolean(list && list.querySelector(`.word-row[data-id="${word.id}"]`))
  const scrollTop = keepScroll ? list.scrollTop : 0
  state.selected = word
  state.cardPhonics = false
  state.cardExample = 0
  state.cardRelated = 0
  state.cardEdit = false
  state.cardNotes = []
  render()
  const next = document.getElementById('word-list')
  if (next && keepScroll) next.scrollTop = scrollTop
  const row = next && next.querySelector(`.word-row[data-id="${word.id}"]`)
  if (row && next) {
    const listBox = next.getBoundingClientRect()
    const rowBox = row.getBoundingClientRect()
    if (rowBox.top < listBox.top - 1 || rowBox.bottom > listBox.bottom + 1) {
      row.scrollIntoView({ block: 'nearest' })
    }
  }
  if ((state.autoSpeak || state.cardPlaying) && word.text) speak(word.text)
  refreshCardSaved()
  loadCardNotes()
  maybeLoadMoreWords()
  return Promise.resolve()
}

async function jumpWord(index) {
  const total = state.wordTotal || state.words.length || 1
  const target = ((Number(index) % total) + total) % total
  const local = target - (state.wordOrigin || 0)
  if (local >= 0 && local < state.words.length) return selectCard(state.words[local])
  const data = await api(`/notebooks/${state.bookId}/words?fromIndex=${target}&limit=40`, { auth: Boolean(state.token) })
  state.words = data.items || []
  state.wordOrigin = Number.isFinite(Number(data.fromIndex)) ? Number(data.fromIndex) : target
  if (data.total) state.wordTotal = data.total
  state.wordCursor = data.nextCursor
  if (state.words[0]) return selectCard(state.words[0])
}

function stopCardPlay() {
  state.cardPlaying = false
  clearTimeout(state.cardTimer)
}

function armCardPlay() {
  clearTimeout(state.cardTimer)
  if (!state.cardPlaying) return
  state.cardTimer = setTimeout(() => {
    stepWord(1).finally(() => armCardPlay())
  }, 2500)
}

function toggleCardPlay() {
  state.cardPlaying = !state.cardPlaying
  if (state.cardPlaying) {
    if (state.autoSpeak && state.selected) speak(state.selected.text)
    armCardPlay()
  } else {
    clearTimeout(state.cardTimer)
  }
  render()
}

async function refreshCardSaved() {
  const word = state.selected
  const bookId = saveBookId()
  if (!state.token || !word || !bookId) {
    state.cardFavorited = false
    return
  }
  try {
    const pos = await api(`/notebooks/${bookId}/position?text=${encodeURIComponent(word.text)}`)
    if (state.selected?.id !== word.id) return
    state.cardFavorited = Boolean(pos.found)
  } catch {
    state.cardFavorited = false
  }
  const chip = document.querySelector('.fav-chip')
  if (chip) {
    chip.classList.toggle('saved', state.cardFavorited)
    const star = chip.querySelector('span')
    if (star) star.textContent = state.cardFavorited ? '★' : '☆'
  }
}

async function loadCardNotes() {
  const word = state.selected
  if (!state.token || !word) return
  try {
    const notes = await api(`/word-notes?word=${encodeURIComponent(word.text)}`)
    if (state.selected?.id !== word.id) return
    state.cardNotes = notes.definitions || []
    const box = document.getElementById('card-notes')
    if (box) box.innerHTML = state.cardNotes.length ? `<p class="card-kicker">我的补充</p>${cardDefsHtml(state.cardNotes)}` : ''
  } catch { /* notes are optional */ }
}

function openFavoritePicker() {
  if (!state.token) return openLogin()
  const books = favoriteNotebooks()
  const modal = document.getElementById('modal')
  modal.hidden = false
  const rows = books.map((book) => (
    `<button type="button" class="set-center" data-act="favorite-to" data-id="${book.id}">${esc(book.name)}</button>`
  )).join('')
  modal.innerHTML = `<div class="dialog"><h2>收藏到生词本</h2>${rows || '<p class="muted">还没有生词本</p>'}
    <button class="ghost" type="button" data-act="close-modal">取消</button></div>`
}

async function favoriteTo(id) {
  const word = state.selected
  if (!word) return
  const data = await api(`/notebooks/${id}/words`, { method: 'POST', body: entryPayload(word) })
  closeModal()
  const notebookId = Number(id)
  const name = favoriteNotebooks().find((book) => book.id === notebookId)?.name || '生词本'
  state.cardFavorited = notebookId === state.bookId || state.cardFavorited
  if (data.created !== false) {
    const book = state.books.find((item) => item.id === notebookId)
    if (book) book.wordCount = Number(book.wordCount || 0) + 1
  }
  toast(data.created === false ? `${name}里已经有了` : `已收藏到「${name}」`)
  if (notebookId === state.bookId) return loadWords(true)
  render()
}

async function openRelated(word) {
  const text = String(word || '').trim()
  if (!text) return
  const modal = document.getElementById('modal')
  modal.hidden = false
  modal.innerHTML = `<div class="dialog"><h2>${esc(text)}</h2><p class="muted">正在查词…</p></div>`
  try {
    const entry = await api(`/dict?q=${encodeURIComponent(text)}`, { auth: false })
    modal.innerHTML = `<div class="dialog">
      <h2>${esc(entry.text || text)}</h2>
      <p class="muted">${entry.ipaUk ? `UK [${esc(entry.ipaUk)}] ` : ''}${entry.ipaUs ? `US [${esc(entry.ipaUs)}]` : ''}</p>
      ${cardDefsHtml(entry.definitions)}
      <div class="actions">
        <button class="primary" type="button" data-act="speak" data-text="${esc(entry.text || text)}">朗读</button>
        <button class="ghost" type="button" data-act="favorite-current" data-word="${esc(entry.text || text)}">收藏</button>
        <button class="ghost" type="button" data-act="close-modal">关闭</button>
      </div>
    </div>`
    state.relatedEntry = entry
  } catch (error) {
    modal.innerHTML = `<div class="dialog"><h2>${esc(text)}</h2><p>${esc(error.message || '暂时查不到释义')}</p>
      <button class="ghost" type="button" data-act="close-modal">关闭</button></div>`
  }
}

async function makeCardImage() {
  const word = state.selected
  if (!word) return
  if (!state.token) return openLogin()
  state.cardImageBusy = true
  render()
  const meaning = defLines(word.definitions)[0] || ''
  const data = await api('/mnemonic-images', {
    method: 'POST',
    body: { word: word.text, meaningHint: meaning, billing: 'points', provider: state.imageProvider },
  })
  state.cardImage = data.imageBase64 ? `data:image/png;base64,${data.imageBase64}` : ''
  state.cardImageFor = word.id
  state.cardImageBusy = false
  if (data.balance != null) state.points = data.balance
  paintChrome()
  toast(data.cached ? '用了已有配图' : `已生成，花费 ${data.pointsSpent || 0} 搭币`)
  render()
}

async function deleteWord() {
  if (!state.selected || !confirm(`删除「${state.selected.text}」？`)) return
  await api(`/words/${state.selected.id}`, { method: 'DELETE' })
  state.words = state.words.filter((word) => word.id !== state.selected.id)
  state.selected = state.words[0] || null
  toast('已删除')
  render()
}

function createBook() {
  if (!state.token) return openLogin()
  const modal = document.getElementById('modal')
  modal.hidden = false
  modal.innerHTML = `<form class="dialog" id="new-book-form">
    <h2>新建生词本</h2>
    <div class="field">
      <label>名称</label>
      <input name="name" maxlength="20" placeholder="最多 20 个字" autocomplete="off" required />
    </div>
    <div class="actions">
      <button class="ghost" type="button" data-act="close-modal">取消</button>
      <button class="primary" type="submit">创建</button>
    </div>
  </form>`
  modal.querySelector('input[name="name"]')?.focus()
}

async function submitNewBook(form) {
  const name = String(new FormData(form).get('name') || '').trim()
  if (!name) {
    toast('请输入生词本名称')
    return
  }
  const created = await api('/notebooks', { method: 'POST', body: { name } })
  const id = created.item?.id
  closeModal()
  if (id) {
    state.bookId = id
    localStorage.setItem('wb.book', String(id))
  }
  toast('已新建')
  await loadBooks()
}

function deleteBook() {
  const book = selectedNotebook()
  if (!book || book.isLocked) return
  const count = notebookSize(book)
  const message = count > 0
    ? `确定删除「${esc(book.name)}」吗？其中的 ${count} 个词条将一并删除，此操作不可撤销。`
    : `确定删除「${esc(book.name)}」吗？此操作不可撤销。`
  const modal = document.getElementById('modal')
  modal.hidden = false
  modal.dataset.strict = '1'
  document.querySelector('.app')?.setAttribute('inert', '')
  modal.innerHTML = `<div class="dialog confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="delete-book-title">
    <h2 id="delete-book-title">删除生词本</h2>
    <p>${message}</p>
    <div class="actions">
      <button class="ghost" type="button" data-act="close-modal">取消</button>
      <button class="danger" type="button" data-act="delete-book-confirm">删除</button>
    </div>
  </div>`
  modal.querySelector('[data-act="delete-book-confirm"]')?.focus()
}

function notebookSize(book) {
  if (!book) return 0
  const stored = Number(book.wordCount || 0)
  if (book.id === state.bookId && !state.wordFilter.trim()) {
    return Number(state.wordTotal || stored || state.words.length || 0)
  }
  return stored
}

async function confirmDeleteBook() {
  const book = selectedNotebook()
  if (!book || book.isLocked) return
  await api(`/notebooks/${book.id}`, { method: 'DELETE' })
  state.bookId = 0
  closeModal()
  toast('已删除')
  await loadBooks()
}

async function moveBook(delta) {
  const index = state.books.findIndex((book) => book.id === state.bookId)
  const next = index + delta
  if (index < 0 || next < 0 || next >= state.books.length) return
  const copy = state.books.slice()
  const [item] = copy.splice(index, 1)
  copy.splice(next, 0, item)
  await api('/notebooks/order', { method: 'PUT', body: { ids: copy.map((book) => book.id) } })
  state.books = copy
  render()
}

async function copyBook() {
  const source = selectedNotebook()
  if (!source) return
  const targets = state.books.filter((book) => !book.isSystem && !book.isLocked)
  if (!targets.length) return toast('先登录并准备一个自己的生词本')
  const name = prompt(`收藏到哪本？可选：${targets.map((book) => book.name).join('、')}`, targets[0].name)
  const target = targets.find((book) => book.name === String(name || '').trim())
  if (!target) return
  toast('正在收藏，词书较大时会等一会儿')
  const data = await api(`/notebooks/${target.id}/copy-from`, { method: 'POST', body: { sourceNotebookId: source.id } })
  toast(`已加入 ${data.added || 0} 个词`)
  await loadBooks()
}

async function exportBooks() {
  const books = state.books.filter((book) => book.kind === 'user' && !book.isSystem)
  if (!books.length) return toast('没有可导出的个人生词本')
  toast('正在导出')
  const notebooks = []
  for (const book of books) {
    const entries = []
    let cursor = ''
    do {
      const params = new URLSearchParams({ limit: '200' })
      if (cursor) params.set('cursor', cursor)
      const page = await api(`/notebooks/${book.id}/words?${params}`)
      entries.push(...(page.items || []))
      cursor = page.nextCursor || ''
    } while (cursor)
    notebooks.push({
      name: book.name,
      entries: entries.map((word) => ({
        word: word.text,
        isPhrase: word.isPhrase,
        ipaUk: word.ipaUk,
        ipaUs: word.ipaUs,
        definitions: word.definitions,
        examples: (word.examples || []).map((item) => ({ en: item.english, zh: item.chinese })),
        nearWords: word.nearWords,
        synonyms: word.synonyms,
        antonyms: word.antonyms,
        sortOrder: word.sortOrder,
        addedAt: word.addedAtMillis,
      })),
    })
  }
  const blob = new Blob([JSON.stringify({ version: 2, exportedAt: Date.now(), notebooks }, null, 2)], { type: 'application/json' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = 'wordbuddy-notebooks.json'
  link.click()
  toast('已导出')
}

function importBooks() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'application/json'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    try {
      const backup = JSON.parse(await file.text())
      const notebooks = backup.version === 1
        ? [{ name: backup.notebook?.name || '导入的生词本', entries: backup.entries || [] }]
        : backup.notebooks || []
      let added = 0
      for (const book of notebooks) {
        let target = state.books.find((item) => item.name === book.name && item.kind === 'user')
        if (!target) {
          const created = await api('/notebooks', { method: 'POST', body: { name: book.name } })
          target = created.item || created.notebook
          await loadBooks()
          target = state.books.find((item) => item.name === book.name && item.kind === 'user') || target
        }
        const targetId = target?.id
        if (!targetId) continue
        for (const entry of book.entries || []) {
          const text = entry.word || entry.text
          if (!text) continue
          await api(`/notebooks/${targetId}/words`, {
            method: 'POST',
            body: {
              text,
              isPhrase: entry.isPhrase,
              ipaUk: entry.ipaUk,
              ipaUs: entry.ipaUs,
              definitions: entry.definitions || [],
              examples: entry.examples || [],
              nearWords: entry.nearWords || [],
              synonyms: entry.synonyms || [],
              antonyms: entry.antonyms || [],
            },
          })
          added += 1
        }
      }
      toast(`导入完成，写入 ${added} 条`)
      await loadBooks()
    } catch (error) {
      toast(error.message)
    }
  }
  input.click()
}

async function favoriteShort() {
  const clip = state.shorts[state.shortIndex]
  if (!clip) return
  if (!state.token) return openLogin()
  const data = await api(`/shorts/${clip.id}/favorite`, { method: 'POST', body: { favorited: !clip.favorited } })
  clip.favorited = data.favorited
  if (!data.favorited) {
    state.favoriteShorts = state.favoriteShorts.filter((item) => String(item.id) !== String(clip.id))
    state.favoriteSelected = state.favoriteSelected.filter((id) => String(id) !== String(clip.id))
    if (state.showFavorites) {
      state.shorts = state.shorts.filter((item) => String(item.id) !== String(clip.id))
      if (state.shortIndex >= state.shorts.length) state.shortIndex = Math.max(0, state.shorts.length - 1)
    }
  }
  render()
  bindShortVideo()
}

async function unfavoriteListedShort(id) {
  return unfavoriteListedShorts([id])
}

async function unfavoriteListedShorts(ids) {
  if (!state.token) return openLogin()
  const targets = [...new Set((ids || []).map(String).filter(Boolean))]
  if (!targets.length || state.favoriteRemoving) return
  state.favoriteRemoving = true
  if (state.view === 'favorites') render()
  const removed = []
  let failed = 0
  try {
    for (let i = 0; i < targets.length; i += 4) {
      const chunk = targets.slice(i, i + 4)
      const results = await Promise.all(chunk.map(async (id) => {
        try {
          const data = await api(`/shorts/${id}/favorite`, { method: 'POST', body: { favorited: false } })
          return data.favorited === false
        } catch {
          return false
        }
      }))
      chunk.forEach((id, index) => {
        if (results[index]) removed.push(id)
        else failed += 1
      })
    }
  } finally {
    const gone = new Set(removed)
    state.favoriteShorts = state.favoriteShorts.filter((item) => !gone.has(String(item.id)))
    state.favoriteSelected = state.favoriteSelected.filter((id) => !gone.has(String(id)))
    if (state.showFavorites) {
      state.shorts = state.shorts.filter((item) => !gone.has(String(item.id)))
      if (state.shortIndex >= state.shorts.length) state.shortIndex = Math.max(0, state.shorts.length - 1)
    }
    state.favoriteRemoving = false
    if (state.view === 'favorites') render()
  }
  if (failed) toast(`有 ${failed} 个取消失败`)
}

function stepEpisode(delta) {
  const show = state.shows.find((item) => item.id === (state.playingShowId || state.showId))
  const list = show?.episodes || []
  if (!show || !list.length) return
  let index = list.findIndex((item) => item.id === state.episodeId)
  if (index < 0) index = delta > 0 ? -1 : 0
  const next = list[index + delta]
  if (!next) return
  state.showId = show.id
  playEpisode(next.id)
}

function syncVinylMotion() {
  const playing = audioPlaying()
  document.querySelectorAll('[data-vinyl]').forEach((node) => {
    node.classList.toggle('playing', playing && node.dataset.vinyl === state.playingShowId)
  })
  const toggle = document.querySelector('[data-act="audio-toggle"]')
  if (toggle) toggle.textContent = playing && state.playingShowId === state.showId ? '暂停' : '播放'
}

function bindPodcastPlayer() {
  const audio = document.getElementById('player')
  const seek = document.getElementById('pod-seek')
  if (!audio || !seek) return
  const paint = () => {
    const elapsed = document.getElementById('pod-elapsed')
    const total = document.getElementById('pod-total')
    const bar = document.getElementById('pod-seek')
    const live = !Number.isFinite(audio.duration)
    if (elapsed) elapsed.textContent = live && audio.src ? '直播' : clock(audio.currentTime)
    if (total) total.textContent = live ? '' : clock(audio.duration)
    if (!bar) return
    bar.disabled = live
    if (!live && audio.duration > 0 && bar.dataset.drag !== '1') {
      bar.value = String(Math.round((audio.currentTime / audio.duration) * 1000))
    }
  }
  audio.ontimeupdate = paint
  audio.onplay = syncVinylMotion
  audio.onpause = syncVinylMotion
  audio.onended = () => {
    syncVinylMotion()
    if (Number.isFinite(audio.duration)) stepEpisode(1)
  }
  seek.onpointerdown = () => { seek.dataset.drag = '1' }
  seek.onpointerup = () => { delete seek.dataset.drag }
  seek.oninput = () => {
    if (!Number.isFinite(audio.duration) || audio.duration <= 0) return
    audio.currentTime = (Number(seek.value) / 1000) * audio.duration
  }
  paint()
  syncVinylMotion()
}

function playEpisode(id) {
  const show = state.shows.find((item) => item.id === state.showId)
  const episode = show?.episodes?.find((item) => item.id === id)
  if (!episode) return
  state.episodeId = id
  state.playingShowId = show.id
  const audio = document.getElementById('player')
  audio.src = episode.audioUrl
  audio.play().catch((error) => toast(error.message))
  render()
}

async function doCheckin() {
  const data = await api('/me/checkin', { method: 'POST', body: {} })
  state.checkIn = data.checkIn
  state.points = data.totalPoints ?? data.checkIn?.totalPoints ?? state.points
  toast(data.already ? '今天已经签过了' : `签到 +${data.pointsEarned || 0}`)
  render()
}

async function doMakeup(date) {
  const data = await api('/me/checkin/makeup', { method: 'POST', body: { date } })
  if (!data.ok) return toast(data.error || '补签失败')
  state.checkIn = data.checkIn || state.checkIn
  toast(`补签 +${data.pointsEarned || 0}`)
  await loadMeTab()
}

async function redeemGift(id) {
  const gift = state.gifts.find((item) => item.id === id)
  if (!gift) return
  const body = {}
  if (gift.needAddress) {
    body.name = prompt('收货姓名', state.me?.user?.shippingName || '')
    body.phone = prompt('收货手机', state.me?.user?.shippingPhone || '')
    body.detail = prompt('收货地址', state.me?.user?.shippingDetail || '')
    if (!body.name || !body.phone || !body.detail) return
  }
  const data = await api(`/gifts/${id}/redeem`, { method: 'POST', body })
  state.points = data.totalPoints ?? state.points
  toast(data.message || '兑换成功')
  paintChrome()
}

async function buyPack(packageId) {
  const channel = confirm('确定用支付宝下单？取消则改用微信下单。') ? 'alipay' : 'wechat'
  const data = await api('/me/point-orders', { method: 'POST', body: { packageId, channel } })
  if (data.sandbox) {
    if (confirm('当前是沙箱，是否模拟支付成功？')) {
      const paid = await api(`/me/point-orders/${data.order.id}/simulate-pay`, { method: 'POST', body: {} })
      state.points = paid.balance ?? state.points
      toast('沙箱支付完成')
    }
  } else {
    toast('订单已创建。请在 Android 里用微信或支付宝完成这笔支付。')
  }
  await loadMeTab()
}

async function withdraw(amountFen) {
  const channel = confirm('提到支付宝？取消则改为微信。') ? 'alipay' : 'wechat'
  const account = prompt(channel === 'alipay' ? '支付宝账号' : '微信账号', '')
  const realName = prompt('真实姓名', state.me?.user?.shippingName || '')
  if (!account || !realName) return
  const data = await api('/me/withdrawals', { method: 'POST', body: { channel, account, realName, amountFen } })
  state.points = data.totalPoints ?? state.points
  toast(data.message || '已提交')
  await loadMeTab()
}

async function copyInvite() {
  const info = state.invite
  const text = `我在用「你的词搭子」背单词，邀请你一起来！\n邀请码：${info.inviteCode}\n打开链接注册：${info.inviteUrl}`
  await navigator.clipboard.writeText(text)
  toast('已复制')
}

async function sendCode(phone) {
  await api('/auth/send-code', { method: 'POST', auth: false, body: { phone } })
  toast('验证码已发送')
}

async function sendLoginCode() {
  const form = document.getElementById('login-form')
  if (authPanel !== 'account' && !form?.agree?.checked) {
    throw new Error('请先阅读并同意《用户协议》和《隐私政策》')
  }
  const phone = (form?.phone?.value || authDraft.phone || '').trim()
  if (authCodeUntil > Date.now()) return
  await sendCode(phone)
  authCodeUntil = Date.now() + 60000
  clearInterval(authCodeTimer)
  authCodeTimer = setInterval(tickAuthCode, 400)
  tickAuthCode()
}

async function acceptLogin(token) {
  if (!token) throw new Error('登录没有返回凭证')
  state.token = token
  localStorage.setItem('wb.token', token)
  closeModal()
  await refreshMe()
  toast('已登录')
  render()
}

async function submitLogin(form) {
  if (authPanel === 'account') {
    const data = await api('/auth/login', {
      method: 'POST',
      auth: false,
      body: { phone: form.account.value.trim(), password: form.password.value },
    })
    return acceptLogin(data.token)
  }
  if (authPanel === 'setup' || authPanel === 'reset') {
    const phone = authPanel === 'reset' ? form.phone.value.trim() : authDraft.phone
    const code = authPanel === 'reset' ? form.code.value.trim() : authDraft.code
    const password = form.password.value
    if (password !== form.confirm.value) throw new Error('两次密码不一致')
    if (authPanel === 'reset' && !form.agree?.checked) throw new Error('请先阅读并同意《用户协议》和《隐私政策》')
    const data = await api('/auth/register', {
      method: 'POST',
      auth: false,
      body: { phone, code, password, inviteCode: form.inviteCode?.value.trim() || '' },
    })
    return acceptLogin(data.token)
  }
  if (!form.agree?.checked) throw new Error('请先阅读并同意《用户协议》和《隐私政策》')
  const phone = form.phone.value.trim()
  const code = form.code.value.trim()
  const data = await api('/auth/login', { method: 'POST', auth: false, body: { phone, code } })
  if (data.isNewUser) {
    authDraft = { phone, code }
    authPanel = 'setup'
    paintAuthMain()
    toast('这个手机号还没有账号，请设置密码')
    return
  }
  return acceptLogin(data.token)
}

async function toggleReminder() {
  if (!state.reminder && Notification.permission !== 'granted') {
    const permission = await Notification.requestPermission()
    if (permission !== 'granted') return toast('浏览器没有允许通知')
  }
  state.reminder = !state.reminder
  localStorage.setItem('wb.reminder', state.reminder ? '1' : '0')
  render()
}

function togglePref(key) {
  if (key === 'reminder') return toggleReminder()
  const fields = {
    autoSpeak: 'wb.autoSpeak',
    podcastBackground: 'wb.podcastBg',
    shortsMeta: 'wb.shortsMeta',
    shortLoop: 'wb.shortLoop',
    biometric: 'wb.biometric',
  }
  const storage = fields[key]
  if (!storage || !(key in state)) return
  state[key] = !state[key]
  localStorage.setItem(storage, state[key] ? '1' : '0')
  if (key === 'shortsMeta') state.shortMetaShown = null
  if (key === 'biometric' && state.biometric) toast('指纹验证在手机 App 里生效，网页不会因此锁住')
  render()
}

function formatBytes(bytes) {
  const n = Number(bytes) || 0
  if (n < 1024) return `${Math.round(n)} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

async function refreshCacheLabel() {
  let bytes = 0
  try {
    if (navigator.storage?.estimate) {
      const est = await navigator.storage.estimate()
      bytes = est.usage || 0
    }
  } catch { /* size stays unknown */ }
  state.cacheLabel = formatBytes(bytes)
  const slot = document.getElementById('cache-label')
  if (slot && !state.cacheBusy) slot.textContent = `短视频 / 播客本地缓存 · 当前 ${state.cacheLabel}`
}

async function refreshSavedFlag() {
  const bookId = saveBookId()
  if (!state.token || !state.entry || !bookId) return
  try {
    const pos = await api(`/notebooks/${bookId}/position?text=${encodeURIComponent(state.entry.text)}`)
    state.savedWordId = pos.found ? pos.wordId || null : null
  } catch {
    state.savedWordId = null
  }
}

async function clearMediaCache() {
  if (state.cacheBusy) return
  if (!confirm(`将清除短视频与播客的本地媒体缓存（约 ${state.cacheLabel}）。下次播放会重新从网络加载。不影响账号与词库数据。`)) return
  state.cacheBusy = true
  render()
  try {
    if (window.caches) {
      const keys = await caches.keys()
      await Promise.all(keys.map((key) => caches.delete(key)))
    }
    toast('缓存已清除')
  } catch (error) {
    toast(error.message || '清除失败')
  }
  state.cacheBusy = false
  await refreshCacheLabel()
  if (state.view === 'settings') render()
}

document.getElementById('nav').addEventListener('click', (event) => {
  const button = event.target.closest('[data-view]')
  if (button) setView(button.dataset.view)
})

document.getElementById('side-user').addEventListener('click', (event) => {
  event.stopPropagation()
  const node = event.target.closest('[data-act]')
  if (!node) return
  if (node.dataset.act === 'toggle-account') {
    accountMenuOpen = !accountMenuOpen
    document.getElementById('side-user').classList.toggle('open', accountMenuOpen)
    return
  }
  accountMenuOpen = false
  document.getElementById('side-user').classList.remove('open')
  onAct(node.dataset.act, node).catch((error) => toast(error.message))
})

document.addEventListener('click', () => {
  if (!accountMenuOpen) return
  accountMenuOpen = false
  document.getElementById('side-user')?.classList.remove('open')
})

document.addEventListener('keydown', (event) => {
  if (event.key !== 'Escape') return
  const modal = document.getElementById('modal')
  if (modal && !modal.hidden) {
    event.preventDefault()
    closeModal()
    return
  }
  if (!accountMenuOpen) return
  accountMenuOpen = false
  document.getElementById('side-user')?.classList.remove('open')
})

document.addEventListener('visibilitychange', () => {
  const audio = document.getElementById('player')
  if (!audio) return
  if (document.hidden) {
    if (state.podcastBackground || audio.paused) {
      state.podcastPausedByHide = false
      return
    }
    state.podcastPausedByHide = true
    audio.pause()
    return
  }
  if (state.podcastPausedByHide) {
    state.podcastPausedByHide = false
    audio.play().catch(() => {})
  }
})

document.getElementById('main').addEventListener('click', (event) => {
  const node = event.target.closest('[data-act]')
  if (!node || node.closest('form') && node.type === 'submit') return
  onAct(node.dataset.act, node).catch((error) => toast(error.message))
})

document.getElementById('main').addEventListener('input', (event) => {
  if (event.target.id === 'word-filter') {
    state.wordFilter = event.target.value
    clearTimeout(state.wordFilterTimer)
    state.wordFilterTimer = setTimeout(() => {
      loadWords(true, { keepSelection: true, listOnly: true }).catch((error) => toast(error.message))
    }, 250)
    return
  }
  if (event.target.id !== 'card-seek') return
  const label = document.getElementById('card-page')
  const total = state.wordTotal || state.words.length
  if (label) label.textContent = `${Number(event.target.value) + 1} / ${total}`
})

document.getElementById('main').addEventListener('change', (event) => {
  if (event.target.id !== 'card-seek') return
  jumpWord(Number(event.target.value)).catch((error) => toast(error.message))
})

document.getElementById('main').addEventListener('submit', (event) => {
  const form = event.target
  event.preventDefault()
  const run = async () => {
    if (form.id === 'lookup-form') return lookup(new FormData(form).get('q'))
    if (form.id === 'tip-form') {
      await api('/homophones', { method: 'POST', body: { word: state.entry.text, body: new FormData(form).get('body') } })
      return lookup(state.entry.text)
    }
    if (form.id === 'note-form') {
      const meaning = String(new FormData(form).get('meaning') || '').trim()
      const definitions = meaning ? meaning.split('\n').map((line) => ({ pos: '', meaning: line.trim() })).filter((item) => item.meaning) : []
      await api('/word-notes', { method: 'PUT', body: { word: state.entry.text, definitions } })
      toast('释义已保存')
      return
    }
    if (form.id === 'edit-word' && state.selected) {
      const meaning = String(new FormData(form).get('meaning') || '')
      const definitions = meaning.split('\n').map((line) => ({ pos: '', meaning: line.trim() })).filter((item) => item.meaning)
      if (selectedNotebook()?.isLocked) {
        await api('/word-notes', { method: 'PUT', body: { word: state.selected.text, definitions } })
        state.cardNotes = definitions
        toast('释义已保存')
      } else {
        const data = await api(`/words/${state.selected.id}`, { method: 'PATCH', body: { definitions } })
        state.selected = data.item || state.selected
        toast('已保存')
      }
      state.cardEdit = false
      return render()
    }
    if (form.id === 'profile-form') {
      const raw = Object.fromEntries(new FormData(form).entries())
      await api('/me', { method: 'PATCH', body: raw })
      toast('资料已保存')
      return refreshMe()
    }
    if (form.id === 'password-form') {
      const raw = Object.fromEntries(new FormData(form).entries())
      const data = await api('/auth/change-password', { method: 'POST', body: raw })
      if (data.token) {
        state.token = data.token
        localStorage.setItem('wb.token', data.token)
      }
      toast('密码已修改')
      return
    }
    if (form.id === 'phone-form') {
      const raw = Object.fromEntries(new FormData(form).entries())
      const data = await api('/auth/change-phone', { method: 'POST', body: raw })
      if (data.token) {
        state.token = data.token
        localStorage.setItem('wb.token', data.token)
      }
      toast('手机号已更换')
      return refreshMe()
    }
    if (form.id === 'deletion-form') {
      const raw = Object.fromEntries(new FormData(form).entries())
      const data = await api('/me/deletion', {
        method: 'POST',
        body: { agreed: raw.agreed === 'on', force: raw.force === 'on', forceAgreed: raw.force === 'on', code: raw.code, reason: raw.reason },
      })
      if (data.deleted || data.immediate) {
        logout(false)
        toast('账号已注销')
        return render()
      }
      toast('已提交注销申请')
      return loadMeTab()
    }
    if (form.id === 'bind-invite') {
      await api('/me/invite/bind', { method: 'POST', body: { inviteCode: new FormData(form).get('code') } })
      toast('邀请码已绑定')
      return loadMeTab()
    }
  }
  run().catch((error) => toast(error.message))
})

document.getElementById('main').addEventListener('change', (event) => {
  if (event.target.id !== 'avatar-file' || !event.target.files?.[0]) return
  const file = event.target.files[0]
  createImageBitmap(file).then((bitmap) => {
    const scale = Math.min(1, 720 / Math.max(bitmap.width, bitmap.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(bitmap.width * scale))
    canvas.height = Math.max(1, Math.round(bitmap.height * scale))
    canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    return api('/me/avatar', { method: 'POST', body: { imageBase64: canvas.toDataURL('image/jpeg', 0.85) } })
  }).then(() => {
    toast('头像已更新')
    return refreshMe()
  }).catch((error) => toast(error.message))
})

document.getElementById('modal').addEventListener('click', (event) => {
  if (event.target.id === 'modal') {
    if (event.currentTarget.dataset.strict === '1') return
    closeModal()
  }
  const node = event.target.closest('[data-act]')
  if (!node) return
  onAct(node.dataset.act, node).catch((error) => toast(error.message))
})

document.getElementById('modal').addEventListener('submit', (event) => {
  if (event.target.id === 'login-form') {
    event.preventDefault()
    submitLogin(event.target).catch((error) => toast(error.message))
    return
  }
  if (event.target.id === 'new-book-form') {
    event.preventDefault()
    submitNewBook(event.target).catch((error) => toast(error.message))
  }
})

let shortWheelAt = 0
let exampleWheelAt = 0
document.addEventListener('wheel', (event) => {
  const exampleCard = event.target instanceof Element ? event.target.closest('.example-card') : null
  if (exampleCard && state.view === 'books' && (state.selected?.examples || []).length > 1) {
    event.preventDefault()
    if (exampleBusy) return
    const now = Date.now()
    if (now - exampleWheelAt < 500) return
    const delta = Math.abs(event.deltaX) > Math.abs(event.deltaY) ? event.deltaX : event.deltaY
    if (Math.abs(delta) < 30) return
    const next = state.cardExample + (delta > 0 ? 1 : -1)
    const count = state.selected.examples.length
    if (next < 0 || next >= count) return
    exampleWheelAt = now
    stepExample(delta > 0 ? 1 : -1)
    return
  }
  if (state.view !== 'shorts' || !state.shorts.length || state.shortBusy) return
  if (event.target instanceof Element && event.target.closest('input, textarea, select')) return
  event.preventDefault()
  const now = Date.now()
  if (now - shortWheelAt < 500) return
  if (Math.abs(event.deltaY) < 30) return
  shortWheelAt = now
  stepShort(event.deltaY > 0 ? 1 : -1).catch((error) => toast(error.message))
}, { passive: false })

document.addEventListener('keydown', (event) => {
  if (!document.getElementById('modal').hidden) return
  if (state.view !== 'books' || event.target.matches('input, textarea')) return
  if (event.key === 'ArrowRight') stepWord(1)
  if (event.key === 'ArrowLeft') stepWord(-1)
  if (event.key === ' ') {
    event.preventDefault()
    toggleCardPlay()
  }
})

setInterval(() => {
  if (!state.reminder || Notification.permission !== 'granted') return
  const now = new Date()
  const shanghai = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Shanghai', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).format(now)
  const day = shanghaiToday()
  if (shanghai.startsWith('20:0') && localStorage.getItem('wb.reminded') !== day) {
    localStorage.setItem('wb.reminded', day)
    new Notification('你的词搭子', { body: '晚上 8 点了，来复习几个单词。' })
  }
}, 30000)

applyPrefs()
render()
if (state.token) refreshMe().catch(() => logout())
if (state.view === 'settings') {
  state.meTab = 'settings'
  loadMeTab().catch((error) => toast(error.message))
} else if (state.view === 'favorites') {
  state.favoriteBusy = true
  loadFavoriteShorts().catch((error) => toast(error.message))
} else if (state.view === 'me') {
  loadMeTab().catch((error) => toast(error.message))
} else if (NAV.some(([id]) => id === state.view) && state.view !== 'lookup') setView(state.view)
