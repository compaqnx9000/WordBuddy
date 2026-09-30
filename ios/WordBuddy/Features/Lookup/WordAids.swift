import SwiftUI
import UIKit

struct MnemonicImageSection: View {
    @EnvironmentObject private var model: AppModel
    var word: String
    var meaningHint: String
    @State private var image: UIImage?
    @State private var busy = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("AI 配图")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Theme.gold)
                Spacer()
                Text("\(model.aiImagePointsCost) 积分")
                    .font(.caption)
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            Button {
                Task { await generate() }
            } label: {
                if busy {
                    ProgressView().tint(Theme.onPrimary)
                } else {
                    Text(image == nil ? "生成助记配图" : "重新生成")
                }
            }
            .buttonStyle(PrimaryButtonStyle(enabled: !busy))
            .disabled(busy)
        }
        .onAppear { reload() }
        .onChange(of: model.mnemonicRevision) { _, _ in reload() }
        .onChange(of: word) { _, _ in reload() }
        .task { await model.refreshPointCatalog() }
    }

    private func reload() {
        if let data = MnemonicImageStore.load(word: word) {
            image = UIImage(data: data)
        } else {
            image = nil
        }
    }

    private func generate() async {
        busy = true
        defer { busy = false }
        _ = await model.generateMnemonicImage(word: word, meaningHint: meaningHint)
        reload()
    }
}

struct HomophoneSection: View {
    @EnvironmentObject private var model: AppModel
    var word: String
    @State private var tips: [WordHomophone] = []
    @State private var draft = ""
    @State private var busy = false
    @State private var likersTip: WordHomophone?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("谐音助记")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Theme.gold)
            Text("全网共享，按点赞展示前 3 条。")
                .font(.caption)
                .foregroundStyle(Theme.onSurfaceVariant)
            if tips.isEmpty {
                Text("还没有谐音")
                    .font(.subheadline)
                    .foregroundStyle(Theme.onSurfaceVariant)
            } else {
                ForEach(tips) { tip in
                    tipRow(tip)
                }
            }
            HStack(spacing: 8) {
                TextField("例如 about ≈ 额抱他", text: $draft)
                    .textInputAutocapitalization(.never)
                    .foregroundStyle(Theme.onSurface)
                Button(busy ? "…" : "发布") {
                    Task { await submit() }
                }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.onPrimary)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Theme.cyan, in: Capsule())
                .disabled(busy || draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(10)
            .background(Theme.surfaceHigh, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .task(id: word.lowercased()) { await reload() }
        .sheet(item: $likersTip) { tip in
            HomophoneLikersSheet(tip: tip)
        }
    }

    private func tipRow(_ tip: WordHomophone) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top, spacing: 8) {
                Text(tip.body)
                    .foregroundStyle(Theme.onSurface)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button {
                    Task { await toggle(tip) }
                } label: {
                    Label("\(tip.likeCount)", systemImage: tip.likedByMe ? "hand.thumbsup.fill" : "hand.thumbsup")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(tip.likedByMe ? Theme.gold : Theme.onSurfaceVariant)
                }
                .buttonStyle(.plain)
            }
            if tip.isMine, tip.likeCount > 0 {
                Button {
                    likersTip = tip
                } label: {
                    Text(likerSummary(tip))
                        .font(.caption)
                        .foregroundStyle(Theme.gold)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func likerSummary(_ tip: WordHomophone) -> String {
        if let name = tip.likerLabel, tip.likeCount == 1 { return "\(name) 赞了你" }
        if let name = tip.likerLabel { return "\(name) 等 \(tip.likeCount) 人赞了你 · 点查看" }
        return "收到 \(tip.likeCount) 人赞 · 点查看"
    }

    private func reload() async {
        tips = (try? await model.api.fetchHomophones(token: model.session?.token, word: word)) ?? tips
    }

    private func submit() async {
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        guard let token = model.session?.token else {
            model.showLogin = true
            return
        }
        busy = true
        defer { busy = false }
        do {
            _ = try await model.api.submitHomophone(token: token, word: word, body: body)
            draft = ""
            tips = try await model.api.fetchHomophones(token: token, word: word)
        } catch {
            model.banner = error.localizedDescription
        }
    }

    private func toggle(_ tip: WordHomophone) async {
        guard let token = model.session?.token else {
            model.showLogin = true
            return
        }
        guard let updated = try? await model.api.toggleHomophoneLike(token: token, id: tip.id),
              let index = tips.firstIndex(where: { $0.id == tip.id }) else { return }
        tips[index] = updated
    }
}

private struct HomophoneLikersSheet: View {
    @EnvironmentObject private var model: AppModel
    var tip: WordHomophone
    @Environment(\.dismiss) private var dismiss
    @State private var items: [HomophoneLiker] = []
    @State private var nextOffset: Int? = 0
    @State private var loading = false

    var body: some View {
        NavigationStack {
            List {
                ForEach(items) { liker in
                    Text(liker.label)
                        .foregroundStyle(Theme.onSurface)
                        .listRowBackground(Theme.surface)
                }
                if nextOffset != nil {
                    Button(loading ? "加载中…" : "加载更多") {
                        Task { await load() }
                    }
                    .listRowBackground(Theme.surface)
                }
            }
            .scrollContentBackground(.hidden)
            .stellarScreenBackground()
            .navigationTitle("点赞名单")
            .navigationBarTitleDisplayMode(.inline)
            .themeNavigationBar()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        guard let token = model.session?.token, let offset = nextOffset, !loading else { return }
        loading = true
        defer { loading = false }
        guard let page = try? await model.api.fetchHomophoneLikers(token: token, id: tip.id, offset: offset) else {
            return
        }
        items = offset == 0 ? page.items : items + page.items
        nextOffset = page.nextOffset
    }
}
