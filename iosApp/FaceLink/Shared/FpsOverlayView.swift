import SwiftUI

struct FpsOverlayView: View {
    let frameTimestamp: Int64

    @State private var fps: Double = 0
    @State private var ringBuffer = [Int64](repeating: 0, count: 30)
    @State private var ringIndex = 0
    @State private var timer: Timer?

    var body: some View {
        Text(String(format: "%.0f FPS", fps))
            .font(.system(.caption2, design: .monospaced))
            .foregroundStyle(.white.opacity(0.8))
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(.ultraThinMaterial, in: Capsule())
            .onChange(of: frameTimestamp) { _, newValue in
                guard newValue > 0 else { return }
                ringBuffer[ringIndex % 30] = newValue
                ringIndex += 1
            }
            .onAppear {
                timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { _ in
                    let filled = min(ringIndex, 30)
                    guard filled >= 2 else { return }
                    let newest = ringBuffer[(ringIndex - 1) % 30]
                    let oldest = ringBuffer[ringIndex % 30]
                    let delta = newest - oldest
                    if delta > 0 {
                        fps = Double(filled - 1) * 1000.0 / Double(delta)
                    }
                }
            }
            .onDisappear {
                timer?.invalidate()
                timer = nil
            }
    }
}
