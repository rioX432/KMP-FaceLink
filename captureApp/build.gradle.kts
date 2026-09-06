import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.anotherball.facelink")
}

/**
 * The build stamps where it came from into the app, and every take records it.
 *
 * A number whose provenance is missing cannot be checked later, and one checked against the wrong
 * tree is worse than no number: twice in one day a measurement was attributed to "production" while
 * the code actually being read was a feature branch from six weeks earlier. `dirty` matters as much
 * as the hash — a take built from an uncommitted tree cannot be reconstructed from the hash alone,
 * and `CaptureCondition` marks it unreproducible on that basis.
 */
fun git(vararg args: String): String = providers.exec {
    commandLine("git", *args)
    workingDir = rootDir
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }.getOrElse("")

val gitSha = git("rev-parse", "HEAD")
val gitBranch = git("rev-parse", "--abbrev-ref", "HEAD")
val gitCommitTime = git("show", "-s", "--format=%cI", "HEAD")
val gitDirty = git("status", "--porcelain").isNotEmpty()
data class ReviewAuthorization(
    val packetId: String,
    val packetManifestSha256: String,
    val sourceManifestSha256: String,
    val fileSha256: String,
)

fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }

fun String.requireLowercaseSha256(label: String): String = also {
    require(matches(Regex("[0-9a-f]{64}"))) { "$label must be a lowercase SHA-256" }
}

val reviewAuthorization = providers.gradleProperty("facelink.reviewAuthorizationFile")
    .orNull
    ?.let(rootProject::file)
    ?.let { authorizationFile ->
        require(authorizationFile.isFile) {
            "review authorization file does not exist: ${authorizationFile.path}"
        }
        val content = authorizationFile.readText(Charsets.UTF_8)
        require('\r' !in content) { "review authorization must use LF line endings" }
        val lines = content.split('\n')
        require(lines.size == 4 && lines.last().isEmpty()) {
            "review authorization must contain exactly three LF-terminated lines"
        }
        val entries = lines.dropLast(1).map { line ->
            val separator = line.indexOf('=')
            require(separator > 0 && separator == line.lastIndexOf('=')) {
                "review authorization lines must contain exactly one '='"
            }
            line.substring(0, separator) to line.substring(separator + 1)
        }
        require(entries.map { it.first } == listOf(
            "packetId",
            "packetManifestSha256",
            "sourceManifestSha256",
        )) { "review authorization keys/order must match the canonical three-line schema" }
        val packetId = entries[0].second
        require(packetId.matches(Regex("[a-z0-9][a-z0-9._-]{0,127}"))) {
            "review authorization packetId must be a canonical lowercase identifier"
        }
        ReviewAuthorization(
            packetId = packetId,
            packetManifestSha256 = entries[1].second
                .requireLowercaseSha256("packetManifestSha256"),
            sourceManifestSha256 = entries[2].second
                .requireLowercaseSha256("sourceManifestSha256"),
            fileSha256 = authorizationFile.readBytes().sha256Hex(),
        )
    }
val reviewPacketId = reviewAuthorization?.packetId.orEmpty()
val reviewPacketSha = reviewAuthorization?.packetManifestSha256.orEmpty()
val reviewSourceManifestSha = reviewAuthorization?.sourceManifestSha256.orEmpty()
val reviewAuthorizationSha = reviewAuthorization?.fileSha256.orEmpty()
val sessionCExecutionContractSha = providers.gradleProperty("facelink.sessionCExecutionContractSha256")
    .orNull
    .orEmpty()
    .also { value ->
        if (value.isNotEmpty()) value.requireLowercaseSha256("sessionCExecutionContractSha256")
    }

val h2SourceManifestSha = providers.gradleProperty("facelink.h2SourceManifestSha256")
    .orNull
    .orEmpty()
    .also { value ->
        if (value.isNotEmpty()) value.requireLowercaseSha256("h2SourceManifestSha256")
    }
val h2SourcePatchSha = providers.gradleProperty("facelink.h2SourcePatchSha256")
    .orNull
    .orEmpty()
    .also { value ->
        if (value.isNotEmpty()) value.requireLowercaseSha256("h2SourcePatchSha256")
    }
val h2BaseCommitSha = providers.gradleProperty("facelink.h2BaseCommitSha")
    .orNull
    .orEmpty()
    .also { value ->
        require(value.isEmpty() || value.matches(Regex("[0-9a-f]{40}"))) {
            "h2BaseCommitSha must be a lowercase Git commit SHA"
        }
    }
val h2BuildInputMatchesManifest = providers.gradleProperty("facelink.h2BuildInputMatchesManifest")
    .orNull
    ?.toBooleanStrict()
    ?: false
val h2ExpectedModelSha = "a304d132e36cad9f6dcc9a8c1c78f34201cbee90e640f39625a2fbe05fd12dd3"
val h2ModelInput = providers.gradleProperty("facelink.h2CheekModelFile")
    .orNull
    ?.let(rootProject::file)
    ?.also { modelFile ->
        require(modelFile.isFile) { "H2 cheek model input is not a regular file" }
        require(modelFile.readBytes().sha256Hex() == h2ExpectedModelSha) {
            "H2 cheek model input SHA-256 does not match the frozen candidate"
        }
    }
val h2GeneratedModelAssets = layout.buildDirectory.dir("generated/h2-model-assets")
val prepareH2ModelAsset by tasks.registering {
    outputs.dir(h2GeneratedModelAssets)
    doLast {
        val outputDirectory = h2GeneratedModelAssets.get().asFile
        outputDirectory.mkdirs()
        val outputFile = outputDirectory.resolve("h2/c3b-cheek-model.json")
        if (outputFile.exists()) outputFile.delete()
        h2ModelInput?.let { modelFile ->
            outputFile.parentFile.mkdirs()
            val retained = modelFile.readBytes()
            require(retained.sha256Hex() == h2ExpectedModelSha) {
                "H2 cheek model changed after configuration"
            }
            outputFile.writeBytes(retained)
        }
    }
}

android {
    namespace = "io.github.kmpfacelink.capture.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.kmpfacelink.capture.app"
        // This validation recorder uses java.time.Instant in its signed Session-C authorization.
        // The reusable kmp-facelink libraries retain their minSdk 24 consumer contract.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("String", "GIT_BRANCH", "\"$gitBranch\"")
        buildConfigField("String", "GIT_COMMIT_TIME", "\"$gitCommitTime\"")
        buildConfigField("boolean", "GIT_DIRTY", "$gitDirty")
        buildConfigField("String", "REVIEW_PACKET_ID", "\"$reviewPacketId\"")
        buildConfigField("String", "REVIEW_PACKET_SHA256", "\"$reviewPacketSha\"")
        buildConfigField("String", "REVIEW_SOURCE_MANIFEST_SHA256", "\"$reviewSourceManifestSha\"")
        buildConfigField("String", "REVIEW_AUTHORIZATION_SHA256", "\"$reviewAuthorizationSha\"")
        buildConfigField("String", "SESSION_C_EXECUTION_CONTRACT_SHA256", "\"$sessionCExecutionContractSha\"")
        buildConfigField("String", "H2_PROTOCOL_ID", "\"DK_CHEEK_S02V17_H2_SCOREFREE_PROTOCOL_PREREG_V2\"")
        buildConfigField(
            "String",
            "H2_PROTOCOL_SHA256",
            "\"67cc2445c04201abe5abb65d5b111eda9204e86e75b31d8d6362584fcf684bd4\"",
        )
        buildConfigField(
            "String",
            "H2_PROTOCOL_PREREG_SHA256",
            "\"67cc2445c04201abe5abb65d5b111eda9204e86e75b31d8d6362584fcf684bd4\"",
        )
        buildConfigField(
            "String",
            "H2_PROTOCOL_V2_CORRECTION_SHA256",
            "\"7f88bae333f16e229400b986e704771b22cbf0b1a5df3dac6db9490859ffc53e\"",
        )
        buildConfigField(
            "String",
            "H2_V1_SCAFFOLD_RECEIPT_SHA256",
            "\"a67dfecbff906aba9aa0c528c5b0a596976be535be4e905329ffc493ad02a23a\"",
        )
        buildConfigField(
            "String",
            "H2_CONTRACT_IDENTITY_DESIGN_SHA256",
            "\"7a3d51a45391fb6cb07b1dfb07825134d5f2058026f1c6474b54855852250bbd\"",
        )
        buildConfigField(
            "String",
            "H2_IMPLEMENTATION_AUTHORITY_SHA256",
            "\"1803f5cb51e42852739dd483ddb401f973617981aac22e33991be6a14414c7b8\"",
        )
        buildConfigField("String", "H2_SOURCE_MANIFEST_SHA256", "\"$h2SourceManifestSha\"")
        buildConfigField("String", "H2_SOURCE_PATCH_SHA256", "\"$h2SourcePatchSha\"")
        buildConfigField("String", "H2_BASE_COMMIT_SHA", "\"$h2BaseCommitSha\"")
        buildConfigField("boolean", "H2_BUILD_INPUT_MATCHES_MANIFEST", "$h2BuildInputMatchesManifest")
        buildConfigField("boolean", "H2_PHYSICAL_EXECUTION_ENABLED", "false")
        buildConfigField(
            "String",
            "H2_PRIMARY_PACKET_SHA256",
            "\"aee9367959ce3b43deb8ba4f189a870c40577d09c57525c5133a780c3d25216a\"",
        )
        buildConfigField(
            "String",
            "H2_PRIMARY_POLICY_SHA256",
            "\"05e0d942c146d595982c8f428471dbe54eccebe690ec193c9c1825fd64928034\"",
        )
        buildConfigField(
            "String",
            "H2_COMPARATOR_PACKET_SHA256",
            "\"19a669297ab1c143725b4b3931a77805b94635278bc833b5bb344d0a0f9414ce\"",
        )
        buildConfigField(
            "String",
            "H2_COMPARATOR_POLICY_SHA256",
            "\"474710061642887d9afcb928a34c2130b4485985049dfffeffe97187389fd6f7\"",
        )
        buildConfigField(
            "String",
            "H2_PRIMARY_MODEL_SHA256",
            "\"$h2ExpectedModelSha\"",
        )
        buildConfigField("boolean", "H2_PRIMARY_MODEL_BUNDLED", "${h2ModelInput != null}")
        buildConfigField("boolean", "H2_ORDINARY_PRODUCT_ROUTE_UNCHANGED", "true")
        buildConfigField("boolean", "H2_CANONICAL_52_UNCHANGED", "true")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets.getByName("main").assets.srcDir(h2GeneratedModelAssets.get().asFile)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.named("preBuild").configure { dependsOn(prepareH2ModelAsset) }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=io.github.kmpfacelink.ExperimentalFaceLinkApi")
    }
}

faceLink {
    // Only the face model: this app records faces and nothing else.
    faceEnabled.set(true)
    handEnabled.set(false)
    bodyEnabled.set(false)
}

dependencies {
    implementation(project(":kmp-facelink"))
    implementation(project(":kmp-facelink-capture"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
