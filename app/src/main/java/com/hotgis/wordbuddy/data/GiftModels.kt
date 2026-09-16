package com.hotgis.wordbuddy.data

data class GiftItem(
    val id: Long,
    val title: String,
    val subtitle: String = "",
    val coverEmoji: String = "🎁",
    val coverColor: String = "#1B6CA8",
    val category: String = "recommend",
    val pointsCost: Int = 0,
    val cashFen: Int = 0,
    val cashYuan: String = "0.00",
    val originalPriceYuan: String? = null,
    val pointsOffsetYuan: String? = null,
    val stock: Int = -1,
    val redeemedCount: Int = 0,
    val needAddress: Boolean = false,
    val description: String = "",
) {
    val priceLabel: String
        get() = if (cashFen > 0) "${pointsCost}积分 + ${cashYuan}元" else "${pointsCost}积分"

    val redeemedLabel: String
        get() = when {
            redeemedCount >= 10_000 -> "已兑${redeemedCount / 10_000}万+"
            redeemedCount >= 1000 -> "已兑${"%.1f".format(redeemedCount / 1000.0)}千"
            else -> "已兑$redeemedCount"
        }
}

data class GiftCategory(
    val id: String,
    val name: String,
)

data class GiftOrder(
    val id: Long,
    val giftId: Long?,
    val giftTitle: String,
    val coverEmoji: String = "🎁",
    val coverColor: String = "#1B6CA8",
    val pointsSpent: Int = 0,
    val cashFen: Int = 0,
    val cashYuan: String = "0.00",
    val status: String = "completed",
    val addressName: String? = null,
    val addressPhone: String? = null,
    val addressDetail: String? = null,
    val remark: String? = null,
    val createdAt: String? = null,
) {
    val statusLabel: String
        get() = when (status) {
            "pending_cash" -> "待付现金"
            "pending_ship" -> "待发货"
            "shipped" -> "已发货"
            "completed" -> "已完成"
            "cancelled" -> "已取消"
            else -> status
        }

    val priceLabel: String
        get() = if (cashFen > 0) "${pointsSpent}积分 + ${cashYuan}元" else "${pointsSpent}积分"
}

data class GiftRedeemResult(
    val message: String,
    val order: GiftOrder,
    val totalPoints: Int,
    val checkIn: CheckInState,
)

data class WithdrawChannel(
    val id: String,
    val name: String,
    val accountLabel: String,
    val accountHint: String = "",
)

data class WithdrawConfig(
    val sandbox: Boolean = true,
    val amountFen: Int = 1,
    val amountYuan: String = "0.01",
    val pointsCost: Int = 1,
    val channels: List<WithdrawChannel> = emptyList(),
    val note: String = "",
)

data class WithdrawalItem(
    val id: Long,
    val channel: String,
    val channelLabel: String = "",
    val account: String = "",
    val amountFen: Int = 0,
    val amountYuan: String = "0.00",
    val pointsSpent: Int = 0,
    val status: String = "pending",
    val statusLabel: String = "",
    val providerTradeNo: String? = null,
    val errorMessage: String? = null,
    val remark: String? = null,
    val sandbox: Boolean = true,
    val createdAt: String? = null,
)

data class WithdrawResult(
    val message: String,
    val item: WithdrawalItem,
    val totalPoints: Int,
    val checkIn: CheckInState,
)
