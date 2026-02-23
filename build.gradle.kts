plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
}

val detektFormatting = libs.detekt.formatting

detekt {
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    config.setFrom(rootProject.files("detekt.yml"))
}

dependencies {
    detektPlugins(detektFormatting)
    dokka(project(":kmp-facelink"))
    dokka(project(":kmp-facelink-avatar"))
    dokka(project(":kmp-facelink-actions"))
    dokka(project(":kmp-facelink-effects"))
    dokka(project(":kmp-facelink-live2d"))
    dokka(project(":kmp-facelink-stream"))
    dokka(project(":kmp-facelink-voice"))
    dokka(project(":kmp-facelink-rive"))
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    detekt {
        buildUponDefaultConfig = true
        allRules = false
        parallel = true
        config.setFrom(rootProject.files("detekt.yml"))
    }

    dependencies {
        detektPlugins(detektFormatting)
    }
}

// Configure detekt source paths for KMP modules
project(":kmp-facelink") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/androidMain/kotlin",
                "src/iosMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-avatar") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-actions") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-effects") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-live2d") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-stream") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-voice") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/androidMain/kotlin",
                "src/iosMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-rive") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/androidMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}
