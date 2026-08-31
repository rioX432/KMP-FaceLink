import SwiftUI

/// Home/landing screen showing feature cards for each tracking mode.
struct HomeView: View {
    let onModeSelect: (TrackingMode) -> Void

    private let features: [(mode: TrackingMode, icon: String, title: String, description: String)] = [
        (.face, "😊", "Face Tracking",
         "Named expression coefficients, head pose, and platform-specific tracking visualization."),
        (.hand, "✋", "Hand Tracking",
         "21-point hand landmarks with gesture recognition for both hands."),
        (.interact, "💬", "Interact",
         "Live stream event feed with mock chat, gifts, raids, and action triggers."),
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                headerSection
                featureCardList
            }
            .padding(.bottom, 16)
        }
        .background(Color(.systemBackground))
    }

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("KMP-FaceLink")
                .font(.largeTitle.bold())
                .foregroundStyle(.primary)
                .dynamicTypeSize(...DynamicTypeSize.accessibility3)
            Text("Cross-platform face, hand & body tracking SDK")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .dynamicTypeSize(...DynamicTypeSize.accessibility1)
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

    /// Icon frame size that scales with Dynamic Type.
    @ScaledMetric(relativeTo: .title) private var iconSize: CGFloat = 40

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Text(icon)
                    .font(.title)
                    .frame(width: iconSize, height: iconSize)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(.primary)
                    Text(description)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .foregroundStyle(.secondary)
                    .font(.caption)
            }
            .padding(16)
            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
        }
        .accessibilityLabel("\(title). \(description)")
    }
}
