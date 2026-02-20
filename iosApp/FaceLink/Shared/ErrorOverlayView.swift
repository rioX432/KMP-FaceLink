import SwiftUI

struct ErrorOverlayView: View {
    let message: String
    var onRetry: (() -> Void)?

    var body: some View {
        VStack(spacing: 8) {
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)

            if let onRetry {
                Button("Retry") {
                    onRetry()
                }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.red)
                .padding(.horizontal, 16)
                .padding(.vertical, 6)
                .background(.white, in: Capsule())
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity)
        .background(.red.opacity(0.9), in: RoundedRectangle(cornerRadius: 8))
        .padding(.horizontal)
    }
}
