import Foundation

enum VocabTransfer {
    static func exportJSON(notebook: Notebook?, entries: [VocabEntry]) throws -> Data {
        var root: [String: Any] = [
            "version": 1,
            "exportedAt": Int(Date().timeIntervalSince1970 * 1000),
        ]
        if let notebook {
            root["notebook"] = [
                "id": notebook.id,
                "name": notebook.name,
            ]
        }
        root["entries"] = entries.map { entry in
            [
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
            ] as [String: Any]
        }
        return try JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted, .sortedKeys])
    }

    static func importEntries(from data: Data) throws -> [VocabEntry] {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError(message: "备份文件格式无效")
        }
        guard JSONValue.int(root, key: "version") == 1 else {
            throw APIError(message: "不支持的备份版本")
        }
        let rows = JSONValue.array(root, key: "entries")
        if rows.isEmpty, root["entries"] == nil {
            throw APIError(message: "备份文件格式无效")
        }
        return rows.compactMap { item in
            guard let object = item as? [String: Any] else { return nil }
            let word = JSONValue.string(object, key: "word") ?? ""
            guard !word.isEmpty else { return nil }
            return VocabEntry(
                text: word,
                isPhrase: JSONValue.bool(object, key: "isPhrase"),
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
