import type { Definition, ExampleSentence, VocabEntry } from '../types'

const TOKEN_KEY = 'wordbuddy_token'
const BASE = '/api/backend'

export class ApiError extends Error {
  code: string | null
  httpCode: number
  constructor(message: string, code: string | null = null, httpCode = 0) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpCode = httpCode
  }
}

export type UserSession = {
  token: string
  userId: number
  phone: string
  vocabNotebookId: number
  avatarUrl?: string | null
  level: number
  nickname?: string | null
  buddyId?: string | null
  signature?: string | null
  region?: string | null
  networkRegion?: string | null
}

export type AuthResult = {
  session: UserSession | null
  isNewUser: boolean
  needsPhoneBind: boolean
}

export type Notebook = {
  id: number
  name: string
  sortOrder: number
  createdAtMillis: number
  kind: string
  slug?: string | null
  wordCount: number
}

export type WordPage = {
  items: VocabEntry[]
  total: number
  nextCursor: string | null
  fromIndex: number
}

export type CheckInState = {
  totalPoints: number
  streakDays: number
  lastCheckInDate: string | null
  checkedInToday: boolean
  todayReward: number
  recentDates: string[]
}

export type CheckInSuccess = {
  kind: 'success'
  pointsEarned: number
  streakDays: number
  totalPoints: number
}

export type CheckInOutcome =
  | CheckInSuccess
  | { kind: 'already' }

export type PointPackage = {
  id: string
  title: string
  subtitle: string
  priceFen: number
  points: number
  badge?: string | null
}

export type PointPackagesPayload = {
  items: PointPackage[]
  aiImagePointsCost: number
  sandbox: boolean
}

export type ShortClip = {
  id: string
  videoUrl: string
  title: string
  author: string
  caption: string
  relatedWords: string[]
  category: string
  categoryName: string
  coverUrl?: string | null
  favorited: boolean
}

function enc(value: string): string {
  return encodeURIComponent(value)
}

function optStr(obj: Record<string, unknown>, key: string): string | null {
  const v = obj[key]
  if (v == null) return null
  const s = String(v).trim()
  return s || null
}

function parseDefinitions(raw: unknown): Definition[] {
  if (!Array.isArray(raw)) return []
  return raw.map((item) => {
    const o = item as Record<string, unknown>
    return {
      pos: String(o.pos ?? '').trim(),
      meaning: String(o.meaning ?? '').trim(),
      isUserAdded: Boolean(o.isUserAdded),
    }
  })
}

function parseExamples(raw: unknown): ExampleSentence[] {
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      const o = item as Record<string, unknown>
      const english = String(o.english ?? o.en ?? '').trim()
      const chinese = String(o.chinese ?? o.zh ?? '').trim()
      if (!english) return null
      return { english, chinese }
    })
    .filter((x): x is ExampleSentence => x != null)
}

function parseWord(obj: Record<string, unknown>): VocabEntry {
  return {
    id: Number(obj.id),
    notebookId: Number(obj.notebookId ?? 0),
    text: String(obj.text ?? ''),
    isPhrase: Boolean(obj.isPhrase),
    ipaUk: optStr(obj, 'ipaUk'),
    ipaUs: optStr(obj, 'ipaUs'),
    definitions: parseDefinitions(obj.definitions),
    examples: parseExamples(obj.examples),
    nearWords: Array.isArray(obj.nearWords) ? obj.nearWords.map(String) : [],
    synonyms: Array.isArray(obj.synonyms) ? obj.synonyms.map(String) : [],
    antonyms: Array.isArray(obj.antonyms) ? obj.antonyms.map(String) : [],
    sortOrder: Number(obj.sortOrder ?? 0),
    addedAtMillis: Number(obj.addedAtMillis ?? Date.now()),
  }
}

function parseNotebook(obj: Record<string, unknown>): Notebook {
  return {
    id: Number(obj.id),
    name: String(obj.name ?? ''),
    sortOrder: Number(obj.sortOrder ?? 0),
    createdAtMillis: Number(obj.createdAtMillis ?? 0),
    kind: String(obj.kind ?? 'user'),
    slug: optStr(obj, 'slug'),
    wordCount: Number(obj.wordCount ?? 0),
  }
}

function parseUserSession(token: string, root: Record<string, unknown>, user: Record<string, unknown>): UserSession {
  const avatarUrl = optStr(root, 'avatarUrl') ?? optStr(user, 'avatarUrl')
  const levelRaw = user.level ?? root.level
  const level = Math.min(7, Math.max(0, Number(levelRaw ?? 0)))
  return {
    token,
    userId: Number(user.id ?? 0),
    phone: String(user.phone ?? ''),
    vocabNotebookId: Number(root.vocabNotebookId ?? 0),
    avatarUrl,
    level,
    nickname: optStr(user, 'nickname'),
    buddyId: optStr(user, 'buddyId'),
    signature: optStr(user, 'signature'),
    region: optStr(user, 'region'),
    networkRegion: optStr(user, 'networkRegion'),
  }
}

function parseCheckIn(obj: Record<string, unknown> | null | undefined): CheckInState {
  if (!obj) {
    return {
      totalPoints: 0,
      streakDays: 0,
      lastCheckInDate: null,
      checkedInToday: false,
      todayReward: 1,
      recentDates: [],
    }
  }
  const recentDates = Array.isArray(obj.recentDates)
    ? obj.recentDates.map((d) => String(d).trim()).filter(Boolean)
    : []
  return {
    totalPoints: Math.max(0, Number(obj.totalPoints ?? 0)),
    streakDays: Math.max(0, Number(obj.streakDays ?? 0)),
    lastCheckInDate: optStr(obj, 'lastCheckInDate'),
    checkedInToday: Boolean(obj.checkedInToday),
    todayReward: Math.min(7, Math.max(1, Number(obj.todayReward ?? 1))),
    recentDates,
  }
}

function parseShortClip(obj: Record<string, unknown>): ShortClip {
  const kw = (obj.keywords ?? obj.relatedWords) as unknown
  const relatedWords = Array.isArray(kw) ? kw.map((w) => String(w).trim()).filter(Boolean) : []
  return {
    id: String(obj.id ?? ''),
    videoUrl: String(obj.videoUrl ?? ''),
    title: String(obj.title ?? ''),
    author: String(obj.author ?? '词搭子'),
    caption: String(obj.caption ?? ''),
    relatedWords,
    category: String(obj.category ?? 'speaking'),
    categoryName: String(obj.categoryName ?? '口语'),
    coverUrl: optStr(obj, 'coverUrl'),
    favorited: Boolean(obj.favorited),
  }
}

function wordBody(entry: VocabEntry): Record<string, unknown> {
  return {
    text: entry.text,
    isPhrase: entry.isPhrase,
    ipaUk: entry.ipaUk,
    ipaUs: entry.ipaUs,
    definitions: entry.definitions.map((d) => ({
      pos: d.pos,
      meaning: d.meaning,
      isUserAdded: d.isUserAdded ?? false,
    })),
    examples: entry.examples.map((e) => ({ english: e.english, chinese: e.chinese })),
    nearWords: entry.nearWords,
    synonyms: entry.synonyms,
    antonyms: entry.antonyms,
  }
}

export function getStoredToken(): string | null {
  if (typeof localStorage === 'undefined') return null
  const t = localStorage.getItem(TOKEN_KEY)?.trim()
  return t || null
}

export function setStoredToken(token: string | null) {
  if (typeof localStorage === 'undefined') return
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export class WordBuddyApi {
  private token: string | null

  constructor(token?: string | null) {
    this.token = token ?? getStoredToken()
  }

  setToken(token: string | null) {
    this.token = token
  }

  private async request<T = Record<string, unknown>>(
    method: string,
    path: string,
    options?: { auth?: boolean; body?: Record<string, unknown> },
  ): Promise<T> {
    const useAuth = options?.auth !== false && this.token
    const headers: Record<string, string> = {
      Accept: 'application/json',
      'User-Agent': 'WordBuddy-Web/0.1 (PC)',
    }
    if (useAuth && this.token) headers.Authorization = `Bearer ${this.token}`
    const init: RequestInit = { method, headers }
    if (options?.body != null) {
      headers['Content-Type'] = 'application/json; charset=utf-8'
      init.body = JSON.stringify(options.body)
    }
    const res = await fetch(`${BASE}${path}`, init)
    const text = await res.text()
    let json: Record<string, unknown> = {}
    if (text) {
      try {
        json = JSON.parse(text) as Record<string, unknown>
      } catch {
        json = {}
      }
    }
    if (!res.ok) {
      const errCode = optStr(json, 'code')
      const message = optStr(json, 'error') ?? `请求失败 (${res.status})`
      throw new ApiError(message, errCode, res.status)
    }
    return json as T
  }

  async sendCode(phone: string): Promise<string | null> {
    const root = await this.request('POST', '/auth/send-code', {
      auth: false,
      body: { phone },
    })
    const debug = root.debugCode
    if (debug != null && String(debug).trim()) return String(debug)
    return null
  }

  async login(phone: string, code: string): Promise<AuthResult> {
    return this.parseAuth(
      await this.request('POST', '/auth/login', { auth: false, body: { phone, code } }),
    )
  }

  async loginWithPassword(phone: string, password: string): Promise<AuthResult> {
    return this.parseAuth(
      await this.request('POST', '/auth/login', { auth: false, body: { phone, password } }),
    )
  }

  async register(phone: string, code: string, password: string, inviteCode?: string): Promise<AuthResult> {
    const body: Record<string, unknown> = { phone, code, password }
    const invite = inviteCode?.trim().toLowerCase()
    if (invite) body.inviteCode = invite
    return this.parseAuth(await this.request('POST', '/auth/register', { auth: false, body }))
  }

  private parseAuth(root: Record<string, unknown>): AuthResult {
    const isNewUser = Boolean(root.isNewUser)
    const needsPhoneBind = Boolean(root.needsPhoneBind)
    const token = optStr(root, 'token')
    if (!token) {
      return { session: null, isNewUser, needsPhoneBind }
    }
    const user = root.user as Record<string, unknown> | undefined
    if (!user) return { session: null, isNewUser, needsPhoneBind }
    const session = parseUserSession(token, root, user)
    return {
      session,
      isNewUser,
      needsPhoneBind: needsPhoneBind || !session.phone,
    }
  }

  async fetchMe(): Promise<UserSession | null> {
    if (!this.token) return null
    const root = await this.request('GET', '/me')
    const user = root.user as Record<string, unknown> | undefined
    if (!user) return null
    return parseUserSession(this.token, root, user)
  }

  async updateProfile(fields: {
    nickname?: string
    signature?: string
    region?: string
    gender?: string
    email?: string
  }): Promise<UserSession> {
    if (!this.token) throw new ApiError('请先登录')
    const root = await this.request('PATCH', '/me', { body: fields })
    const user = root.user as Record<string, unknown>
    return parseUserSession(this.token, root, user)
  }

  async listNotebooks(): Promise<Notebook[]> {
    if (!this.token) throw new ApiError('请先登录')
    const root = await this.request('GET', '/notebooks')
    const items = root.items as unknown
    if (!Array.isArray(items)) return []
    return items.map((i) => parseNotebook(i as Record<string, unknown>))
  }

  async listCatalogs(): Promise<Notebook[]> {
    const root = await this.request('GET', '/catalogs', { auth: false })
    const items = root.items as unknown
    if (!Array.isArray(items)) return []
    return items.map((i) => parseNotebook(i as Record<string, unknown>))
  }

  async createNotebook(name: string): Promise<Notebook> {
    if (!this.token) throw new ApiError('请先登录')
    const root = await this.request('POST', '/notebooks', { body: { name: name.trim() } })
    const item = root.item as Record<string, unknown>
    return parseNotebook(item)
  }

  async deleteNotebook(id: number): Promise<void> {
    if (!this.token) throw new ApiError('请先登录')
    await this.request('DELETE', `/notebooks/${id}`)
  }

  async listWords(
    notebookId: number,
    cursor?: string | null,
    limit = 100,
    fromIndex = 0,
  ): Promise<WordPage> {
    let path = `/notebooks/${notebookId}/words?limit=${limit}`
    if (cursor) path += `&cursor=${enc(cursor)}`
    else if (fromIndex > 0) path += `&fromIndex=${fromIndex}`
    const root = await this.request('GET', path, { auth: Boolean(this.token) })
    const items = root.items as unknown
    const list = Array.isArray(items)
      ? items.map((i) => parseWord(i as Record<string, unknown>))
      : []
    return {
      items: list,
      total: Number(root.total ?? list.length),
      nextCursor: optStr(root, 'nextCursor'),
      fromIndex: Number(root.fromIndex ?? fromIndex),
    }
  }

  async createWord(notebookId: number, entry: VocabEntry): Promise<VocabEntry> {
    if (!this.token) throw new ApiError('请先登录')
    const root = await this.request('POST', `/notebooks/${notebookId}/words`, {
      body: wordBody(entry),
    })
    return parseWord(root.item as Record<string, unknown>)
  }

  async updateWord(
    id: number,
    patch: {
      definitions?: Definition[]
      examples?: ExampleSentence[]
      nearWords?: string[]
      synonyms?: string[]
      antonyms?: string[]
    },
  ): Promise<VocabEntry> {
    if (!this.token) throw new ApiError('请先登录')
    const root = await this.request('PATCH', `/words/${id}`, { body: patch })
    return parseWord(root.item as Record<string, unknown>)
  }

  async deleteWord(id: number): Promise<void> {
    if (!this.token) throw new ApiError('请先登录')
    await this.request('DELETE', `/words/${id}`)
  }

  async getCheckIn(): Promise<CheckInState> {
    if (!this.token) throw new ApiError('请先登录')
    const root = await this.request('GET', '/me/checkin')
    return parseCheckIn(root.checkIn as Record<string, unknown> | undefined)
  }

  async performCheckIn(): Promise<CheckInOutcome> {
    if (!this.token) throw new ApiError('请先登录')
    const root = await this.request('POST', '/me/checkin', { body: {} })
    const checkIn = parseCheckIn(root.checkIn as Record<string, unknown> | undefined)
    if (root.already === true || root.ok === false) {
      return { kind: 'already' }
    }
    return {
      kind: 'success',
      pointsEarned: Number(root.pointsEarned ?? checkIn.todayReward),
      streakDays: Number(root.streakDays ?? checkIn.streakDays),
      totalPoints: Number(root.totalPoints ?? checkIn.totalPoints),
    }
  }

  async fetchPointPackages(): Promise<PointPackagesPayload> {
    const root = await this.request('GET', '/point-packages', { auth: false })
    const items = root.items as unknown
    const list: PointPackage[] = Array.isArray(items)
      ? items.map((raw) => {
          const o = raw as Record<string, unknown>
          return {
            id: String(o.id ?? ''),
            title: String(o.title ?? ''),
            subtitle: String(o.subtitle ?? ''),
            priceFen: Number(o.priceFen ?? 0),
            points: Number(o.points ?? 0),
            badge: optStr(o, 'badge'),
          }
        })
      : []
    return {
      items: list,
      aiImagePointsCost: Number(root.aiImagePointsCost ?? 5),
      sandbox: Boolean(root.sandbox),
    }
  }

  async shortsFeed(limit = 24, excludeIds: string[] = []): Promise<ShortClip[]> {
    let path = `/shorts/feed?limit=${limit}`
    const exclude = excludeIds.filter(Boolean).join(',')
    if (exclude) path += `&exclude=${enc(exclude)}`
    const root = await this.request('GET', path)
    const items = root.items as unknown
    if (!Array.isArray(items)) return []
    return items.map((i) => parseShortClip(i as Record<string, unknown>))
  }

  async shortFavorite(videoId: string, favorited: boolean): Promise<boolean> {
    if (!this.token) throw new ApiError('请先登录')
    const root = await this.request('POST', `/shorts/${enc(videoId)}/favorite`, {
      body: { favorited },
    })
    return Boolean(root.favorited ?? favorited)
  }

  async listShortFavorites(page = 1, pageSize = 40): Promise<ShortClip[]> {
    if (!this.token) throw new ApiError('请先登录')
    const root = await this.request('GET', `/me/short-favorites?page=${page}&pageSize=${pageSize}`)
    const items = root.items as unknown
    if (!Array.isArray(items)) return []
    return items.map((i) => parseShortClip(i as Record<string, unknown>))
  }
}

export const api = new WordBuddyApi()
