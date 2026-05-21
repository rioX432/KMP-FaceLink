plugins {
    `java-platform`
}

dependencies {
    constraints {
        api(project(":kmp-facelink"))
        api(project(":kmp-facelink-avatar"))
        api(project(":kmp-facelink-actions"))
        api(project(":kmp-facelink-voice"))
        api(project(":kmp-facelink-llm"))
        api(project(":kmp-facelink-pipeline"))
        api(project(":kmp-facelink-networking"))
        api(project(":kmp-facelink-vmc"))
        api(project(":kmp-facelink-ifm"))
        api(project(":kmp-facelink-stream"))
        api(project(":kmp-facelink-vts"))
        api(project(":kmp-facelink-interact"))
        api(project(":kmp-facelink-compose"))
    }
}

afterEvaluate {
    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["javaPlatform"])
            }
        }
    }
}
