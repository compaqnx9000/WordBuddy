import CoreImage.CIFilterBuiltins
import PhotosUI
import SwiftUI
import UIKit

struct ProfileEditView: View {
    var body: some View {
        AccountProfileView()
    }
}

struct AccountProfileView: View {
    @EnvironmentObject private var model: AppModel

    @State private var inviteInfo: InviteInfo?
    @State private var busy = false
    @State private var dialogError: String?

    @State private var showAvatarSource = false
    @State private var showCamera = false
    @State private var showPhotoPicker = false
    @State private var photoItem: PhotosPickerItem?

    @State private var showNickname = false
    @State private var showGender = false
    @State private var showRegion = false
    @State private var showSignature = false
    @State private var showEmail = false
    @State private var showPhone = false
    @State private var showShipping = false
    @State private var showInvite = false
    @State private var showQR = false
    @State private var showUnbindAlipay = false
    @State private var showUnbindWechat = false
    @State private var wechatBinding = false

    private static let regions = [
        "北京", "天津", "上海", "重庆",
        "河北", "山西", "辽宁", "吉林", "黑龙江",
        "江苏", "浙江", "安徽", "福建", "江西", "山东",
        "河南", "湖北", "湖南", "广东", "海南",
        "四川", "贵州", "云南", "陕西", "甘肃", "青海",
        "台湾", "内蒙古", "广西", "西藏", "宁夏", "新疆",
        "香港", "澳门", "海外",
    ]

    private var session: UserSession? { model.session }
    private var buddyId: String { session?.buddyId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "" }

    var body: some View {
        ScrollView {
            VStack(spacing: 10) {
                profileGroup {
                    row("头像", chevron: true) {
                        guard !model.avatarBusy else { return }
                        showAvatarSource = true
                    } trailing: {
                        avatarThumbnail
                    }
                    divider()
                    row("名字", value: displayName) {
                        dialogError = nil
                        showNickname = true
                    }
                    divider()
                    row("性别", chevron: true) {
                        dialogError = nil
                        showGender = true
                    } trailing: {
                        genderLabel
                    }
                    divider()
                    row("地区", value: blank(session?.region, placeholder: "去设置")) {
                        dialogError = nil
                        showRegion = true
                    }
                    divider()
                    row("手机号", value: session?.maskedPhone ?? "") {
                        dialogError = nil
                        showPhone = true
                    }
                    divider()
                    row("邮箱", value: blank(session?.email, placeholder: "去填写")) {
                        dialogError = nil
                        showEmail = true
                    }
                    divider()
                    row("搭子号", value: buddyId.isEmpty ? "分配中…" : buddyId, chevron: false) {
                        model.banner = "搭子号由系统分配，不可修改"
                    }
                    divider()
                    row("邀请好友", value: buddyId.isEmpty ? "分配中…" : "分享链接赚积分") {
                        shareInvite()
                    }
                    divider()
                    row(
                        "填写邀请码",
                        value: inviteTrailing,
                        chevron: inviteInfo?.canBindInvite != false
                    ) {
                        handleInviteTap()
                    }
                    divider()
                    row("我的二维码", chevron: true) {
                        if buddyId.isEmpty {
                            model.banner = "搭子号尚未分配，请稍后重试"
                        } else {
                            showQR = true
                        }
                    } trailing: {
                        Image(systemName: "qrcode")
                            .foregroundStyle(Theme.onSurfaceVariant)
                    }
                    divider()
                    row("签名", value: blank(session?.signature, placeholder: "去填写")) {
                        dialogError = nil
                        showSignature = true
                    }
                }

                profileGroup {
                    row("我的地址", value: session?.shortShippingLabel ?? "去填写") {
                        dialogError = nil
                        showShipping = true
                    }
                }

                // 上架前关闭微信、支付宝绑定。恢复时取消下面这一段注释。
                // profileGroup {
                //     alipayRow
                //     divider()
                //     wechatRow
                // }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .padding(.bottom, 28)
        }
        .stellarScreenBackground()
        .navigationTitle("个人资料")
        .navigationBarTitleDisplayMode(.inline)
        .themeNavigationBar()
        .task {
            await model.loadAvatar()
            if let token = session?.token {
                inviteInfo = try? await model.api.fetchInviteInfo(token: token)
            }
        }
        .photosPicker(isPresented: $showPhotoPicker, selection: $photoItem, matching: .images, photoLibrary: .shared())
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task { await handlePickedPhoto(item) }
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraImagePicker { image in
                showCamera = false
                guard let image else { return }
                Task { await model.uploadAvatar(image: image) }
            }
            .ignoresSafeArea()
        }
        .overlay {
            if showAvatarSource {
                AvatarSourceDialog(
                    busy: model.avatarBusy,
                    onCancel: { showAvatarSource = false },
                    onCamera: {
                        showAvatarSource = false
                        openCamera()
                    },
                    onGallery: {
                        showAvatarSource = false
                        photoItem = nil
                        showPhotoPicker = true
                    }
                )
            }
            if showNickname {
                ProfileFormDialog(
                    title: "修改昵称",
                    subtitle: "最多 24 个字，会显示在个人资料和「我的」页顶部。",
                    initial: displayName == "词搭子" ? (session?.nickname ?? "") : displayName,
                    busy: busy,
                    error: dialogError,
                    onCancel: { showNickname = false },
                    onConfirm: { value in Task { await saveNickname(value) } },
                    field: { binding in
                        ProfileDialogField(text: binding, placeholder: "输入昵称", limit: 24)
                    }
                )
            }
            if showGender {
                GenderDialog(
                    selected: session?.gender,
                    busy: busy,
                    error: dialogError,
                    onCancel: { showGender = false },
                    onPick: { value in Task { await saveGender(value) } }
                )
            }
            if showRegion {
                RegionDialog(
                    initial: session?.region ?? "",
                    regions: Self.regions,
                    busy: busy,
                    error: dialogError,
                    onCancel: { showRegion = false },
                    onConfirm: { value in Task { await saveRegion(value) } }
                )
            }
            if showSignature {
                ProfileFormDialog(
                    title: "修改签名",
                    subtitle: "最多 40 个字，展示在个人资料页。",
                    initial: session?.signature ?? "",
                    busy: busy,
                    error: dialogError,
                    onCancel: { showSignature = false },
                    onConfirm: { value in Task { await saveSignature(value) } },
                    field: { binding in
                        ProfileDialogField(text: binding, placeholder: "介绍一下自己", limit: 40, multiLine: true)
                    }
                )
            }
            if showPhone {
                ChangePhoneDialog(
                    currentPhoneMasked: session?.maskedPhone ?? "",
                    onDismiss: { showPhone = false }
                )
            }
            if showEmail {
                ProfileFormDialog(
                    title: "修改邮箱",
                    subtitle: "用于账号联系与找回，仅自己和管理员可见。",
                    initial: session?.email ?? "",
                    busy: busy,
                    error: dialogError,
                    onCancel: { showEmail = false },
                    onConfirm: { value in Task { await saveEmail(value) } },
                    field: { binding in
                        ProfileDialogField(text: binding, placeholder: "name@example.com", limit: 80)
                    }
                )
            }
            if showShipping {
                ShippingDialog(
                    name: session?.shippingName ?? "",
                    phone: session?.shippingPhone ?? "",
                    detail: session?.shippingDetail ?? "",
                    busy: busy,
                    error: dialogError,
                    onCancel: { showShipping = false },
                    onConfirm: { name, phone, detail in
                        Task { await saveShipping(name: name, phone: phone, detail: detail) }
                    }
                )
            }
            if showInvite {
                ProfileFormDialog(
                    title: "填写邀请码",
                    subtitle: "注册时漏填可在此补填一次。填写成功后你将获得 \(inviteInfo?.inviteeReward ?? 10) 积分，且之后不可更改。",
                    busy: busy,
                    error: dialogError,
                    confirmTitle: "确认填写",
                    onCancel: { showInvite = false },
                    onConfirm: { value in Task { await bindInvite(value) } },
                    field: { binding in
                        ProfileDialogField(text: binding, placeholder: "好友的搭子号", limit: 16)
                    }
                )
            }
            if showQR {
                BuddyQRDialog(buddyId: buddyId) { showQR = false }
            }
        }
        .alert("解绑支付宝", isPresented: $showUnbindAlipay) {
            Button("解绑", role: .destructive) {
                Task { await unbindAlipay() }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("解绑后不能用该账号提现。需要时可以重新绑定。")
        }
        .alert("解绑微信", isPresented: $showUnbindWechat) {
            Button("解绑", role: .destructive) {
                Task { await unbindWechat() }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("解绑后不能用该微信提现，也不能用此微信直接登录当前账号。需要时可重新绑定。")
        }
    }

    private var displayName: String {
        let nick = session?.nickname?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return nick.isEmpty ? (session?.displayNickname ?? "词搭子") : nick
    }

    private var avatarThumbnail: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(Theme.surfaceHigh)
            if let image = model.avatarImage {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                Image(systemName: "person.crop.rectangle")
                    .font(.title3)
                    .foregroundStyle(Theme.cyan)
            }
            if model.avatarBusy {
                Color.black.opacity(0.35)
                ProgressView()
                    .tint(.white)
            }
        }
        .frame(width: 46, height: 46)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private func openCamera() {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            model.banner = "当前设备不支持拍照，请从相册选择"
            return
        }
        showCamera = true
    }

    private func handlePickedPhoto(_ item: PhotosPickerItem) async {
        defer { photoItem = nil }
        do {
            guard let data = try await item.loadTransferable(type: Data.self),
                  let image = UIImage(data: data) else {
                model.banner = "无法读取图片"
                return
            }
            await model.uploadAvatar(image: image)
        } catch {
            model.banner = error.localizedDescription
        }
    }

    private var inviteTrailing: String {
        if let invited = inviteInfo?.invitedByBuddyId, !invited.isEmpty, inviteInfo?.canBindInvite == false {
            return "已绑定 \(invited)"
        }
        if inviteInfo?.canBindInvite == false { return "已填写" }
        return "注册漏填可补一次"
    }

    @ViewBuilder
    private var genderLabel: some View {
        let value = session?.gender?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if value.isEmpty {
            Text("去设置").foregroundStyle(Theme.onSurfaceVariant.opacity(0.92))
        } else {
            HStack(spacing: 6) {
                Text(genderSymbol(value))
                    .foregroundStyle(genderColor(value))
                    .fontWeight(.bold)
                Text(value).foregroundStyle(Theme.onSurfaceVariant.opacity(0.92))
            }
        }
    }

    private var alipayRow: some View {
        let bound = !(session?.alipayAccount?.isEmpty ?? true)
        return HStack {
            Text("支付宝账号").foregroundStyle(Theme.onSurfaceVariant)
            Spacer()
            if bound {
                Text("已绑定").foregroundStyle(Theme.onSurfaceVariant.opacity(0.92))
                Button("解绑") { showUnbindAlipay = true }
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Theme.pink)
                    .buttonStyle(.plain)
                    .padding(.leading, 10)
            } else {
                Button {
                    model.banner = "支付宝绑定即将支持"
                } label: {
                    HStack(spacing: 6) {
                        Text("去绑定").foregroundStyle(Theme.onSurfaceVariant.opacity(0.92))
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Theme.onSurfaceVariant.opacity(0.55))
                    }
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }

    private var wechatRow: some View {
        let bound = !(session?.wechatAccount?.isEmpty ?? true)
        return HStack {
            Text("微信账号").foregroundStyle(Theme.onSurfaceVariant)
            Spacer()
            if wechatBinding {
                Text("正在打开微信…").foregroundStyle(Theme.onSurfaceVariant.opacity(0.92))
            } else if bound {
                Text("已绑定").foregroundStyle(Theme.onSurfaceVariant.opacity(0.92))
                Button("解绑") { showUnbindWechat = true }
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Theme.pink)
                    .buttonStyle(.plain)
                    .padding(.leading, 10)
            } else {
                Button {
                    Task { await bindWechatAccount() }
                } label: {
                    HStack(spacing: 6) {
                        Text("去绑定").foregroundStyle(Theme.onSurfaceVariant.opacity(0.92))
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Theme.onSurfaceVariant.opacity(0.55))
                    }
                }
                .buttonStyle(.plain)
                .disabled(busy)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }

    private func profileGroup<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(spacing: 0) { content() }
            .glassPanel()
    }

    private func divider() -> some View {
        Divider().overlay(Theme.outline.opacity(0.35)).padding(.leading, 16)
    }

    private func row(
        _ title: String,
        value: String? = nil,
        chevron: Bool = true,
        action: @escaping () -> Void,
        @ViewBuilder trailing: () -> some View = { EmptyView() }
    ) -> some View {
        Button(action: action) {
            rowLabel(title, value: value, chevron: chevron, trailing: trailing)
        }
        .buttonStyle(.plain)
    }

    private func rowLabel(
        _ title: String,
        value: String?,
        chevron: Bool,
        @ViewBuilder trailing: () -> some View = { EmptyView() }
    ) -> some View {
        HStack(spacing: 12) {
            Text(title).foregroundStyle(Theme.onSurfaceVariant)
            Spacer(minLength: 8)
            trailing()
            if let value {
                Text(value)
                    .foregroundStyle(Theme.onSurfaceVariant.opacity(0.92))
                    .lineLimit(1)
            }
            if chevron {
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Theme.onSurfaceVariant.opacity(0.55))
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .contentShape(Rectangle())
    }

    private func blank(_ value: String?, placeholder: String) -> String {
        let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? placeholder : trimmed
    }

    private func genderSymbol(_ value: String) -> String {
        switch value {
        case "男": "♂"
        case "女": "♀"
        default: "○"
        }
    }

    private func genderColor(_ value: String) -> Color {
        switch value {
        case "男": Color(hex: 0x3B82F6)
        case "女": Color(hex: 0xEC4899)
        default: Color(hex: 0x9CA3AF)
        }
    }

    private func shareInvite() {
        guard !buddyId.isEmpty else {
            model.banner = "搭子号尚未分配，请稍后重试"
            return
        }
        let text = "我在用「词搭子」背单词，邀请你一起来！\n邀请码：\(buddyId)\n打开链接注册：\(WordBuddyAPI.primaryBase)/i/\(buddyId)"
        let activity = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let root = scene.keyWindow?.rootViewController {
            root.present(activity, animated: true)
        }
    }

    private func handleInviteTap() {
        if inviteInfo?.canBindInvite == false {
            if let id = inviteInfo?.invitedByBuddyId, !id.isEmpty {
                model.banner = "已绑定邀请人 \(id)"
            } else {
                model.banner = "已填写过邀请码"
            }
            return
        }
        dialogError = nil
        showInvite = true
    }

    private func saveNickname(_ value: String) async {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            dialogError = "请输入昵称"
            return
        }
        busy = true
        defer { busy = false }
        if await model.patchProfile(nickname: trimmed) {
            showNickname = false
        } else {
            dialogError = model.banner
        }
    }

    private func saveGender(_ value: String) async {
        busy = true
        defer { busy = false }
        if await model.patchProfile(gender: value) {
            showGender = false
        } else {
            dialogError = model.banner
        }
    }

    private func saveRegion(_ value: String) async {
        busy = true
        defer { busy = false }
        if await model.patchProfile(region: value) {
            showRegion = false
        } else {
            dialogError = model.banner
        }
    }

    private func saveSignature(_ value: String) async {
        busy = true
        defer { busy = false }
        if await model.patchProfile(signature: value) {
            showSignature = false
        } else {
            dialogError = model.banner
        }
    }

    private func saveEmail(_ value: String) async {
        busy = true
        defer { busy = false }
        if await model.patchProfile(email: value) {
            showEmail = false
        } else {
            dialogError = model.banner
        }
    }

    private func saveShipping(name: String, phone: String, detail: String) async {
        busy = true
        defer { busy = false }
        if await model.patchProfile(
            shippingName: name,
            shippingPhone: phone,
            shippingDetail: detail,
            successMessage: "收货地址已保存"
        ) {
            showShipping = false
        } else {
            dialogError = model.banner
        }
    }

    private func bindInvite(_ code: String) async {
        guard let token = session?.token else { return }
        let cleaned = code.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard cleaned.count >= 4 else {
            dialogError = "请输入邀请码"
            return
        }
        busy = true
        defer { busy = false }
        do {
            let result = try await model.api.bindInviteCode(token: token, inviteCode: cleaned)
            model.applyPoints(result.totalPoints)
            model.banner = result.message
            inviteInfo = try await model.api.fetchInviteInfo(token: token)
            showInvite = false
        } catch {
            dialogError = error.localizedDescription
        }
    }

    private func unbindAlipay() async {
        _ = await model.patchProfile(alipayAccount: "", alipayName: "", successMessage: "已解绑支付宝")
    }

    private func bindWechatAccount() async {
        guard !wechatBinding else { return }
        wechatBinding = true
        defer { wechatBinding = false }
        await model.bindWechat()
    }

    private func unbindWechat() async {
        _ = await model.patchProfile(wechatAccount: "", successMessage: "已解绑微信")
    }
}

// MARK: - Dialogs

struct LogoutConfirmDialog: View {
    var title: String = "退出登录"
    var message: String = "退出后将清除本机登录状态与词库缓存，可继续以游客身份使用。再次使用需重新登录。"
    var confirmTitle: String = "退出"
    var onCancel: () -> Void
    var onConfirm: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea().onTapGesture(perform: onCancel)
            VStack(alignment: .leading, spacing: 14) {
                Text(title)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Theme.onSurface)
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
                HStack {
                    Spacer()
                    Button("取消", action: onCancel)
                        .foregroundStyle(Theme.onSurfaceVariant)
                    Button(confirmTitle, action: onConfirm)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(Theme.onPrimary)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 8)
                        .background(Theme.pink, in: Capsule())
                }
                .padding(.top, 4)
            }
            .padding(20)
            .background(Theme.surfaceContainer.opacity(0.98), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .stroke(Theme.cyan.opacity(0.28), lineWidth: 1)
            )
            .padding(.horizontal, 28)
        }
    }
}

private struct AvatarSourceDialog: View {
    var busy: Bool
    var onCancel: () -> Void
    var onCamera: () -> Void
    var onGallery: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea().onTapGesture { if !busy { onCancel() } }
            VStack(alignment: .leading, spacing: 12) {
                Text("更换头像")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Theme.cyanSoft)
                Text("自拍一张，或从相册选择图片，保存后会同步到服务器。")
                    .font(.footnote)
                    .foregroundStyle(Theme.onSurfaceVariant)
                avatarSourceRow(icon: "camera", title: "拍照", action: onCamera)
                avatarSourceRow(icon: "photo.on.rectangle", title: "从相册选择", action: onGallery)
                HStack {
                    Spacer()
                    Button("取消", action: onCancel)
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .disabled(busy)
                }
                .padding(.top, 4)
            }
            .padding(20)
            .background(Theme.surfaceContainer.opacity(0.98), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Theme.cyan.opacity(0.4), lineWidth: 1)
            )
            .padding(.horizontal, 28)
            .shadow(color: Theme.cyan.opacity(0.25), radius: 20)
        }
    }

    private func avatarSourceRow(icon: String, title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Theme.cyan)
                    .frame(width: 22)
                Text(title)
                    .font(.body.weight(.medium))
                    .foregroundStyle(Theme.onSurface)
                Spacer()
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 14)
            .background(Theme.surfaceHigh.opacity(0.7), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(busy)
    }
}

private struct CameraImagePicker: UIViewControllerRepresentable {
    var onFinish: (UIImage?) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.allowsEditing = true
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onFinish: onFinish)
    }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onFinish: (UIImage?) -> Void

        init(onFinish: @escaping (UIImage?) -> Void) {
            self.onFinish = onFinish
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onFinish(nil)
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            let image = (info[.editedImage] ?? info[.originalImage]) as? UIImage
            onFinish(image)
        }
    }
}

private struct ChangePhoneDialog: View {
    @EnvironmentObject private var model: AppModel

    var currentPhoneMasked: String
    var onDismiss: () -> Void

    @State private var step = 1
    @State private var password = ""
    @State private var newPhone = ""
    @State private var code = ""
    @State private var passwordVisible = false
    @State private var busy = false
    @State private var sendingCode = false
    @State private var countdown = 0
    @State private var errorText: String?

    private var blocking: Bool { busy || sendingCode }

    var body: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea().onTapGesture {
                if !blocking { onDismiss() }
            }
            VStack(alignment: .leading, spacing: 12) {
                Text("修改手机号")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Theme.cyanSoft)
                Text(step == 1
                     ? "当前号码 \(currentPhoneMasked)\n请先输入登录密码以确认身份"
                     : "密码已验证。请输入新手机号并完成短信验证")
                    .font(.footnote)
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
                if step == 1 {
                    passwordField
                } else {
                    phoneField
                    HStack(spacing: 8) {
                        codeField
                        Button(sendTitle) { Task { await sendCode() } }
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(canSend ? Theme.cyanSoft : Theme.onSurfaceVariant)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 12)
                            .background(Theme.surfaceHigh, in: Capsule())
                            .overlay(Capsule().stroke(Theme.cyan.opacity(0.35), lineWidth: 1))
                            .disabled(!canSend)
                    }
                }
                if let errorText, !errorText.isEmpty {
                    Text(errorText).font(.footnote).foregroundStyle(Theme.pink)
                }
                HStack {
                    Spacer()
                    Button("取消", action: onDismiss)
                        .foregroundStyle(Theme.cyanSoft)
                        .disabled(blocking)
                    Button(busy ? "…" : (step == 1 ? "下一步" : "完成修改")) {
                        Task { await advance() }
                    }
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Theme.onPrimary)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Theme.cyan, in: Capsule())
                    .disabled(blocking)
                }
            }
            .padding(20)
            .background(Theme.surfaceContainer.opacity(0.98), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Theme.cyan.opacity(0.4), lineWidth: 1)
            )
            .padding(.horizontal, 28)
            .shadow(color: Theme.cyan.opacity(0.25), radius: 20)
        }
        .task(id: countdown) {
            guard countdown > 0 else { return }
            try? await Task.sleep(for: .seconds(1))
            if countdown > 0 { countdown -= 1 }
        }
    }

    private var canSend: Bool {
        countdown <= 0 && !sendingCode && !busy && newPhone.count == 11
    }

    private var sendTitle: String {
        if countdown > 0 { return "\(countdown)s" }
        if sendingCode { return "发送中" }
        return "获取验证码"
    }

    private var passwordField: some View {
        HStack(spacing: 8) {
            Group {
                if passwordVisible {
                    TextField("当前登录密码", text: $password)
                } else {
                    SecureField("当前登录密码", text: $password)
                }
            }
            .textContentType(.password)
            .foregroundStyle(Theme.onSurface)
            .onChange(of: password) { _, _ in errorText = nil }
            Button {
                passwordVisible.toggle()
            } label: {
                Image(systemName: passwordVisible ? "eye.slash" : "eye")
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Theme.surfaceHigh.opacity(0.75), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Theme.cyan.opacity(0.35), lineWidth: 1)
        )
    }

    private var phoneField: some View {
        TextField("新手机号", text: $newPhone)
            .keyboardType(.phonePad)
            .foregroundStyle(Theme.onSurface)
            .padding(12)
            .background(Theme.surfaceHigh.opacity(0.75), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(Theme.cyan.opacity(0.35), lineWidth: 1)
            )
            .onChange(of: newPhone) { _, value in
                newPhone = String(value.filter(\.isNumber).prefix(11))
                errorText = nil
            }
    }

    private var codeField: some View {
        TextField("短信验证码", text: $code)
            .keyboardType(.numberPad)
            .foregroundStyle(Theme.onSurface)
            .padding(12)
            .background(Theme.surfaceHigh.opacity(0.75), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(Theme.cyan.opacity(0.35), lineWidth: 1)
            )
            .onChange(of: code) { _, value in
                code = String(value.filter(\.isNumber).prefix(6))
                errorText = nil
            }
    }

    private func advance() async {
        if step == 1 {
            busy = true
            errorText = nil
            defer { busy = false }
            do {
                try await model.verifyLoginPassword(password)
                step = 2
            } catch {
                errorText = error.localizedDescription
            }
        } else {
            busy = true
            errorText = nil
            defer { busy = false }
            do {
                try await model.changePhone(password: password, newPhone: newPhone, code: code)
                onDismiss()
            } catch {
                errorText = error.localizedDescription
            }
        }
    }

    private func sendCode() async {
        guard canSend else { return }
        sendingCode = true
        errorText = nil
        defer { sendingCode = false }
        do {
            try await model.sendChangePhoneCode(newPhone: newPhone)
            countdown = 60
        } catch {
            errorText = error.localizedDescription
        }
    }
}

private struct ProfileFormDialog<Field: View>: View {
    var title: String
    var subtitle: String
    var initial: String = ""
    var busy: Bool
    var error: String?
    var confirmTitle: String = "保存"
    var onCancel: () -> Void
    var onConfirm: (String) -> Void
    @ViewBuilder var field: (Binding<String>) -> Field

    @State private var text = ""

    var body: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea().onTapGesture { if !busy { onCancel() } }
            VStack(alignment: .leading, spacing: 12) {
                Text(title)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Theme.cyanSoft)
                Text(subtitle)
                    .font(.footnote)
                    .foregroundStyle(Theme.onSurfaceVariant)
                field($text)
                if let error, !error.isEmpty {
                    Text(error).font(.footnote).foregroundStyle(Theme.pink)
                }
                HStack {
                    Spacer()
                    Button("取消", action: onCancel)
                        .foregroundStyle(Theme.cyanSoft)
                        .disabled(busy)
                    Button(busy ? "…" : confirmTitle) { onConfirm(text) }
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(Theme.onPrimary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Theme.cyan, in: Capsule())
                        .disabled(busy)
                }
            }
            .padding(20)
            .background(Theme.surfaceContainer.opacity(0.98), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Theme.cyan.opacity(0.4), lineWidth: 1)
            )
            .padding(.horizontal, 28)
            .shadow(color: Theme.cyan.opacity(0.25), radius: 20)
            .onAppear { if text.isEmpty { text = initial } }
        }
    }
}

private struct ProfileDialogField: View {
    @Binding var text: String
    var placeholder: String
    var limit: Int
    var multiLine: Bool = false

    var body: some View {
        Group {
            if multiLine {
                TextField(placeholder, text: limited, axis: .vertical)
                    .lineLimit(3...5)
            } else {
                TextField(placeholder, text: limited)
            }
        }
        .foregroundStyle(Theme.onSurface)
        .padding(12)
        .background(Theme.surfaceHigh.opacity(0.75), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Theme.cyan.opacity(0.35), lineWidth: 1)
        )
    }

    private var limited: Binding<String> {
        Binding(
            get: { text },
            set: { text = String($0.prefix(limit)) }
        )
    }
}

private struct GenderDialog: View {
    var selected: String?
    var busy: Bool
    var error: String?
    var onCancel: () -> Void
    var onPick: (String) -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea().onTapGesture { if !busy { onCancel() } }
            VStack(alignment: .leading, spacing: 12) {
                Text("选择性别")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Theme.cyanSoft)
                Text("仅自己可见，可随时修改。")
                    .font(.footnote)
                    .foregroundStyle(Theme.onSurfaceVariant)
                VStack(spacing: 8) {
                    option("男", "♂", Color(hex: 0x3B82F6))
                    option("女", "♀", Color(hex: 0xEC4899))
                    option("未知", "○", Color(hex: 0x9CA3AF))
                }
                if let error, !error.isEmpty {
                    Text(error).font(.footnote).foregroundStyle(Theme.pink)
                }
            }
            .padding(20)
            .background(Theme.surfaceContainer.opacity(0.98), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Theme.cyan.opacity(0.4), lineWidth: 1)
            )
            .padding(.horizontal, 28)
        }
    }

    private func option(_ value: String, _ symbol: String, _ color: Color) -> some View {
        let active = selected == value
        return Button {
            onPick(value)
        } label: {
            HStack(spacing: 10) {
                Text(symbol).foregroundStyle(color).fontWeight(.bold)
                Text(value).foregroundStyle(Theme.onSurface)
                Spacer()
                if active {
                    Image(systemName: "checkmark").foregroundStyle(Theme.cyan)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(
                (active ? Theme.cyan.opacity(0.18) : Theme.surfaceHigh.opacity(0.75)),
                in: RoundedRectangle(cornerRadius: 14, style: .continuous)
            )
        }
        .buttonStyle(.plain)
        .disabled(busy)
    }
}

private struct RegionDialog: View {
    var initial: String
    var regions: [String]
    var busy: Bool
    var error: String?
    var onCancel: () -> Void
    var onConfirm: (String) -> Void
    @State private var custom = ""

    var body: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea().onTapGesture { if !busy { onCancel() } }
            VStack(alignment: .leading, spacing: 12) {
                Text("选择地区")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Theme.cyanSoft)
                Text("点选常用地区，或自行填写（最多 40 字）。")
                    .font(.footnote)
                    .foregroundStyle(Theme.onSurfaceVariant)
                ProfileDialogField(text: $custom, placeholder: "例如：北京", limit: 40)
                ScrollView {
                    VStack(spacing: 6) {
                        ForEach(regions, id: \.self) { option in
                            let active = custom == option
                            Button { custom = option } label: {
                                Text(option)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .foregroundStyle(active ? Theme.cyan : Theme.onSurface)
                                    .fontWeight(active ? .semibold : .regular)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 10)
                                    .background(
                                        (active ? Theme.cyan.opacity(0.16) : Theme.surfaceHigh.opacity(0.55)),
                                        in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .frame(maxHeight: 220)
                if let error, !error.isEmpty {
                    Text(error).font(.footnote).foregroundStyle(Theme.pink)
                }
                HStack {
                    Spacer()
                    Button("取消", action: onCancel).foregroundStyle(Theme.cyanSoft)
                    Button(busy ? "…" : "保存") { onConfirm(custom) }
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(Theme.onPrimary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Theme.cyan, in: Capsule())
                        .disabled(busy)
                }
            }
            .padding(20)
            .background(Theme.surfaceContainer.opacity(0.98), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Theme.cyan.opacity(0.4), lineWidth: 1)
            )
            .padding(.horizontal, 28)
            .onAppear { custom = initial }
        }
    }
}

private struct ShippingDialog: View {
    @State private var name: String
    @State private var phone: String
    @State private var detail: String
    var busy: Bool
    var error: String?
    var onCancel: () -> Void
    var onConfirm: (String, String, String) -> Void

    init(
        name: String,
        phone: String,
        detail: String,
        busy: Bool,
        error: String?,
        onCancel: @escaping () -> Void,
        onConfirm: @escaping (String, String, String) -> Void
    ) {
        _name = State(initialValue: name)
        _phone = State(initialValue: phone)
        _detail = State(initialValue: detail)
        self.busy = busy
        self.error = error
        self.onCancel = onCancel
        self.onConfirm = onConfirm
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea().onTapGesture { if !busy { onCancel() } }
            VStack(alignment: .leading, spacing: 12) {
                Text("收货地址")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Theme.cyanSoft)
                Text("用于积分兑礼发货，请填写真实可联系的信息。")
                    .font(.footnote)
                    .foregroundStyle(Theme.onSurfaceVariant)
                labeled("收件人") {
                    ProfileDialogField(text: $name, placeholder: "请填写收件人姓名", limit: 40)
                }
                labeled("电话") {
                    ProfileDialogField(text: $phone, placeholder: "请填写联系电话", limit: 20)
                }
                labeled("地址") {
                    ProfileDialogField(text: $detail, placeholder: "省市区 + 详细地址", limit: 200, multiLine: true)
                }
                if let error, !error.isEmpty {
                    Text(error).font(.footnote).foregroundStyle(Theme.pink)
                }
                HStack {
                    Spacer()
                    Button("取消", action: onCancel).foregroundStyle(Theme.cyanSoft)
                    Button(busy ? "…" : "保存") {
                        onConfirm(name.trimmingCharacters(in: .whitespacesAndNewlines),
                                  phone.trimmingCharacters(in: .whitespacesAndNewlines),
                                  detail.trimmingCharacters(in: .whitespacesAndNewlines))
                    }
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Theme.onPrimary)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Theme.cyan, in: Capsule())
                    .disabled(busy)
                }
            }
            .padding(20)
            .background(Theme.surfaceContainer.opacity(0.98), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Theme.cyan.opacity(0.4), lineWidth: 1)
            )
            .padding(.horizontal, 28)
        }
    }

    private func labeled<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.subheadline.weight(.medium)).foregroundStyle(Theme.onSurface)
            content()
        }
    }
}

private enum BuddyQr {
    /// Same payload on Android: wordbuddy://buddy/{搭子号}
    static func payload(_ buddyId: String) -> String? {
        guard let id = normalize(buddyId) else { return nil }
        return "wordbuddy://buddy/\(id)"
    }

    static func buddyId(from payload: String) -> String? {
        let text = payload.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let prefix = "wordbuddy://buddy/"
        guard text.hasPrefix(prefix) else { return nil }
        let id = text.dropFirst(prefix.count).prefix { $0 != "?" && $0 != "#" }
        return normalize(String(id))
    }

    static func normalize(_ buddyId: String) -> String? {
        let id = buddyId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard id.count >= 4, id.count <= 32, id.allSatisfy({ $0.isLetter || $0.isNumber }) else { return nil }
        return id
    }
}

private struct BuddyQRDialog: View {
    var buddyId: String
    var onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea().onTapGesture(perform: onDismiss)
            VStack(spacing: 10) {
                Text("我的二维码")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Theme.cyanSoft)
                Text("搭子号  \(BuddyQr.normalize(buddyId) ?? buddyId)")
                    .font(.subheadline)
                    .foregroundStyle(Theme.onSurfaceVariant)
                Text("与搭子号一一对应，之后可扫码加搭子")
                    .font(.caption)
                    .foregroundStyle(Theme.onSurfaceVariant.opacity(0.75))
                    .multilineTextAlignment(.center)
                if let payload = BuddyQr.payload(buddyId), let image = Self.makeQR(payload: payload) {
                    Image(uiImage: image)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .padding(12)
                        .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .frame(width: 220, height: 220)
                } else {
                    Text("二维码生成失败").foregroundStyle(Theme.onSurfaceVariant)
                }
                Button("关闭", action: onDismiss)
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .padding(.top, 8)
            }
            .padding(22)
            .background(Theme.surfaceContainer.opacity(0.98), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Theme.cyan.opacity(0.45), lineWidth: 1)
            )
            .padding(.horizontal, 36)
        }
    }

    private static func makeQR(payload: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(payload.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }
        let scaled = output.transformed(by: CGAffineTransform(scaleX: 12, y: 12))
        let context = CIContext()
        guard let cg = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}

private extension UIWindowScene {
    var keyWindow: UIWindow? { windows.first { $0.isKeyWindow } ?? windows.first }
}
