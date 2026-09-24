package com.hotgis.wordbuddy.wxapi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.hotgis.wordbuddy.auth.WeChatAuth
import com.hotgis.wordbuddy.pay.WeChatPayHelper
import com.tencent.mm.opensdk.modelbase.BaseReq
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.modelpay.PayResp
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler

/**
 * WeChat Pay callback Activity (package + class name required by WeChat SDK).
 */
class WXPayEntryActivity : Activity(), IWXAPIEventHandler {
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
        if (resp is PayResp) {
            WeChatPayHelper.deliver(resp)
        }
        finish()
    }
}
