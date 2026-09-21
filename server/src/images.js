import { query } from './db.js'
import { adjustPoints, aiImagePointsCost, getPointsBalance } from './points.js'

const inflight = new Map()

function fail(status, message) {
  const error = new Error(message)
  error.status = status
  return error
}

export function normalizeImageProvider(raw) {
  const value = String(raw || '').trim().toLowerCase()
  if (value === 'siliconflow' || value === 'silicon_flow') return 'siliconflow'
  return 'pollinations'
}

function wordKey(word) {
  return String(word || '').trim().toLowerCase().slice(0, 80)
}

function meaningKey(hint) {
  return String(hint || '').trim().replace(/\s+/g, ' ').slice(0, 120).toLowerCase()
}

function hasChinese(text) {
  return /[\u4e00-\u9fff]/.test(String(text || ''))
}

function siliconFlowBase() {
  return String(process.env.SILICONFLOW_API_BASE || 'https://api.siliconflow.com/v1').replace(/\/$/, '')
}

function siliconFlowKey() {
  const apiKey = String(process.env.SILICONFLOW_API_KEY || '').trim()
  if (!apiKey) throw fail(503, '服务器未配置硅基流动密钥')
  return apiKey
}

function mnemonicPrompt(word, scene) {
  const picture = String(scene || '').trim()
  if (picture) {
    return `simple cute educational mnemonic illustration of ${picture}, clean white background, no text, no letters, no watermark, everyday life scene`
  }
  return `simple cute educational mnemonic illustration for English word "${String(word).trim()}", clean white background, no text, no letters, no watermark, everyday life scene`
}

async function englishScene(word, meaningHint) {
  const hint = String(meaningHint || '').trim().slice(0, 80)
  if (!hint) return ''
  if (!hasChinese(hint)) return hint
  const model = String(process.env.SILICONFLOW_TEXT_MODEL || 'Qwen/Qwen2.5-7B-Instruct').trim()
  const response = await fetch(`${siliconFlowBase()}/chat/completions`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${siliconFlowKey()}`,
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify({
      model,
      temperature: 0,
      max_tokens: 80,
      messages: [
        {
          role: 'system',
          content:
            'Turn the selected dictionary sense into one short English scene for an illustration. Match only that sense, not other meanings of the word. Output one English line, no Chinese, no quotes, no explanation.',
        },
        {
          role: 'user',
          content: `Word: ${String(word).trim()}\nSense: ${hint}`,
        },
      ],
    }),
    signal: AbortSignal.timeout(30_000),
  })
  const text = await response.text()
  if (!response.ok) throw fail(502, '没能把词义译成英文画面')
  let scene = ''
  try {
    scene = String(JSON.parse(text)?.choices?.[0]?.message?.content || '')
  } catch {
    scene = ''
  }
  scene = scene.replace(/^["'`]+|["'`]+$/g, '').split('\n')[0].trim().slice(0, 180)
  if (!scene || hasChinese(scene)) throw fail(502, '没能把词义译成英文画面')
  return scene
}

function isImage(bytes) {
  if (!bytes || bytes.length < 8) return false
  if (bytes[0] === 0xff && bytes[1] === 0xd8) return true
  if (bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47) return true
  const head = bytes.subarray(0, 12).toString('ascii')
  return head.startsWith('RIFF') && head.includes('WEBP')
}

async function readImageResponse(response) {
  const bytes = Buffer.from(await response.arrayBuffer())
  if (!response.ok) {
    const message = bytes.toString('utf8').slice(0, 180)
    throw fail(502, message || `生图失败（${response.status}）`)
  }
  if (!isImage(bytes) || bytes.length > 2_000_000) {
    throw fail(502, '生图结果不是可用图片')
  }
  return bytes
}

async function fetchPollinations(prompt) {
  const seed = Math.floor(Math.random() * 999_999) + 1
  const url =
    `https://image.pollinations.ai/prompt/${encodeURIComponent(prompt)}` +
    `?width=768&height=768&nologo=true&seed=${seed}`
  const response = await fetch(url, {
    headers: { Accept: 'image/*,*/*', 'User-Agent': 'HotWords/1.0' },
    signal: AbortSignal.timeout(90_000),
  })
  return readImageResponse(response)
}

async function fetchSiliconFlow(prompt) {
  const apiKey = siliconFlowKey()
  const base = siliconFlowBase()
  const response = await fetch(`${base}/images/generations`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify({
      model: 'black-forest-labs/FLUX.1-schnell',
      prompt,
      image_size: '1024x1024',
    }),
    signal: AbortSignal.timeout(90_000),
  })
  const text = await response.text()
  if (!response.ok) {
    let message = ''
    try {
      message = JSON.parse(text).message || ''
    } catch {
      message = text.slice(0, 180)
    }
    if (response.status === 401 || response.status === 403) {
      throw fail(502, '硅基流动密钥无效')
    }
    throw fail(502, message || `硅基流动生图失败（${response.status}）`)
  }
  let imageUrl = ''
  try {
    imageUrl = JSON.parse(text)?.images?.[0]?.url || ''
  } catch {
    imageUrl = ''
  }
  if (!imageUrl.startsWith('http')) throw fail(502, '硅基流动没有返回图片')
  const image = await fetch(imageUrl, {
    headers: { Accept: 'image/*,*/*', 'User-Agent': 'HotWords/1.0' },
    signal: AbortSignal.timeout(60_000),
  })
  return readImageResponse(image)
}

async function loadCached(key, provider, sense) {
  const cached = await query(
    'SELECT image FROM mnemonic_images WHERE word_key = $1 AND provider = $2 AND meaning_key = $3',
    [key, provider, sense],
  )
  const bytes = cached.rows[0]?.image
  if (bytes && bytes.length > 0) return Buffer.from(bytes)
  return null
}

async function createMnemonicImage({ word, meaningHint, provider, userId = null }) {
  const key = wordKey(word)
  if (!key) throw fail(400, '请填写单词')
  const sense = meaningKey(meaningHint)
  const cached = await loadCached(key, provider, sense)
  if (cached) {
    return {
      bytes: cached,
      provider,
      cached: true,
      pointsSpent: 0,
      pointsCost: aiImagePointsCost(),
      balance: userId ? await getPointsBalance(userId) : null,
    }
  }

  const cost = aiImagePointsCost()
  let balanceAfter = null
  if (cost > 0) {
    if (!userId) throw fail(401, '请先登录后再生成配图')
    const debited = await adjustPoints({
      userId,
      delta: -cost,
      reason: 'ai_image',
      refType: 'mnemonic',
      refId: `${provider}:${key}`,
    })
    if (!debited.ok) {
      const err = fail(402, debited.error || '积分不足')
      err.code = debited.code || 'INSUFFICIENT_POINTS'
      err.need = debited.need
      err.balance = debited.balance
      err.pointsCost = cost
      throw err
    }
    balanceAfter = debited.balance
  }

  try {
    const scene = provider === 'siliconflow' ? await englishScene(word, meaningHint) : String(meaningHint || '').trim()
    const prompt = provider === 'siliconflow'
      ? mnemonicPrompt(word, scene)
      : `simple cute educational mnemonic illustration for English word "${String(word).trim()}"${scene ? `, specifically meaning: ${scene}` : ''}, clean white background, no text, no letters, no watermark, everyday life scene`
    const bytes = provider === 'siliconflow'
      ? await fetchSiliconFlow(prompt)
      : await fetchPollinations(prompt)
    await query(
      `INSERT INTO mnemonic_images (word_key, provider, meaning_key, image, prompt)
       VALUES ($1, $2, $3, $4, $5)
       ON CONFLICT (word_key, provider, meaning_key) DO NOTHING`,
      [key, provider, sense, bytes, prompt],
    )
    const stored = await loadCached(key, provider, sense)
    return {
      bytes: stored || bytes,
      provider,
      cached: false,
      pointsSpent: cost,
      pointsCost: cost,
      balance: balanceAfter,
    }
  } catch (error) {
    if (cost > 0 && userId) {
      await adjustPoints({
        userId,
        delta: cost,
        reason: 'ai_image_refund',
        refType: 'mnemonic',
        refId: `${provider}:${key}`,
      }).catch(() => {})
    }
    throw error
  }
}

export function getOrCreateMnemonicImage(input) {
  const provider = normalizeImageProvider(input.provider)
  const key = `${provider}:${wordKey(input.word)}:${meaningKey(input.meaningHint)}`
  const pending = inflight.get(key)
  if (pending) return pending
  const job = createMnemonicImage({
    word: input.word,
    meaningHint: input.meaningHint,
    provider,
    userId: input.userId ?? null,
  }).finally(() => {
    inflight.delete(key)
  })
  inflight.set(key, job)
  return job
}
