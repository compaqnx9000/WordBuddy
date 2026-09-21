package com.hotgis.wordbuddy.wxapi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.hotgis.wordbuddy.auth.WeChatAuth
import com.tencent.mm.opensdk.modelbase.BaseReq
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.modelmsg.SendAuth
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler

class WXEntryActivity : Activity(), IWXAPIEventHandler {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WeChatAuth.api(this).handleIntent(intent, this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        WeChatAuth.api(this).handleIntent(intent, this)
    }

    override fun onReq(req: BaseReq?) {
        finish()
    }

    override fun onResp(resp: BaseResp?) {
        val auth = resp as? SendAuth.Resp
        if (auth != null && auth.state == WeChatAuth.STATE) {
            when (auth.errCode) {
                BaseResp.ErrCode.ERR_OK -> WeChatAuth.deliver(auth.code, null)
                BaseResp.ErrCode.ERR_USER_CANCEL -> WeChatAuth.deliver(null, "已取消微信登录")
                else -> WeChatAuth.deliver(null, auth.errStr?.takeIf { it.isNotBlank() } ?: "微信登录失败")
            }
        }
        finish()
    }
}
