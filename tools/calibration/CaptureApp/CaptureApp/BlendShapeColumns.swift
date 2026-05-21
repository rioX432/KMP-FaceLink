import ARKit

/// All 52 ARKit face blendshape locations in a stable, sorted order.
///
/// This ordering is used as the CSV column layout for `arkit_blendshapes.csv`.
/// The order matches Apple's documentation alphabetical listing.
enum BlendShapeColumns {
    static let allLocations: [ARFaceAnchor.BlendShapeLocation] = [
        .browDownLeft,
        .browDownRight,
        .browInnerUp,
        .browOuterUpLeft,
        .browOuterUpRight,
        .cheekPuff,
        .cheekSquintLeft,
        .cheekSquintRight,
        .eyeBlinkLeft,
        .eyeBlinkRight,
        .eyeLookDownLeft,
        .eyeLookDownRight,
        .eyeLookInLeft,
        .eyeLookInRight,
        .eyeLookOutLeft,
        .eyeLookOutRight,
        .eyeLookUpLeft,
        .eyeLookUpRight,
        .eyeSquintLeft,
        .eyeSquintRight,
        .eyeWideLeft,
        .eyeWideRight,
        .jawForward,
        .jawLeft,
        .jawOpen,
        .jawRight,
        .mouthClose,
        .mouthDimpleLeft,
        .mouthDimpleRight,
        .mouthFrownLeft,
        .mouthFrownRight,
        .mouthFunnel,
        .mouthLeft,
        .mouthLowerDownLeft,
        .mouthLowerDownRight,
        .mouthPressLeft,
        .mouthPressRight,
        .mouthPucker,
        .mouthRight,
        .mouthRollLower,
        .mouthRollUpper,
        .mouthShrugLower,
        .mouthShrugUpper,
        .mouthSmileLeft,
        .mouthSmileRight,
        .mouthStretchLeft,
        .mouthStretchRight,
        .mouthUpperUpLeft,
        .mouthUpperUpRight,
        .noseSneerLeft,
        .noseSneerRight,
        .tongueOut
    ]

    /// Column names for CSV header, matching `allLocations` order.
    static let columnNames: [String] = allLocations.map { $0.rawValue }

    /// CSV header line: "timestamp,browDownLeft,browDownRight,..."
    static var csvHeader: String {
        (["timestamp"] + columnNames).joined(separator: ",")
    }
}
