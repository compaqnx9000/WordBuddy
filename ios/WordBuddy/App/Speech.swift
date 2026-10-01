import AVFoundation

enum Speech {
    private static let synthesizer = AVSpeechSynthesizer()
    private static let finisher = SpeechFinisher()

    static func speak(_ text: String, accent: Accent, slow: Bool = false) {
        guard let utterance = makeUtterance(text, accent: accent, slow: slow) else { return }
        synthesizer.delegate = finisher
        finisher.cancelWaiting()
        synthesizer.stopSpeaking(at: .immediate)
        synthesizer.speak(utterance)
    }

    /// Speaks and returns after this utterance finishes or is cancelled.
    static func speakAndWait(_ text: String, accent: Accent, slow: Bool = false) async {
        guard let utterance = makeUtterance(text, accent: accent, slow: slow) else { return }
        await withCheckedContinuation { continuation in
            synthesizer.delegate = finisher
            synthesizer.stopSpeaking(at: .immediate)
            finisher.beginWait(utterance, continuation: continuation)
            synthesizer.speak(utterance)
        }
    }

    /// Syllables first, then the whole word once. A single part is spoken only once.
    static func speakPhonics(parts: [String], word: String, accent: Accent) async {
        let cleaned = parts
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        guard !cleaned.isEmpty else { return }
        if cleaned.count > 1 {
            for (index, part) in cleaned.enumerated() {
                if Task.isCancelled { return }
                await speakAndWait(part, accent: accent)
                if Task.isCancelled { return }
                if index < cleaned.count - 1 {
                    try? await Task.sleep(nanoseconds: 320_000_000)
                }
            }
            if Task.isCancelled { return }
            try? await Task.sleep(nanoseconds: 280_000_000)
            if Task.isCancelled { return }
        }
        let whole = word.trimmingCharacters(in: .whitespacesAndNewlines)
        await speakAndWait(whole.isEmpty ? cleaned.joined() : whole, accent: accent)
    }

    private static func makeUtterance(_ text: String, accent: Accent, slow: Bool) -> AVSpeechUtterance? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let utterance = AVSpeechUtterance(string: trimmed)
        utterance.voice = AVSpeechSynthesisVoice(language: accent.speechLanguage)
        utterance.rate = slow ? AVSpeechUtteranceMinimumSpeechRate : AVSpeechUtteranceDefaultSpeechRate
        return utterance
    }
}

private final class SpeechFinisher: NSObject, AVSpeechSynthesizerDelegate {
    private var waitingId: ObjectIdentifier?
    private var continuation: CheckedContinuation<Void, Never>?

    func cancelWaiting() {
        continuation?.resume()
        continuation = nil
        waitingId = nil
    }

    func beginWait(_ utterance: AVSpeechUtterance, continuation: CheckedContinuation<Void, Never>) {
        cancelWaiting()
        waitingId = ObjectIdentifier(utterance)
        self.continuation = continuation
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        complete(utterance)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        complete(utterance)
    }

    private func complete(_ utterance: AVSpeechUtterance) {
        guard waitingId == ObjectIdentifier(utterance) else { return }
        continuation?.resume()
        continuation = nil
        waitingId = nil
    }
}
