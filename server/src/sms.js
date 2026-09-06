/**
 * SMS gateway.
 * - stub: log code to console (local/dev)
 * - aliyun: Alibaba Cloud Dysmsapi SendSms
 */
import Dysmsapi20170525, { SendSmsRequest } from '@alicloud/dysmsapi20170525'
import OpenApi from '@alicloud/openapi-client'

// CJS/ESM interop: default may be { default: Client } or Client itself.
const DysmsClient = Dysmsapi20170525?.default || Dysmsapi20170525

let aliyunClient = null

function createAliyunClient() {
  const accessKeyId =
    process.env.ALIYUN_ACCESS_KEY_ID ||
    process.env.ALIBABA_CLOUD_ACCESS_KEY_ID
  const accessKeySecret =
    process.env.ALIYUN_ACCESS_KEY_SECRET ||
    process.env.ALIBABA_CLOUD_ACCESS_KEY_SECRET
  if (!accessKeyId || !accessKeySecret) {
    throw new Error('缺少阿里云 AccessKey：请配置 ALIYUN_ACCESS_KEY_ID / ALIYUN_ACCESS_KEY_SECRET')
  }
  const config = new OpenApi.Config({
    accessKeyId,
    accessKeySecret,
    endpoint: process.env.ALIYUN_SMS_ENDPOINT || 'dysmsapi.aliyuncs.com',
  })
  return new DysmsClient(config)
}

function getAliyunClient() {
  if (!aliyunClient) aliyunClient = createAliyunClient()
  return aliyunClient
}

async function sendAliyunSms(phone, code) {
  const signName = process.env.ALIYUN_SMS_SIGN_NAME
  const templateCode = process.env.ALIYUN_SMS_TEMPLATE_CODE
  const paramKey = process.env.ALIYUN_SMS_TEMPLATE_PARAM_KEY || 'code'
  if (!signName || !templateCode) {
    throw new Error('缺少短信签名或模板：请配置 ALIYUN_SMS_SIGN_NAME / ALIYUN_SMS_TEMPLATE_CODE')
  }

  const request = new SendSmsRequest({
    phoneNumbers: phone,
    signName,
    templateCode,
    templateParam: JSON.stringify({ [paramKey]: code }),
  })
  const response = await getAliyunClient().sendSms(request)
  const body = response?.body || {}
  if (body.code !== 'OK') {
    const detail = body.message || body.code || '短信发送失败'
    console.error('[sms-aliyun] send failed', {
      phone: phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2'),
      code: body.code,
      message: body.message,
      requestId: body.requestId,
    })
    throw new Error(detail)
  }
  console.log('[sms-aliyun] delivered', {
    phone: phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2'),
    requestId: body.requestId,
  })
  return {
    provider: 'aliyun',
    delivered: true,
    requestId: body.requestId,
  }
}

export async function sendCode(phone, code) {
  const provider = (process.env.SMS_PROVIDER || 'stub').toLowerCase()
  if (provider === 'stub') {
    console.log(`[sms-stub] ${phone} code=${code}`)
    return { provider, delivered: false, stub: true }
  }
  if (provider === 'aliyun') {
    return sendAliyunSms(phone, code)
  }
  throw new Error(`SMS provider "${provider}" is not wired yet`)
}

export function newLoginCode() {
  if (skipVerify()) {
    return process.env.DEV_FIXED_CODE || '123456'
  }
  return String(Math.floor(100000 + Math.random() * 900000))
}

export function skipVerify() {
  return (process.env.SMS_SKIP_VERIFY || 'true') === 'true'
}
