import SwiftUI

/// A small status indicator badge showing tracking state text with a colored dot.
struct StatusBadge: View {
    let text: String
    let isTracking: Bool

    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(isTracking ? .green : .gray)
                .frame(width: 8, height: 8)
            Text(text)
                .font(.system(.caption, design: .monospaced).weight(.medium))
                .dynamicTypeSize(...DynamicTypeSize.accessibility1)
                .foregroundStyle(.primary)
                .lineLimit(1)
                .truncationMode(.tail)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(.ultraThinMaterial, in: Capsule())
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Status: \(text)")
    }
}

#Preview {
    VStack(spacing: 12) {
        StatusBadge(text: "Tracking", isTracking: true)
        StatusBadge(text: "Idle", isTracking: false)
        StatusBadge(text: "Stopped", isTracking: false)
    }
    .padding()
    .background(Color(.systemBackground))
}
