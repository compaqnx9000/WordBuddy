import SwiftUI

struct PlaceholderView: View {
    var title: String
    var systemImage: String
    var message: String

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            VStack(spacing: 16) {
                Image(systemName: systemImage)
                    .font(.system(size: 42))
                    .foregroundStyle(Theme.cyan)
                Text(title)
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(Theme.onSurface)
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }
        }
    }
}

