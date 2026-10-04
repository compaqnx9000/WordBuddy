import Foundation

enum MnemonicImageStore {
    static func load(word: String) -> Data? {
        let url = fileURL(word: word)
        return try? Data(contentsOf: url)
    }

    static func save(word: String, data: Data) -> Bool {
        let url = fileURL(word: word)
        try? FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        do {
            try data.write(to: url, options: .atomic)
            return true
        } catch {
            return false
        }
    }

    private static func fileURL(word: String) -> URL {
        let trimmed = word.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let safe = String(trimmed.map { character in
            character.isLetter || character.isNumber || character == "-" || character == "_" ? character : "_"
        }.prefix(80))
        let directory = FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("mnemonic-images", isDirectory: true)
        return directory.appendingPathComponent(safe.isEmpty ? "word" : safe).appendingPathExtension("img")
    }
}
