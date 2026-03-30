import SwiftUI

/// Home/landing screen showing feature cards for each tracking mode.
struct HomeView: View {
    let onModeSelect: (TrackingMode) -> Void

    private let features: [(mode: TrackingMode, icon: String, title: String, description: String)] = [
        (.face, "😊", "Face Tracking",
         "52 ARKit blend shapes, head pose, and facial landmarks with real-time visualization."),
        (.hand, "✋", "Hand Tracking",
         "21-point hand landmarks with gesture recognition for both hands."),
        (.holistic, "🧑", "Holistic Tracking",
         "Combined face + hand tracking in a single pipeline for full upper-body capture."),
        (.actions, "⚡", "Actions",
         "High-level action detection built on top of face tracking data."),
        (.voice, "🎤", "Voice",
         "TTS with real-time lip sync blend shapes and speech recognition."),
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                headerSection
                featureCardList
            }
            .padding(.bottom, 16)
        }
        .background(Color(white: 0.07))
    }

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("KMP-FaceLink")
                .font(.largeTitle.bold())
                .foregroundStyle(.white)
            Text("Cross-platform face, hand & body tracking SDK")
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.6))
        }
        .padding(.horizontal, 20)
        .padding(.top, 16)
    }

    private var featureCardList: some View {
        VStack(spacing: 12) {
            ForEach(features, id: \.mode) { feature in
                FeatureCard(
                    icon: feature.icon,
                    title: feature.title,
                    description: feature.description
                ) {
                    onModeSelect(feature.mode)
                }
            }
        }
        .padding(.horizontal, 16)
    }
}

private struct FeatureCard: View {
    let icon: String
    let title: String
    let description: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Text(icon)
                    .font(.title)
                    .frame(width: 40, height: 40)

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(.white)
                    Text(description)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.6))
                        .lineLimit(2)
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .foregroundStyle(.white.opacity(0.4))
                    .font(.caption)
            }
            .padding(16)
            .background(Color(white: 0.12), in: RoundedRectangle(cornerRadius: 12))
        }
    }
}
