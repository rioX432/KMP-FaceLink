import SwiftUI
import SampleShared

/// Settings view for adjusting smoothing parameters
struct SmoothingSettingsView: View {
    @ObservedObject var settings: SmoothingSettings
    let onApply: (SmoothingConfig) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("Smoothing Mode") {
                    Picker("Mode", selection: $settings.mode) {
                        Text("None").tag(SmoothingMode.none)
                        Text("EMA").tag(SmoothingMode.ema)
                        Text("One Euro").tag(SmoothingMode.oneEuro)
                    }
                    .pickerStyle(.segmented)
                }

                switch settings.mode {
                case .none:
                    Section {
                        Text("No smoothing applied")
                            .foregroundStyle(.secondary)
                    }

                case .ema:
                    Section("EMA Parameters") {
                        VStack(alignment: .leading) {
                            HStack {
                                Text("Alpha")
                                Spacer()
                                Text(String(format: "%.2f", settings.emaAlpha))
                                    .foregroundStyle(.secondary)
                            }
                            Slider(value: $settings.emaAlpha, in: 0.1...1.0, step: 0.05)
                        }
                        Text("Higher = less smoothing, more responsive")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                case .oneEuro:
                    Section("One Euro Filter Parameters") {
                        VStack(alignment: .leading) {
                            HStack {
                                Text("Min Cutoff")
                                Spacer()
                                Text(String(format: "%.2f", settings.minCutoff))
                                    .foregroundStyle(.secondary)
                            }
                            Slider(value: $settings.minCutoff, in: 0.1...5.0, step: 0.1)
                        }

                        VStack(alignment: .leading) {
                            HStack {
                                Text("Beta")
                                Spacer()
                                Text(String(format: "%.3f", settings.beta))
                                    .foregroundStyle(.secondary)
                            }
                            Slider(value: $settings.beta, in: 0.0...0.1, step: 0.005)
                        }

                        VStack(alignment: .leading) {
                            HStack {
                                Text("D Cutoff")
                                Spacer()
                                Text(String(format: "%.2f", settings.dCutoff))
                                    .foregroundStyle(.secondary)
                            }
                            Slider(value: $settings.dCutoff, in: 0.5...2.0, step: 0.1)
                        }

                        Text("Adaptive filter: reduces jitter while preserving quick movements")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                Section {
                    Button("Apply Changes") {
                        onApply(settings.toSmoothingConfig())
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .navigationTitle("Smoothing")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

// MARK: - Settings Model

enum SmoothingMode: String, CaseIterable {
    case none
    case ema
    case oneEuro
}

@MainActor
class SmoothingSettings: ObservableObject {
    @Published var mode: SmoothingMode = .ema
    @Published var emaAlpha: Double = 0.4
    @Published var minCutoff: Double = 1.0
    @Published var beta: Double = 0.007
    @Published var dCutoff: Double = 1.0

    func toSmoothingConfig() -> SmoothingConfig {
        switch mode {
        case .none:
            return SmoothingConfig.None.shared
        case .ema:
            return SmoothingConfig.Ema(alpha: Float(emaAlpha))
        case .oneEuro:
            return SmoothingConfig.OneEuro(
                minCutoff: Float(minCutoff),
                beta: Float(beta),
                dCutoff: Float(dCutoff),
                predictionMs: 0
            )
        }
    }

    func update(from config: SmoothingConfig) {
        switch config {
        case is SmoothingConfig.None:
            mode = .none
        case let ema as SmoothingConfig.Ema:
            mode = .ema
            emaAlpha = Double(ema.alpha)
        case let oneEuro as SmoothingConfig.OneEuro:
            mode = .oneEuro
            minCutoff = Double(oneEuro.minCutoff)
            beta = Double(oneEuro.beta)
            dCutoff = Double(oneEuro.dCutoff)
        default:
            break
        }
    }
}

#Preview {
    SmoothingSettingsView(settings: SmoothingSettings()) { _ in }
}
