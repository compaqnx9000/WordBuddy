import type { Accent } from '../types'

let currentAudio: HTMLAudioElement | null = null

function stopAudio() {
  if (currentAudio) {
    currentAudio.pause()
    currentAudio.src = ''
    currentAudio = null
  }
  if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
    window.speechSynthesis.cancel()
  }
}

function speakWithWebSpeech(text: string, accent: Accent) {
  if (!('speechSynthesis' in window)) return
  const utter = new SpeechSynthesisUtterance(text)
  utter.lang = accent === 'UK' ? 'en-GB' : 'en-US'
  utter.rate = 0.92
  const voices = window.speechSynthesis.getVoices()
  const prefer = voices.find((v) =>
    accent === 'UK'
      ? /en-GB|British/i.test(v.lang + v.name)
      : /en-US|American/i.test(v.lang + v.name),
  )
  if (prefer) utter.voice = prefer
  window.speechSynthesis.speak(utter)
}

export async function speakText(text: string, accent: Accent): Promise<void> {
  const word = text.trim()
  if (!word) return
  stopAudio()

  const type = accent === 'UK' ? 1 : 2
  const url = `/api/youdao/dictvoice?audio=${encodeURIComponent(word)}&type=${type}`

  try {
    const audio = new Audio(url)
    currentAudio = audio
    await new Promise<void>((resolve, reject) => {
      audio.onended = () => resolve()
      audio.onerror = () => reject(new Error('audio error'))
      void audio.play().catch(reject)
    })
  } catch {
    speakWithWebSpeech(word, accent)
  }
}

export function stopSpeaking() {
  stopAudio()
}
