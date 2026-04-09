import SwiftUI
import KMPFaceLink

struct VoiceTrackingView: View {
    @StateObject private var viewModel = VoiceTrackingViewModel()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                // Provider selection
                Text("TTS Provider").font(.caption.weight(.bold))
                HStack(spacing: 8) {
                    ForEach(["Voicevox", "OpenAI", "ElevenLabs"], id: \.self) { provider in
                        Button {
                            viewModel.selectedProvider = provider
                        } label: {
                            Text(provider)
                                .font(.caption2)
                                .foregroundStyle(.white)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(
                                    viewModel.selectedProvider == provider
                                        ? Color.blue.opacity(0.6) : Color(.tertiarySystemFill),
                                    in: Capsule()
                                )
                        }
                        .frame(minHeight: 44)
                        .accessibilityLabel("\(provider) provider")
                        .accessibilityHint(viewModel.selectedProvider == provider
                            ? "Currently selected"
                            : "Double tap to select \(provider)")
                    }
                }

                // Provider config
                switch viewModel.selectedProvider {
                case "Voicevox":
                    TextField("Voicevox host address", text: $viewModel.voicevoxHost)
                        .textFieldStyle(.roundedBorder).font(.caption)
                        .accessibilityLabel("Voicevox host address")
                case "OpenAI":
                    TextField("OpenAI API key", text: $viewModel.apiKey)
                        .textFieldStyle(.roundedBorder).font(.caption)
                        .accessibilityLabel("OpenAI API key")
                    TextField("OpenAI voice name", text: $viewModel.voiceId)
                        .textFieldStyle(.roundedBorder).font(.caption)
                        .accessibilityLabel("OpenAI voice name")
                case "ElevenLabs":
                    TextField("ElevenLabs API key", text: $viewModel.apiKey)
                        .textFieldStyle(.roundedBorder).font(.caption)
                        .accessibilityLabel("ElevenLabs API key")
                    TextField("ElevenLabs voice ID", text: $viewModel.voiceId)
                        .textFieldStyle(.roundedBorder).font(.caption)
                        .accessibilityLabel("ElevenLabs voice ID")
                default: EmptyView()
                }

                // Speak controls
                Text("Speak").font(.caption.weight(.bold))
                TextField("Text to speak", text: $viewModel.speakText, axis: .vertical)
                    .textFieldStyle(.roundedBorder).font(.caption)
                    .lineLimit(3)
                    .accessibilityLabel("Text to speak")

                HStack(spacing: 8) {
                    Button("Speak") { viewModel.speak() }
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(.blue, in: Capsule())
                        .disabled(viewModel.isLoading)
                        .accessibilityLabel("Speak")
                        .accessibilityHint("Synthesize and play the entered text")

                    Button(viewModel.isListening ? "Stop Mic" : "Listen") {
                        viewModel.toggleListening()
                    }
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(viewModel.isListening ? .red : .blue, in: Capsule())
                    .disabled(viewModel.isLoading)
                    .accessibilityLabel(viewModel.isListening ? "Stop microphone" : "Start listening")
                    .accessibilityHint(viewModel.isListening
                        ? "Stop recording from the microphone"
                        : "Start recording from the microphone")

                    if viewModel.isLoading {
                        ProgressView()
                            .progressViewStyle(.circular)
                            .tint(.primary)
                            .scaleEffect(0.8)
                    }
                }

                // Pipeline state
                Text(viewModel.pipelineStateText)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(viewModel.pipelineStateColor)

                // Transcription
                Text("Transcription").font(.caption.weight(.bold))
                if viewModel.transcription.isEmpty {
                    Text("No transcription yet")
                        .font(.caption2).foregroundStyle(.secondary)
                } else {
                    Text(viewModel.transcription)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(3)
                        .truncationMode(.tail)
                }

                // Lip sync bars
                Text("Lip Sync").font(.caption.weight(.bold))
                if viewModel.lipSyncShapes.isEmpty {
                    Text("No lip sync data")
                        .font(.caption2).foregroundStyle(.secondary)
                } else {
                    ForEach(
                        Array(viewModel.lipSyncShapes.sorted { $0.value > $1.value }.prefix(8)),
                        id: \.key
                    ) { key, value in
                        HStack {
                            Text(key)
                                .font(.system(.caption2, design: .monospaced))
                                .foregroundStyle(.secondary)
                                .frame(minWidth: 100, alignment: .leading)
                                .dynamicTypeSize(...DynamicTypeSize.accessibility1)
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    Rectangle().fill(Color(.separator))
                                    Rectangle()
                                        .fill(.green)
                                        .frame(width: max(0, geo.size.width * CGFloat(min(1, max(0, value)))))
                                }
                            }
                            .frame(height: 6)
                        }
                        .accessibilityLabel("\(key): \(Int(value * 100)) percent")
                    }
                }
            }
            .padding()
        }
        .background(Color(.systemBackground))
    }
}

// MARK: - ViewModel

@MainActor
class VoiceTrackingViewModel: ObservableObject {
    @Published var selectedProvider = "Voicevox"
    @Published var apiKey = ""
    @Published var voiceId = "alloy"
    @Published var voicevoxHost = "localhost"
    @Published var speakText = "Hello, I am FaceLink!"
    @Published var pipelineStateText = "Idle"
    @Published var pipelineStateColor: Color = .gray
    @Published var isListening = false
    @Published var isLoading = false
    @Published var transcription = ""
    @Published var lipSyncShapes: [String: Float] = [:]

    private var pipeline: VoicePipeline?
    private var observeTasks: [Task<Void, Never>] = []

    func speak() {
        let config = buildTtsConfig()
        pipeline?.release()
        let pipelineConfig = VoicePipelineConfig(
            asrConfig: nil,
            ttsConfig: config,
            lipSyncConfig: LipSyncConfig(),
            autoPlayAudio: true
        )
        let newPipeline = VoicePipeline(
            config: pipelineConfig,
            audioRecorder: AudioFactoryKt.createAudioRecorder(),
            audioPlayer: AudioFactoryKt.createAudioPlayer(),
            ttsEngine: nil,
            asrEngine: nil,
            lipSyncEngine: nil
        )
        pipeline = newPipeline
        observePipeline(newPipeline)

        isLoading = true
        Task {
            defer { isLoading = false }
            let flow = try await newPipeline.speak(text: speakText)
            for await _ in flow { }
        }
    }

    func toggleListening() {
        guard let pipeline = pipeline else { return }
        if isListening {
            isLoading = true
            Task {
                defer { isLoading = false }
                let result = try? await pipeline.stopListening()
                transcription = result?.text ?? "(no result)"
                isListening = false
            }
        } else {
            isLoading = true
            Task {
                defer { isLoading = false }
                try? await pipeline.startListening()
                isListening = true
            }
        }
    }

    private func observePipeline(_ pipeline: VoicePipeline) {
        observeTasks.forEach { $0.cancel() }
        observeTasks = []

        let stateTask = Task { [weak self] in
            for await state in pipeline.state {
                guard let self, !Task.isCancelled else { break }
                switch state {
                case is VoicePipelineState.Idle:
                    self.pipelineStateText = "Idle"
                    self.pipelineStateColor = .gray
                case is VoicePipelineState.Processing:
                    self.pipelineStateText = "Processing..."
                    self.pipelineStateColor = .yellow
                case is VoicePipelineState.Speaking:
                    self.pipelineStateText = "Speaking"
                    self.pipelineStateColor = .green
                case is VoicePipelineState.Listening:
                    self.pipelineStateText = "Listening..."
                    self.pipelineStateColor = .blue
                default:
                    self.pipelineStateText = "Unknown"
                    self.pipelineStateColor = .gray
                }
            }
        }

        let lipSyncTask = Task { [weak self] in
            for await shapes in pipeline.lipSyncOutput {
                guard let self, !Task.isCancelled else { break }
                var result: [String: Float] = [:]
                for (key, value) in shapes {
                    if let blendShape = key as? BlendShape {
                        result[blendShape.arKitName] = value.floatValue
                    }
                }
                self.lipSyncShapes = result
            }
        }

        observeTasks = [stateTask, lipSyncTask]
    }

    private func buildTtsConfig() -> TtsConfig {
        switch selectedProvider {
        case "Voicevox":
            return TtsConfig.Voicevox(host: voicevoxHost, port: 50021, speakerId: 0)
        case "OpenAI":
            return TtsConfig.OpenAiTts(apiKey: apiKey, voice: voiceId, model: "tts-1", speed: 1.0)
        case "ElevenLabs":
            return TtsConfig.ElevenLabs(apiKey: apiKey, voiceId: voiceId, modelId: "eleven_multilingual_v2", stability: 0.5, similarityBoost: 0.75)
        default:
            return TtsConfig.Voicevox(host: "localhost", port: 50021, speakerId: 0)
        }
    }

    deinit {
        observeTasks.forEach { $0.cancel() }
        pipeline?.release()
    }
}
