/**
 * SMS gateway. Swap implementations when a vendor is purchased.
 * Keep the sendCode(phone, code) signature stable.
 */
export async function sendCode(phone, code) {
  const provider = process.env.SMS_PROVIDER || 'stub'
  if (provider === 'stub') {
    console.log(`[sms-stub] ${phone} code=${code}`)
    return { provider, delivered: false, stub: true }
  }
  throw new Error(`SMS provider "${provider}" is not wired yet`)
}

export function newLoginCode() {
  if ((process.env.SMS_SKIP_VERIFY || 'true') === 'true') {
    return process.env.DEV_FIXED_CODE || '123456'
  }
  return String(Math.floor(100000 + Math.random() * 900000))
}

export function skipVerify() {
  return (process.env.SMS_SKIP_VERIFY || 'true') === 'true'
}
