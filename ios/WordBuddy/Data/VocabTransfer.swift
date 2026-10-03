import Foundation

struct VocabBackupNotebook {
    var name: String
    var entries: [VocabEntry]
}

enum VocabTransfer {
    static func exportLibrary(_ books: [VocabBackupNotebook]) throws -> Data {
        let root: [String: Any] = [
            "version": 2,
            "exportedAt": Int(Date().timeIntervalSince1970 * 1000),
            "notebooks": books.map { book in
                [
                    "name": book.name,
                    "entries": book.entries.map(entryObject),
                ] as [String: Any]
            },
        ]
        return try JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted, .sortedKeys])
    }

    /// Version 2 restores every notebook. Version 1 is a single notebook from older backups.
    static func importLibrary(from data: Data) throws -> [VocabBackupNotebook] {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError(message: "备份文件格式无效")
        }
        let version = JSONValue.int(root, key: "version")
        if version == 2 {
            let rows = JSONValue.array(root, key: "notebooks")
            if rows.isEmpty {
                throw APIError(message: "备份文件格式无效")
            }
            return rows.compactMap { item in
                guard let object = item as? [String: Any] else { return nil }
                let name = (JSONValue.string(object, key: "name") ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                guard !name.isEmpty else { return nil }
                return VocabBackupNotebook(name: name, entries: parseEntries(object["entries"]))
            }
        }
        if version == 1 {
            let notebook = root["notebook"] as? [String: Any]
            let name = (notebook.flatMap { JSONValue.string($0, key: "name") } ?? "导入的生词本")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            return [VocabBackupNotebook(name: name.isEmpty ? "导入的生词本" : name, entries: parseEntries(root["entries"]))]
        }
        throw APIError(message: "不支持的备份版本")
    }

    private static func entryObject(_ entry: VocabEntry) -> [String: Any] {
        var object: [String: Any] = [
            "word": entry.text,
            "isPhrase": entry.isPhrase,
            "definitions": entry.definitions.map {
                ["pos": $0.pos, "meaning": $0.meaning, "user": $0.isUserAdded] as [String: Any]
            },
            "examples": entry.examples.map {
                ["en": $0.english, "zh": $0.chinese]
            },
            "nearWords": entry.nearWords,
            "synonyms": entry.synonyms,
            "antonyms": entry.antonyms,
            "sortOrder": entry.sortOrder,
            "addedAt": entry.addedAtMillis,
        ]
        if let ipaUk = entry.ipaUk, !ipaUk.isEmpty { object["ipaUk"] = ipaUk }
        if let ipaUs = entry.ipaUs, !ipaUs.isEmpty { object["ipaUs"] = ipaUs }
        return object
    }

    private static func parseEntries(_ raw: Any?) -> [VocabEntry] {
        (raw as? [Any] ?? []).compactMap { item in
            guard let object = item as? [String: Any] else { return nil }
            let word = JSONValue.string(object, key: "word") ?? ""
            guard !word.isEmpty else { return nil }
            return VocabEntry(
                text: word,
                isPhrase: JSONValue.bool(object, key: "isPhrase"),
                ipaUk: JSONValue.string(object, key: "ipaUk"),
                ipaUs: JSONValue.string(object, key: "ipaUs"),
                definitions: parseDefinitions(object["definitions"]),
                examples: parseExamples(object["examples"]),
                nearWords: parseStrings(object["nearWords"]),
                synonyms: parseStrings(object["synonyms"]),
                antonyms: parseStrings(object["antonyms"]),
                sortOrder: JSONValue.int(object, key: "sortOrder"),
                addedAtMillis: JSONValue.int64(object, key: "addedAt")
            )
        }
    }

    private static func parseDefinitions(_ raw: Any?) -> [Definition] {
        (raw as? [Any] ?? []).compactMap { item in
            guard let object = item as? [String: Any] else { return nil }
            let meaning = JSONValue.string(object, key: "meaning") ?? ""
            guard !meaning.isEmpty else { return nil }
            return Definition(
                pos: JSONValue.string(object, key: "pos") ?? "",
                meaning: meaning,
                isUserAdded: JSONValue.bool(object, key: "user")
            )
        }
    }

    private static func parseExamples(_ raw: Any?) -> [ExampleSentence] {
        (raw as? [Any] ?? []).compactMap { item in
            guard let object = item as? [String: Any] else { return nil }
            let english = JSONValue.string(object, key: "en") ?? JSONValue.string(object, key: "english") ?? ""
            let chinese = JSONValue.string(object, key: "zh") ?? JSONValue.string(object, key: "chinese") ?? ""
            guard !english.isEmpty else { return nil }
            return ExampleSentence(english: english, chinese: chinese)
        }
    }

    private static func parseStrings(_ raw: Any?) -> [String] {
        (raw as? [Any] ?? []).compactMap { item in
            guard let text = item as? String else { return nil }
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
    }
}
