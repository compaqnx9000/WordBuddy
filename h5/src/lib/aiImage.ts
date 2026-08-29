export async function generateAiImageUrl(word: string, meaningHint?: string): Promise<string> {
  const prompt = [
    'simple mnemonic illustration for English learner,',
    `word "${word}"`,
    meaningHint ? `meaning: ${meaningHint}` : '',
    'clean flat illustration, no text',
  ]
    .filter(Boolean)
    .join(', ')
  const seed = Math.floor(Math.random() * 1_000_000)
  const url = `/api/pollinations/prompt/${encodeURIComponent(prompt)}?width=768&height=768&nologo=true&seed=${seed}`
  const res = await fetch(url)
  if (!res.ok) throw new Error('AI 生图失败')
  const blob = await res.blob()
  return await blobToDataUrl(blob)
}

export function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(new Error('读图失败'))
    reader.readAsDataURL(file)
  })
}

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(new Error('读图失败'))
    reader.readAsDataURL(blob)
  })
}
