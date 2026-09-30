import Foundation

actor DictionaryClient {
    private var cache: [String: VocabEntry] = [:]
    private let cacheLimit = 50
    private let session: URLSession

    init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 12
        configuration.timeoutIntervalForResource = 15
        session = URLSession(configuration: configuration)
    }

    func lookup(_ query: String) async throws -> VocabEntry {
        let key = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !key.isEmpty else { throw APIError(message: "请输入要查的词") }
        if let cached = cache[key] { return cached }
        let entry = try await fetch(query.trimmingCharacters(in: .whitespacesAndNewlines))
        cache[key] = entry
        if cache.count > cacheLimit, let first = cache.keys.first {
            cache.removeValue(forKey: first)
        }
        return entry
    }

    private func fetch(_ query: String) async throws -> VocabEntry {
        var youdaoRoot: [String: Any]?
        var fromYoudao: VocabEntry?
        if let raw = try? await httpGet(youdaoURL(query)),
           let root = try? JSONSerialization.jsonObject(with: raw) as? [String: Any] {
            youdaoRoot = root
            fromYoudao = parseYoudao(root, fallback: query)
        }

        let base: VocabEntry
        if let fromYoudao, !fromYoudao.definitions.isEmpty {
            base = fromYoudao
        } else if let raw = try? await httpGet(baiduURL(query)),
                  let parsed = parseBaidu(raw, query: query) {
            base = VocabEntry(
                text: parsed.text,
                isPhrase: parsed.isPhrase,
                ipaUk: fromYoudao?.ipaUk,
                ipaUs: fromYoudao?.ipaUs,
                definitions: parsed.definitions
            )
        } else if let fromYoudao {
            base = fromYoudao
        } else {
            throw APIError(message: "没有查到这个词")
        }

        let examples = collectExamples(youdaoRoot).prefix(2).map { $0 }
        async let synonyms = loadCommonSynonyms(root: youdaoRoot, word: base.text)
        async let antonyms = loadCommonAntonyms(word: base.text)
        async let nearWords = findNearWords(base.text)
        return VocabEntry(
            text: base.text,
            isPhrase: base.isPhrase,
            ipaUk: base.ipaUk,
            ipaUs: base.ipaUs,
            definitions: base.definitions,
            examples: examples,
            nearWords: await nearWords,
            synonyms: await synonyms,
            antonyms: await antonyms
        )
    }

    private func parseYoudao(_ root: [String: Any], fallback: String) -> VocabEntry? {
        let word = JSONValue.childObject(JSONValue.childObject(root, key: "ec"), key: "word")
            ?? JSONValue.childObject(JSONValue.childObject(root, key: "simple"), key: "word")
        guard let word else { return nil }
        let text = parseReturnPhrase(word) ?? fallback
        let uk = JSONValue.string(word, key: "ukphone")
        let us = JSONValue.string(word, key: "usphone")
        let rawDefs = parseTrs(word["trs"])
        let defs = enrichMissingPos(rawDefs, root: root, isPhrase: text.contains(" "))
        if defs.isEmpty && uk == nil && us == nil { return nil }
        return VocabEntry(
            text: text,
            isPhrase: text.contains(" "),
            ipaUk: uk,
            ipaUs: us,
            definitions: defs.isEmpty ? [Definition(pos: "", meaning: text)] : defs
        )
    }

    private func enrichMissingPos(_ defs: [Definition], root: [String: Any], isPhrase: Bool) -> [Definition] {
        guard !defs.isEmpty, defs.contains(where: { $0.pos.isEmpty }) else { return defs }
        let eePos = parseEePosList(root)
        guard !eePos.isEmpty else { return defs }
        var unique: [String] = []
        for pos in eePos {
            let key = pos.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "."))
            if !unique.contains(where: { $0.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: ".")) == key }) {
                unique.append(pos)
            }
        }
        let fallbackPos: String
        if unique.count == 1 {
            fallbackPos = unique[0]
        } else if isPhrase, let verb = unique.first(where: { $0.lowercased().hasPrefix("v") }) {
            fallbackPos = verb
        } else {
            fallbackPos = unique[0]
        }
        return defs.enumerated().map { index, def in
            if !def.pos.isEmpty { return def }
            let pos = defs.count == eePos.count ? eePos[index] : fallbackPos
            return Definition(pos: pos, meaning: def.meaning, isUserAdded: def.isUserAdded)
        }
    }

    private func parseEePosList(_ root: [String: Any]) -> [String] {
        guard let word = JSONValue.childObject(JSONValue.childObject(root, key: "ee"), key: "word") else {
            return []
        }
        return (word["trs"] as? [Any] ?? []).compactMap { item in
            guard let object = item as? [String: Any] else { return nil }
            return JSONValue.string(object, key: "pos")
        }
    }

    private func parseReturnPhrase(_ word: [String: Any]) -> String? {
        guard let value = word["return-phrase"], !(value is NSNull) else { return nil }
        if let text = value as? String {
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
        if let object = value as? [String: Any] {
            if let nested = object["l"] as? [String: Any] {
                return extractPlainText(nested["i"])
            }
            return extractPlainText(object["l"])
        }
        return nil
    }

    private func parseTrs(_ raw: Any?) -> [Definition] {
        guard let items = raw as? [Any] else { return [] }
        var definitions: [Definition] = []
        for item in items {
            guard let object = item as? [String: Any] else { continue }
            if let tran = JSONValue.string(object, key: "tran") {
                definitions.append(Definition(pos: JSONValue.string(object, key: "pos") ?? "", meaning: tran))
                continue
            }
            let trArray = object["tr"] as? [Any] ?? []
            for trItem in trArray {
                guard let tr = trItem as? [String: Any] else { continue }
                let payload = (tr["l"] as? [String: Any])?["i"]
                for line in extractPlainTextList(payload) {
                    definitions.append(splitPosMeaning(line))
                }
            }
        }
        return definitions
    }

    private func parseBaidu(_ data: Data, query: String) -> VocabEntry? {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        let rows = root["data"] as? [Any] ?? []
        var meaning: String?
        for row in rows {
            guard let item = row as? [String: Any] else { continue }
            let key = JSONValue.string(item, key: "k") ?? ""
            if key.caseInsensitiveCompare(query) == .orderedSame {
                meaning = JSONValue.string(item, key: "v")
                break
            }
        }
        if meaning == nil, let first = rows.first as? [String: Any] {
            meaning = JSONValue.string(first, key: "v")
        }
        guard let meaning, !meaning.isEmpty else { return nil }
        return VocabEntry(
            text: query,
            isPhrase: query.contains(" "),
            definitions: [Definition(pos: "", meaning: meaning)]
        )
    }

    private func collectExamples(_ root: [String: Any]?) -> [ExampleSentence] {
        guard let root else { return [] }
        var candidates: [ExampleSentence] = []
        let pairs = ((root["blng_sents_part"] as? [String: Any])?["sentence-pair"]) as? [Any]
        if let pairs {
            for item in pairs {
                guard let object = item as? [String: Any] else { continue }
                let english = cleanExample(
                    JSONValue.string(object, key: "sentence") ?? JSONValue.string(object, key: "sentence-eng") ?? ""
                )
                let chinese = cleanExample(JSONValue.string(object, key: "sentence-translation") ?? "")
                if !english.isEmpty, !chinese.isEmpty, !looksLikeProperName(english, chinese) {
                    candidates.append(ExampleSentence(english: english, chinese: chinese))
                }
            }
        }
        var seen = Set<String>()
        let unique = candidates.filter { sentence in
            let key = sentence.english.lowercased()
            if seen.contains(key) { return false }
            seen.insert(key)
            return true
        }
        return unique.sorted { lhs, rhs in
            everydayScore(lhs) == everydayScore(rhs)
                ? lhs.english.count < rhs.english.count
                : everydayScore(lhs) < everydayScore(rhs)
        }
    }

    private func looksLikeProperName(_ english: String, _ chinese: String) -> Bool {
        if chinese.contains("人名") { return true }
        return english.range(of: #"^[A-Z][a-z]+ [A-Z][a-z]+,"#, options: .regularExpression) != nil
    }

    private func everydayScore(_ example: ExampleSentence) -> Int {
        let english = example.english
        var score = 0
        if english.count > 90 { score += 3 }
        if english.count > 60 { score += 1 }
        if english.contains(","), english.count > 50 { score += 1 }
        let formal = ["whereas", "hereby", "thereof", "aforesaid", "pursuant"]
        if formal.contains(where: { english.localizedCaseInsensitiveContains($0) }) { score += 4 }
        return score
    }

    private func cleanExample(_ raw: String) -> String {
        var text = raw
        text = text.replacingOccurrences(of: "</b>", with: "", options: .caseInsensitive)
        text = text.replacingOccurrences(of: "<b>", with: "", options: .caseInsensitive)
        text = text.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        text = text.replacingOccurrences(of: "&nbsp;", with: " ")
        text = text.replacingOccurrences(of: "&amp;", with: "&")
        text = text.replacingOccurrences(of: "&lt;", with: "<")
        text = text.replacingOccurrences(of: "&gt;", with: ">")
        text = text.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func splitPosMeaning(_ raw: String) -> Definition {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let match = trimmed.range(of: #"^([A-Za-z]+\.)\s*(.+)$"#, options: .regularExpression) else {
            return Definition(pos: "", meaning: trimmed)
        }
        let line = String(trimmed[match])
        let parts = line.split(separator: " ", maxSplits: 1, omittingEmptySubsequences: true)
        if parts.count == 2, parts[0].hasSuffix(".") {
            return Definition(pos: String(parts[0]), meaning: String(parts[1]))
        }
        return Definition(pos: "", meaning: trimmed)
    }

    private func extractPlainText(_ value: Any?) -> String? {
        switch value {
        case let text as String:
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        case let array as [Any]:
            let parts = array.compactMap { item -> String? in
                guard let text = item as? String else { return nil }
                let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                return trimmed.isEmpty ? nil : trimmed
            }
            let joined = parts.joined(separator: " ")
            return joined.isEmpty ? nil : joined
        default:
            return nil
        }
    }

    private func extractPlainTextList(_ value: Any?) -> [String] {
        switch value {
        case let text as String:
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? [] : [trimmed]
        case let array as [Any]:
            return array.compactMap { item in
                guard let text = item as? String else { return nil }
                let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                return trimmed.isEmpty ? nil : trimmed
            }
        default:
            return []
        }
    }

    private func youdaoURL(_ query: String) -> URL? {
        var components = URLComponents(string: "https://dict.youdao.com/jsonapi")
        components?.queryItems = [
            URLQueryItem(name: "q", value: query),
            URLQueryItem(name: "doctype", value: "json"),
        ]
        return components?.url
    }

    private func baiduURL(_ query: String) -> URL? {
        var components = URLComponents(string: "https://fanyi.baidu.com/sug")
        components?.queryItems = [URLQueryItem(name: "kw", value: query)]
        return components?.url
    }

    private func httpGet(_ url: URL?) async throws -> Data {
        guard let url else { throw APIError(message: "词典地址无效") }
        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) HotWords/1.0", forHTTPHeaderField: "User-Agent")
        request.setValue("application/json,text/plain,*/*", forHTTPHeaderField: "Accept")
        let (data, response) = try await session.data(for: request)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(code) else {
            throw APIError(message: "词典请求失败", httpCode: code)
        }
        return data
    }

    private func loadCommonSynonyms(root: [String: Any]?, word: String) async -> [String] {
        let fromDatamuse = await datamuseRelatedScored(rel: "rel_syn", word: word)
        let scored = fromDatamuse.isEmpty ? await datamuseTagged(word: word, tag: "syn") : fromDatamuse
        let primary = scored.sorted { $0.freq > $1.freq }.map(\.word)
        let fromYoudao = parseYoudaoSynonyms(root).map { ScoredWord(word: $0, freq: 1) }
        return mergeCommonWords(primary: primary, scored: fromYoudao, exclude: word)
    }

    private func loadCommonAntonyms(word: String) async -> [String] {
        async let fromAnt = datamuseRelatedScored(rel: "rel_ant", word: word)
        async let fromMl = datamuseTagged(word: word, tag: "ant")
        let primary = await fromAnt.sorted { $0.freq > $1.freq }.map(\.word)
        return mergeCommonWords(primary: primary, scored: await fromMl, exclude: word)
    }

    private func mergeCommonWords(primary: [String], scored: [ScoredWord], exclude: String) -> [String] {
        var out: [String] = []
        func add(_ word: String) {
            let key = word.lowercased()
            guard isSingleCommonLemma(key), key.caseInsensitiveCompare(exclude) != .orderedSame else { return }
            if !out.contains(key) { out.append(key) }
        }
        primary.forEach(add)
        for item in scored.sorted(by: { $0.freq > $1.freq }) {
            if out.count >= 5 { break }
            if item.freq < 0.3 && out.count >= 2 { break }
            add(item.word)
        }
        return Array(out.prefix(5))
    }

    private func isSingleCommonLemma(_ word: String) -> Bool {
        let text = word.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard text.count >= 2, text.count <= 14, !text.contains(" ") else { return false }
        return text.allSatisfy { $0.isLetter || $0 == "-" }
    }

    private func parseYoudaoSynonyms(_ root: [String: Any]?) -> [String] {
        guard let synos = (root?["syno"] as? [String: Any])?["synos"] as? [Any] else { return [] }
        var words: [String] = []
        for item in synos {
            guard let group = item as? [String: Any],
                  let syno = group["syno"] as? [String: Any],
                  let list = syno["ws"] as? [Any] else { continue }
            for row in list {
                guard let object = row as? [String: Any],
                      let word = JSONValue.string(object, key: "w"),
                      isSingleCommonLemma(word) else { continue }
                let key = word.lowercased()
                if !words.contains(key) { words.append(key) }
            }
        }
        return words
    }

    private func datamuseRelatedScored(rel: String, word: String) async -> [ScoredWord] {
        let query = word.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard query.count >= 2, !query.contains(" ") else { return [] }
        guard let data = try? await httpGet(datamuseURL([
            URLQueryItem(name: rel, value: query),
            URLQueryItem(name: "md", value: "f"),
            URLQueryItem(name: "max", value: "20"),
        ])) else { return [] }
        return parseDatamuseScored(data)
    }

    private func datamuseTagged(word: String, tag: String) async -> [ScoredWord] {
        let query = word.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard query.count >= 2, !query.contains(" ") else { return [] }
        guard let data = try? await httpGet(datamuseURL([
            URLQueryItem(name: "ml", value: query),
            URLQueryItem(name: "md", value: "f"),
            URLQueryItem(name: "max", value: "25"),
        ])),
              let array = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
        return array.compactMap { object in
            let tags = object["tags"] as? [String] ?? []
            guard tags.contains(tag) else { return nil }
            let freq = tags.first { $0.hasPrefix("f:") }.flatMap { Double($0.dropFirst(2)) } ?? 0
            guard let word = JSONValue.string(object, key: "word"), isSingleCommonLemma(word) else { return nil }
            return ScoredWord(word: word.lowercased(), freq: freq)
        }
    }

    private func parseDatamuseScored(_ data: Data) -> [ScoredWord] {
        guard let array = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
        return array.compactMap { object in
            guard let word = JSONValue.string(object, key: "word"), isSingleCommonLemma(word) else { return nil }
            let tags = object["tags"] as? [String] ?? []
            let freq = tags.first { $0.hasPrefix("f:") }.flatMap { Double($0.dropFirst(2)) } ?? 0
            return ScoredWord(word: word.lowercased(), freq: freq)
        }
    }

    private func findNearWords(_ word: String) async -> [String] {
        let query = word.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard query.count >= 2, query.allSatisfy(\.isLetter) else { return [] }
        var patterns: [String] = []
        for index in query.indices {
            var shorter = query
            shorter.remove(at: index)
            if shorter.count >= 2 { patterns.append(shorter) }
        }
        for index in 0...query.count {
            let split = query.index(query.startIndex, offsetBy: index)
            patterns.append(query[..<split] + "?" + query[split...])
        }
        for index in query.indices {
            let next = query.index(after: index)
            patterns.append(query[..<index] + "?" + query[next...])
        }
        return await withTaskGroup(of: [ScoredWord].self) { group in
            for pattern in patterns {
                group.addTask { [self] in
                    await scoredPattern(pattern)
                }
            }
            var rows: [ScoredWord] = []
            for await batch in group {
                rows.append(contentsOf: batch)
            }
            let grouped = Dictionary(grouping: rows.filter { item in
                item.word != query &&
                    item.word.allSatisfy(\.isLetter) &&
                    editDistanceOne(query, item.word) &&
                    !isInflection(of: query, other: item.word) &&
                    (item.freq >= 0.05 || item.word.count <= query.count + 1)
            }, by: \.word)
            return grouped
                .map { word, items in ScoredWord(word: word, freq: items.map(\.freq).max() ?? 0) }
                .sorted { lhs, rhs in
                    if lhs.freq != rhs.freq { return lhs.freq > rhs.freq }
                    let leftGap = abs(lhs.word.count - query.count)
                    let rightGap = abs(rhs.word.count - query.count)
                    if leftGap != rightGap { return leftGap < rightGap }
                    if lhs.word.count != rhs.word.count { return lhs.word.count < rhs.word.count }
                    return lhs.word < rhs.word
                }
                .prefix(5)
                .map(\.word)
        }
    }

    private func scoredPattern(_ pattern: String) async -> [ScoredWord] {
        let exact = !pattern.contains("?")
        guard let data = try? await httpGet(datamuseURL([
            URLQueryItem(name: "sp", value: pattern),
            URLQueryItem(name: "md", value: "f"),
            URLQueryItem(name: "max", value: exact ? "6" : "12"),
        ])) else { return [] }
        let scored = parseDatamuseScored(data)
        if exact {
            return scored.filter { $0.word.caseInsensitiveCompare(pattern) == .orderedSame }
        }
        return scored
    }

    private func editDistanceOne(_ a: String, _ b: String) -> Bool {
        if abs(a.count - b.count) > 1 { return false }
        if a.count == b.count {
            return zip(a, b).filter { $0 != $1 }.count == 1
        }
        let shorter = a.count < b.count ? a : b
        let longer = a.count < b.count ? b : a
        var si = shorter.startIndex
        var li = longer.startIndex
        var skipped = 0
        while si < shorter.endIndex && li < longer.endIndex {
            if shorter[si] == longer[li] {
                shorter.formIndex(after: &si)
                longer.formIndex(after: &li)
            } else {
                skipped += 1
                if skipped > 1 { return false }
                longer.formIndex(after: &li)
            }
        }
        skipped += longer.distance(from: li, to: longer.endIndex)
        return skipped == 1 && si == shorter.endIndex
    }

    private func isInflection(of base: String, other: String) -> Bool {
        var forms = [base + "s", base + "es", base + "ed", base + "ing"]
        if base.hasSuffix("e") {
            forms.append(base + "d")
            forms.append(String(base.dropLast()) + "ing")
        }
        if base.hasSuffix("y"), base.count > 1 {
            let before = base[base.index(base.endIndex, offsetBy: -2)]
            if !"aeiou".contains(before) {
                forms.append(String(base.dropLast()) + "ies")
                forms.append(String(base.dropLast()) + "ied")
            }
        }
        return forms.contains(other)
    }

    private func datamuseURL(_ items: [URLQueryItem]) -> URL? {
        var components = URLComponents(string: "https://api.datamuse.com/words")
        components?.queryItems = items
        return components?.url
    }
}

private struct ScoredWord: Sendable {
    var word: String
    var freq: Double
}
