import UIKit

enum AvatarImageCodec {
    static func jpegData(from image: UIImage, maxEdge: CGFloat = 512, quality: CGFloat = 0.88) -> Data? {
        let normalized = image.normalizedOrientation()
        let scaled = normalized.scaled(toMaxEdge: maxEdge)
        return scaled.jpegData(compressionQuality: quality)
    }
}

private extension UIImage {
    func normalizedOrientation() -> UIImage {
        guard imageOrientation != .up else { return self }
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = scale
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        return renderer.image { _ in
            draw(in: CGRect(origin: .zero, size: size))
        }
    }

    func scaled(toMaxEdge maxEdge: CGFloat) -> UIImage {
        let longest = max(size.width, size.height)
        guard longest > maxEdge, longest > 0 else { return self }
        let ratio = maxEdge / longest
        let target = CGSize(
            width: max(1, (size.width * ratio).rounded()),
            height: max(1, (size.height * ratio).rounded())
        )
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: target, format: format)
        return renderer.image { _ in
            draw(in: CGRect(origin: .zero, size: target))
        }
    }
}
