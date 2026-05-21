import SwiftUI

/// A button style for tracking start/stop buttons.
/// Shows a filled capsule that changes color based on tracking state.
struct TrackingButtonStyle: ButtonStyle {
    let isTracking: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 48)
            .background(
                (isTracking ? Color.red : Color.blue)
                    .opacity(configuration.isPressed ? 0.7 : 1.0),
                in: Capsule()
            )
            .padding(.horizontal, 16)
            .animation(.easeInOut(duration: 0.15), value: configuration.isPressed)
    }
}

#Preview {
    VStack(spacing: 16) {
        Button("Start") {}
            .buttonStyle(TrackingButtonStyle(isTracking: false))
        Button("Stop") {}
            .buttonStyle(TrackingButtonStyle(isTracking: true))
    }
    .padding()
}
