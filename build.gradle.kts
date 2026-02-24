plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.nexus.publish)
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(providers.environmentVariable("OSSRH_USERNAME"))
            password.set(providers.environmentVariable("OSSRH_PASSWORD"))
        }
    }
}

val publishableModules = setOf(
    "kmp-facelink",
    "kmp-facelink-avatar",
    "kmp-facelink-actions",
    "kmp-facelink-effects",
    "kmp-facelink-stream",
    "kmp-facelink-voice",
    "kmp-facelink-rive",
)

val pomDescriptions = mapOf(
    "kmp-facelink" to "Kotlin Multiplatform face, hand, and body tracking library",
    "kmp-facelink-avatar" to "Live2D parameter mapping for KMP-FaceLink",
    "kmp-facelink-actions" to "Gesture and expression trigger system for KMP-FaceLink",
    "kmp-facelink-effects" to "Real-time face effects engine for KMP-FaceLink",
    "kmp-facelink-stream" to "WebSocket streaming (VTubeStudio protocol) for KMP-FaceLink",
    "kmp-facelink-voice" to "ASR, TTS, and lip sync for KMP-FaceLink",
    "kmp-facelink-rive" to "Rive avatar integration for KMP-FaceLink",
)

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

    if (name in publishableModules) {
        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        group = property("GROUP").toString()
        version = property("VERSION_NAME").toString()

        val javadocJar by tasks.registering(Jar::class) {
            archiveClassifier.set("javadoc")
        }

        afterEvaluate {
            extensions.configure<PublishingExtension> {
                publications.withType<MavenPublication>().configureEach {
                    artifact(javadocJar)
                    pom {
                        name.set(this@subprojects.name)
                        description.set(pomDescriptions[this@subprojects.name] ?: this@subprojects.name)
                        url.set(property("POM_URL").toString())
                        licenses {
                            license {
                                name.set(property("POM_LICENCE_NAME").toString())
                                url.set(property("POM_LICENCE_URL").toString())
                            }
                        }
                        developers {
                            developer {
                                id.set(property("POM_DEVELOPER_ID").toString())
                                name.set(property("POM_DEVELOPER_NAME").toString())
                            }
                        }
                        scm {
                            url.set(property("POM_SCM_URL").toString())
                            connection.set(property("POM_SCM_CONNECTION").toString())
                            developerConnection.set(property("POM_SCM_DEV_CONNECTION").toString())
                        }
                    }
                }
            }

            extensions.configure<SigningExtension> {
                val signingKeyId = providers.environmentVariable("GPG_KEY_ID").orNull
                val signingKey = providers.environmentVariable("GPG_KEY").orNull
                val signingPassword = providers.environmentVariable("GPG_PASSPHRASE").orNull
                if (signingKeyId != null) {
                    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
                    sign(extensions.getByType<PublishingExtension>().publications)
                }
            }
        }
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
                "src/androidMain/kotlin",
                "src/iosMain/kotlin",
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
