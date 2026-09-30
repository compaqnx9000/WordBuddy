import AVFoundation

enum Speech {
    private static let synthesizer = AVSpeechSynthesizer()

    static func speak(_ text: String, accent: Accent, slow: Bool = false) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        synthesizer.stopSpeaking(at: .immediate)
        let utterance = AVSpeechUtterance(string: trimmed)
        utterance.voice = AVSpeechSynthesisVoice(language: accent.speechLanguage)
        utterance.rate = slow ? AVSpeechUtteranceMinimumSpeechRate : AVSpeechUtteranceDefaultSpeechRate
        synthesizer.speak(utterance)
    }
}
