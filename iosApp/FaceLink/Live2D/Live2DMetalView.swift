import SwiftUI
import MetalKit

/// SwiftUI wrapper around MTKView for Live2D rendering via CubismBridge.
struct Live2DMetalView: UIViewRepresentable {
    let bridge: CubismBridge
    let commandQueue: MTLCommandQueue

    func makeUIView(context: Context) -> MTKView {
        let metalView = MTKView()
        metalView.device = commandQueue.device
        metalView.colorPixelFormat = .bgra8Unorm
        metalView.depthStencilPixelFormat = .depth32Float
        metalView.clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 1)
        metalView.delegate = context.coordinator
        metalView.preferredFramesPerSecond = 60
        metalView.enableSetNeedsDisplay = false
        metalView.isPaused = false

        // Register the CAMetalLayer with the Cubism rendering singleton
        if let metalLayer = metalView.layer as? CAMetalLayer {
            CubismBridge.setMetalLayer(metalLayer)
        }

        return metalView
    }

    func updateUIView(_ uiView: MTKView, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(bridge: bridge, commandQueue: commandQueue)
    }

    class Coordinator: NSObject, MTKViewDelegate {
        let bridge: CubismBridge
        let commandQueue: MTLCommandQueue
        private var lastTime: CFTimeInterval = CACurrentMediaTime()

        init(bridge: CubismBridge, commandQueue: MTLCommandQueue) {
            self.bridge = bridge
            self.commandQueue = commandQueue
            super.init()
        }

        func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

        func draw(in view: MTKView) {
            guard bridge.isModelLoaded,
                  let commandBuffer = commandQueue.makeCommandBuffer(),
                  let rpd = view.currentRenderPassDescriptor,
                  let drawable = view.currentDrawable else { return }

            let now = CACurrentMediaTime()
            let deltaTime = Float(now - lastTime)
            lastTime = now

            // Single thread-safe call: applies pending params, updates physics, draws
            bridge.renderFrame(
                withDeltaTime: deltaTime,
                commandBuffer: commandBuffer,
                renderPassDescriptor: rpd,
                drawableSize: view.drawableSize
            )

            commandBuffer.present(drawable)
            commandBuffer.commit()
        }
    }
}
