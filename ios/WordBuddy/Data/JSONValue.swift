import Foundation

enum JSONValue {
    static func object(from data: Data) -> [String: Any] {
        guard !data.isEmpty else { return [:] }
        let parsed = try? JSONSerialization.jsonObject(with: data)
        return parsed as? [String: Any] ?? [:]
    }

    static func childObject(_ parent: [String: Any]?, key: String) -> [String: Any]? {
        guard let parent, let value = parent[key], !(value is NSNull) else { return nil }
        if let object = value as? [String: Any] { return object }
        if let array = value as? [Any], let first = array.first as? [String: Any] { return first }
        return nil
    }

    static func array(_ parent: [String: Any], key: String) -> [Any] {
        parent[key] as? [Any] ?? []
    }

    static func string(_ parent: [String: Any], key: String) -> String? {
        guard let value = parent[key], !(value is NSNull) else { return nil }
        let text: String
        if let string = value as? String {
            text = string
        } else if let number = value as? NSNumber {
            text = number.stringValue
        } else {
            return nil
        }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed == "null" { return nil }
        return trimmed
    }

    static func bool(_ parent: [String: Any], key: String, default defaultValue: Bool = false) -> Bool {
        guard let value = parent[key], !(value is NSNull) else { return defaultValue }
        if let bool = value as? Bool { return bool }
        if let number = value as? NSNumber { return number.boolValue }
        return defaultValue
    }

    static func int(_ parent: [String: Any], key: String, default defaultValue: Int = 0) -> Int {
        guard let value = parent[key], !(value is NSNull) else { return defaultValue }
        if let number = value as? NSNumber { return number.intValue }
        if let string = value as? String, let parsed = Int(string) { return parsed }
        return defaultValue
    }

    static func optionalInt(_ parent: [String: Any], key: String) -> Int? {
        guard let value = parent[key], !(value is NSNull) else { return nil }
        if let number = value as? NSNumber { return number.intValue }
        if let string = value as? String, let parsed = Int(string) { return parsed }
        return nil
    }

    static func int64(_ parent: [String: Any], key: String, default defaultValue: Int64 = 0) -> Int64 {
        guard let value = parent[key], !(value is NSNull) else { return defaultValue }
        if let number = value as? NSNumber { return number.int64Value }
        if let string = value as? String, let parsed = Int64(string) { return parsed }
        return defaultValue
    }
}
