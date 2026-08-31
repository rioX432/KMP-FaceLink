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
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

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
