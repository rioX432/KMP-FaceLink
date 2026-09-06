@file:Suppress(
    "ArgumentListWrapping",
    "CyclomaticComplexMethod",
    "Indentation",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "MaximumLineLength",
    "MultiLineIfElse",
    "TooManyFunctions",
    "Wrapping",
)

package io.github.kmpfacelink.capture

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/** Retained public inputs used by the trusted capture-seal verifier. No hash is caller supplied. */
internal data class H2CaptureSealInputBytes(
    val sourceManifest: ByteArray,
    val sourcePatch: ByteArray,
    val apk: ByteArray,
    val buildConfig: ByteArray,
    val baseCommitSha: String,
    val binaryReceipt: ByteArray,
    val devicePreflight: ByteArray,
    val privacyStorageParticipantAuthority: ByteArray,
    val rioCaptureAuthority: ByteArray,
)

/**
 * Verifies finalized score-free stream bytes before building the capture-only terminal seal.
 * A later physical/action annotation is a separate transition bound to this seal's SHA.
 */
internal class AndroidH2FinalizedRunVerifier(private val blockRoot: File) {
    private val json = Json { ignoreUnknownKeys = false }
    private val hasher = H2SealSha256(::sha256)

    init {
        require(blockRoot.isDirectory)
        refuseLink(blockRoot)
        require(blockRoot.canonicalFile == blockRoot.absoluteFile)
    }

    fun verifyAndBuildCaptureSeal(
        state: H2RunState,
        inputs: H2CaptureSealInputBytes,
        sealedAtIso: String,
    ): H2PublicSafeSeal {
        require(state.phase == H2RunPhase.OPEN && state.activeBlockId == null)
        require(state.nextBlockIndex == H2ConfirmationContract.EXPECTED_BLOCK_COUNT)
        require(state.failures.size <= 1) { "H2 bounded retry ledger has more than one failure" }
        val expected = expectedAttempts(state)
        require(blockRoot.listFiles().orEmpty().map { it.name }.toSet() == expected.map { it.directoryName }.toSet()) {
            "H2 finalized attempt inventory has a missing, extra, or duplicate directory"
        }

        val eventAggregate = StringBuilder()
        val clockAggregate = StringBuilder()
        val manifestAggregate = StringBuilder()
        expected.forEach { attempt ->
            val directory = File(blockRoot, attempt.directoryName)
            require(directory.isDirectory && directory.canonicalFile.parentFile == blockRoot.canonicalFile)
            refuseLink(directory)
            val manifestName = if (attempt.terminated) {
                H2StreamingBlockWriter.TERMINATED_STREAM_MANIFEST
            } else {
                H2StreamingBlockWriter.STREAM_MANIFEST
            }
            val manifest = verifyManifest(directory, manifestName, attempt)
            if (!attempt.terminated) {
                val receipt = state.receipts.single { it.blockId == attempt.blockId }
                require(receipt.captureAttempt == attempt.attempt)
                require(receipt.retainedStreamManifestSha256 == sha256(manifest))
            }
            val events = File(directory, EVENT_STREAM).takeIf(File::isFile)?.readBytes() ?: ByteArray(0)
            val parsedEvents = parseEvents(events)
            val mappingCanonical = verifyEvents(attempt, parsedEvents, state.runIdentitySha256)
            if (!attempt.terminated) verifyPublishedScientificEvidence(directory, attempt, parsedEvents, state)
            eventAggregate.append(attempt.identityLine).append(sha256(events)).append('\n')
            clockAggregate.append(attempt.identityLine).append(mappingCanonical).append('\n')
            manifestAggregate.append(attempt.identityLine).append(sha256(manifest)).append('\n')
        }

        val receiptsCanonical = state.orderedBlockReceiptsCanonicalUtf8Lf()
        val digests = H2VerifiedSealDigestBundle(
            runIdentitySha256 = state.runIdentitySha256,
            orderedBlockReceiptsCanonicalUtf8Lf = receiptsCanonical,
            orderedBlockReceiptsSha256 = sha256(receiptsCanonical.encodeToByteArray()),
            controllerEventStreamsSha256 = sha256(eventAggregate.toString().encodeToByteArray()),
            clockMappingReceiptSha256 = sha256(clockAggregate.toString().encodeToByteArray()),
            retainedStreamManifestsSha256 = sha256(manifestAggregate.toString().encodeToByteArray()),
        )
        val authority = verifySealInputs(inputs, state.runIdentitySha256)
        return buildH2PublicSafeSeal(
            state,
            authority,
            digests,
            missingExtraDuplicateMutationChecksPassed = true,
            sealedAtIso = sealedAtIso,
        )
    }

    private fun verifySealInputs(inputs: H2CaptureSealInputBytes, runIdentitySha256: String): H2SealAuthorityChain {
        val sourceManifestSha = sha256(inputs.sourceManifest)
        val sourcePatchSha = sha256(inputs.sourcePatch)
        val apkSha = sha256(inputs.apk)
        val buildConfigSha = sha256(inputs.buildConfig)
        val binaryReceiptSha = sha256(inputs.binaryReceipt)
        val deviceSha = sha256(inputs.devicePreflight)
        val privacySha = sha256(inputs.privacyStorageParticipantAuthority)
        val source = parseObject(inputs.sourceManifest)
        require(source.string("protocol_sha256") == H2ConfirmationContract.PROTOCOL_PREREG_SHA256)
        require(source.string("overlay_sha256") == sourcePatchSha)
        require(source.obj("source_base").string("commit_sha") == inputs.baseCommitSha)

        val config = inputs.buildConfig.decodeToString()
        requireBuildConfig(config, "H2_PROTOCOL_SHA256", H2ConfirmationContract.PROTOCOL_PREREG_SHA256)
        requireBuildConfig(config, "H2_SOURCE_MANIFEST_SHA256", sourceManifestSha)
        requireBuildConfig(config, "H2_SOURCE_PATCH_SHA256", sourcePatchSha)
        requireBuildConfig(config, "H2_BASE_COMMIT_SHA", inputs.baseCommitSha)
        requireBuildConfig(config, "H2_BUILD_INPUT_MATCHES_MANIFEST", "true")
        requireBuildConfig(config, "H2_PHYSICAL_EXECUTION_ENABLED", "true")
        requireBuildConfig(config, "H2_PRIMARY_MODEL_BUNDLED", "true")

        val binary = parseObject(inputs.binaryReceipt)
        requireChain(binary, sourceManifestSha, sourcePatchSha, inputs.baseCommitSha, buildConfigSha, apkSha)
        val device = parseObject(inputs.devicePreflight)
        require(device.string("protocol_sha256") == H2ConfirmationContract.PROTOCOL_PREREG_SHA256)
        require(device.string("binary_receipt_sha256") == binaryReceiptSha)
        require(device.string("apk_sha256") == apkSha)
        require(device.string("build_config_sha256") == buildConfigSha)
        val privacy = parseObject(inputs.privacyStorageParticipantAuthority)
        require(privacy.string("protocol_sha256") == H2ConfirmationContract.PROTOCOL_PREREG_SHA256)
        require(privacy.string("binary_receipt_sha256") == binaryReceiptSha)
        require(privacy.string("device_preflight_sha256") == deviceSha)
        val capture = parseObject(inputs.rioCaptureAuthority)
        require(capture.string("protocol_sha256") == H2ConfirmationContract.PROTOCOL_PREREG_SHA256)
        require(capture.string("binary_receipt_sha256") == binaryReceiptSha)
        require(capture.string("device_preflight_sha256") == deviceSha)
        require(capture.string("privacy_authority_sha256") == privacySha)
        require(capture.string("run_identity_sha256") == runIdentitySha256)

        return H2SealAuthorityChain.fromRetainedBytes(
            inputs.sourceManifest,
            inputs.sourcePatch,
            inputs.apk,
            inputs.buildConfig,
            inputs.baseCommitSha,
            inputs.binaryReceipt,
            inputs.devicePreflight,
            inputs.privacyStorageParticipantAuthority,
            inputs.rioCaptureAuthority,
            hasher,
        )
    }

    private fun requireChain(
        binary: JsonObject,
        sourceManifestSha: String,
        sourcePatchSha: String,
        baseCommitSha: String,
        buildConfigSha: String,
        apkSha: String,
    ) {
        require(binary.string("protocol_sha256") == H2ConfirmationContract.PROTOCOL_PREREG_SHA256)
        require(binary.string("source_manifest_sha256") == sourceManifestSha)
        require(binary.string("source_patch_sha256") == sourcePatchSha)
        require(binary.string("base_commit_sha") == baseCommitSha)
        require(binary.string("build_config_sha256") == buildConfigSha)
        require(binary.string("apk_sha256") == apkSha)
    }

    private fun requireBuildConfig(config: String, name: String, value: String) {
        val quoted = "public static final String $name = \"$value\";"
        val boolean = "public static final boolean $name = $value;"
        require(quoted in config || boolean in config) { "H2 BuildConfig $name is not bound" }
    }

    private fun expectedAttempts(state: H2RunState): List<ExpectedAttempt> {
        val failure = state.failures.singleOrNull()
        failure?.let {
            require(it.captureAttempt == 1 && it.code.retryEligibleBeforePublication)
            require(state.replacementUsedBlockId == it.blockId)
        }
        return H2ConfirmationContract.blocks.flatMap { block ->
            if (failure?.blockId == block.id) {
                listOf(ExpectedAttempt(block.id, 1, true, failure.code), ExpectedAttempt(block.id, 2, false, null))
            } else {
                listOf(ExpectedAttempt(block.id, 1, false, null))
            }
        }
    }

    private fun verifyManifest(directory: File, manifestName: String, attempt: ExpectedAttempt): ByteArray {
        directory.listFiles().orEmpty().forEach(::refuseLink)
        val manifestFile = File(directory, manifestName)
        require(manifestFile.isFile)
        val bytes = manifestFile.readBytes()
        val records = bytes.decodeToString().lineSequence().filter(String::isNotEmpty).map { line ->
            val fields = line.split('\t')
            require(fields.size == 3)
            StreamRecord(fields[0], fields[1].toLong(), fields[2])
        }.toList()
        val names = records.map(StreamRecord::name)
        require(names == names.distinct())
        if (!attempt.terminated) require(names == H2StreamingBlockWriter.REQUIRED_STREAMS.toList())
        else require(names.all { it in H2StreamingBlockWriter.REQUIRED_STREAMS })
        records.forEach { record ->
            require(record.sha256.matches(SHA_REGEX))
            val file = File(directory, record.name)
            require(file.isFile && file.length() == record.length && sha256(file.readBytes()) == record.sha256)
        }
        val permitted = names.toSet() + manifestName + if (attempt.terminated) setOf(FAILURE_CODE_FILE) else emptySet()
        require(directory.listFiles().orEmpty().map { it.name }.toSet() == permitted)
        if (attempt.terminated) {
            val code = File(directory, FAILURE_CODE_FILE).readText().trim()
            require(code == requireNotNull(attempt.failureCode).name)
        }
        return bytes
    }

    private fun parseEvents(bytes: ByteArray): List<JsonObject> =
        bytes.decodeToString().lineSequence().filter(String::isNotBlank).map { line ->
            json.parseToJsonElement(line).jsonObject
        }.toList()

    private fun verifyEvents(attempt: ExpectedAttempt, events: List<JsonObject>, runIdentity: String): String {
        events.forEachIndexed { index, event ->
            require(event.long("sequence") == index.toLong()) { "H2 attempt event sequence is not contiguous from zero" }
            require(event.string("block_id") == attempt.blockId)
            require(event.string("run_identity_sha256") == runIdentity)
        }
        verifyEventInventory(attempt, events)
        if (attempt.terminated) {
            val terminal = events.lastOrNull()?.takeIf {
                it.string("event_kind") == H2ControllerEventKind.TERMINAL_FAILURE.name
            }
            if (attempt.failureCode !in TERMINAL_EVENT_MAY_BE_ABSENT) {
                requireNotNull(terminal) { "H2 terminated attempt has no terminal event" }
            }
            terminal?.let { require(it.string("failure_code") == requireNotNull(attempt.failureCode).name) }
            require(events.dropLast(if (terminal == null) 0 else 1).none {
                it.string("event_kind") == H2ControllerEventKind.TERMINAL_FAILURE.name
            })
        } else {
            require(events.none { it.string("event_kind") == H2ControllerEventKind.TERMINAL_FAILURE.name })
        }
        val mappingEvent = events.singleOrNull { it.string("event_kind") == H2ControllerEventKind.CLOCK_MAPPING.name }
            ?: if (attempt.terminated) return "NO_CLOCK_MAPPING" else error("H2 clock mapping missing")
        val mapping = mappingEvent.mapping()
        require(mappingEvent.long("monotonic_nanos") == mapping.cameraOriginNanos)
        val schedule = events.singleOrNull {
            it.string("event_kind") == H2ControllerEventKind.BLOCK_SCHEDULE_OPENED.name
        } ?: if (attempt.terminated) null else error("H2 block schedule event missing")
        val visibility = schedule?.let {
            require(it.long("monotonic_nanos") == mapping.cameraOriginNanos)
            H2SurfaceVisibilityMode.valueOf(it.string("participant_visibility")).also { mode ->
                require(mode == H2SurfaceVisibilityMode.NEITHER || mode == H2SurfaceVisibilityMode.PRIMARY_ONLY)
            }
        }
        verifyEventTimeline(events, mapping)
        verifyEventClocks(attempt, events, mapping)
        if (!attempt.terminated) verifyCueSchedule(attempt, events, mapping)
        if (!attempt.terminated && attempt.blockId == H2ConfirmationContract.blocks.first().id) {
            verifyReadinessContinuity(events, mapping)
        }
        if (!attempt.terminated) requireNotNull(visibility)
        return mapping.canonical()
    }

    private fun verifyEventInventory(attempt: ExpectedAttempt, events: List<JsonObject>) {
        val block = H2ConfirmationContract.blocks.single { it.id == attempt.blockId }
        val envelopeKinds = setOf(
            H2ControllerEventKind.BLOCK_SCHEDULE_OPENED.name,
            H2ControllerEventKind.CLOCK_MAPPING.name,
            H2ControllerEventKind.CUE_ROUTE_ACTIVATED.name,
            H2ControllerEventKind.CUE_PRESENTATION_ACKNOWLEDGED.name,
        )
        val frameKinds = setOf(
            H2ControllerEventKind.FRAME_JOINED.name,
            H2ControllerEventKind.CALIBRATION_ONLY.name,
            H2ControllerEventKind.CANDIDATE_MISSING_OR_NOT_READY.name,
            H2ControllerEventKind.ROUTE_AVAILABLE.name,
            H2ControllerEventKind.PARTICIPANT_ROUTE_SELECTED.name,
            H2ControllerEventKind.COMPOSE_DRAW_ACKNOWLEDGED.name,
            "VISIBLE_OUTPUT",
        )
        val allowed = if (block.kind == H2BlockKind.STARTUP_READINESS) {
            envelopeKinds + H2ControllerEventKind.READINESS_OBSERVATION.name
        } else {
            envelopeKinds + frameKinds
        } + if (attempt.terminated) setOf(H2ControllerEventKind.TERMINAL_FAILURE.name) else emptySet()
        require(events.all { it.string("event_kind") in allowed }) { "H2 event kind is outside the closed inventory" }
        val joinedIdentities = events.filter {
            it.string("event_kind") == H2ControllerEventKind.FRAME_JOINED.name
        }.map { it.long("frame_index") to it.long("take_elapsed_millis") }.toSet()
        events.filter { it.string("event_kind") in frameKinds }.forEach { event ->
            val identity = requireNotNull(event.optionalLong("frame_index")) to
                requireNotNull(event.optionalLong("take_elapsed_millis"))
            require(identity in joinedIdentities) { "H2 frame-scoped event has no joined frame" }
        }
        events.forEach(::requireExactEventKeys)
    }

    private fun requireExactEventKeys(event: JsonObject) {
        val base = setOf("sequence", "block_id", "event_kind", "monotonic_nanos", "run_identity_sha256")
        val frame = setOf("frame_index", "take_elapsed_millis")
        val expected = base + when (event.string("event_kind")) {
            H2ControllerEventKind.READINESS_OBSERVATION.name ->
                setOf("primary_availability", "comparator_availability")
            H2ControllerEventKind.BLOCK_SCHEDULE_OPENED.name -> setOf("participant_visibility")
            H2ControllerEventKind.CLOCK_MAPPING.name -> setOf(
                "clock_id",
                "camera_origin_nanos",
                "cue_origin_nanos",
                "action_observer_origin_nanos",
                "visible_output_origin_nanos",
                "max_mapping_uncertainty_nanos",
            )
            H2ControllerEventKind.FRAME_JOINED.name,
            H2ControllerEventKind.CALIBRATION_ONLY.name,
            H2ControllerEventKind.CANDIDATE_MISSING_OR_NOT_READY.name,
            H2ControllerEventKind.ROUTE_AVAILABLE.name,
            -> frame
            H2ControllerEventKind.PARTICIPANT_ROUTE_SELECTED.name,
            H2ControllerEventKind.COMPOSE_DRAW_ACKNOWLEDGED.name,
            -> frame + "participant_visibility"
            H2ControllerEventKind.CUE_ROUTE_ACTIVATED.name,
            H2ControllerEventKind.CUE_PRESENTATION_ACKNOWLEDGED.name,
            -> setOf("take_elapsed_millis", "cue_segment")
            H2ControllerEventKind.TERMINAL_FAILURE.name -> {
                val hasFrame = "frame_index" in event
                val hasElapsed = "take_elapsed_millis" in event
                require(hasFrame == hasElapsed) {
                    "H2 terminal frame identity must contain both frame_index and take_elapsed_millis"
                }
                setOf("failure_code") + if (hasFrame) frame else emptySet()
            }
            "VISIBLE_OUTPUT" -> frame + setOf("candidate_role", "surface", "state")
            else -> error("H2 event kind escaped its whitelist")
        }
        require(event.keys == expected) { "H2 event keys do not match the closed schema" }
    }

    private fun verifyEventTimeline(events: List<JsonObject>, mapping: H2ClockMapping) {
        var lastTakeNanos = -1L
        events.forEach { event ->
            val kind = event.string("event_kind")
            val mapped = when (kind) {
                H2ControllerEventKind.READINESS_OBSERVATION.name ->
                    event.long("monotonic_nanos") - mapping.cueOriginNanos
                H2ControllerEventKind.CUE_PRESENTATION_ACKNOWLEDGED.name ->
                    requireNotNull(event.optionalLong("take_elapsed_millis")) * NANOS_PER_MILLI
                H2ControllerEventKind.COMPOSE_DRAW_ACKNOWLEDGED.name ->
                    requireNotNull(event.optionalLong("take_elapsed_millis")) * NANOS_PER_MILLI
                else -> event.optionalLong("take_elapsed_millis")?.times(NANOS_PER_MILLI)
            }
            mapped?.let {
                require(it >= lastTakeNanos) { "H2 event timeline is reordered" }
                lastTakeNanos = it
            }
        }
    }

    private fun verifyPublishedScientificEvidence(
        directory: File,
        attempt: ExpectedAttempt,
        events: List<JsonObject>,
        state: H2RunState,
    ) {
        val block = H2ConfirmationContract.blocks.single { it.id == attempt.blockId }
        val receipt = state.receipts.single { it.blockId == attempt.blockId }
        val mapping = events.single { it.string("event_kind") == H2ControllerEventKind.CLOCK_MAPPING.name }.mapping()
        val visibility = H2SurfaceVisibilityMode.valueOf(
            events.single { it.string("event_kind") == H2ControllerEventKind.BLOCK_SCHEDULE_OPENED.name }
                .string("participant_visibility"),
        )
        require(receipt.observableSeconds == block.minimumObservableSeconds)
        require(events.none {
            it.string("event_kind") == H2ControllerEventKind.CANDIDATE_MISSING_OR_NOT_READY.name ||
                (it.string("event_kind") == "VISIBLE_OUTPUT" && it.string("state") == "OUTPUT_NOT_VISIBLE")
        })
        val joined = events.filter { it.string("event_kind") == H2ControllerEventKind.FRAME_JOINED.name }
        val visible = events.filter { it.string("event_kind") == "VISIBLE_OUTPUT" }
        if (block.kind == H2BlockKind.STARTUP_READINESS) {
            require(joined.isEmpty() && visible.isEmpty())
            require(receipt.primaryVisibleEventCount == 0 && receipt.comparatorVisibleEventCount == 0)
        } else {
            val end = block.totalDurationSeconds * 1_000L
            require(joined.isNotEmpty() && joined.last().long("take_elapsed_millis") == end)
            val observable = joined.filter { it.long("take_elapsed_millis") > 3_000 }
            val calibration = joined.filter { it.long("take_elapsed_millis") in 1_501..3_000 }
            require(calibration.size >= 15 && calibration.last().long("take_elapsed_millis") == 3_000L)
            val comparatorCalibration = joined.filter { it.long("take_elapsed_millis") in 250..1_250 }
            require(comparatorCalibration.size >= 15 && comparatorCalibration.last().long("take_elapsed_millis") == 1_250L)
            require(observable.isNotEmpty() && observable.first().long("take_elapsed_millis") <= 3_250)
            observable.zipWithNext().forEach { (left, right) ->
                require(right.long("take_elapsed_millis") - left.long("take_elapsed_millis") in 1..250)
            }
            val visibleByFrame = visible.groupBy { it.long("frame_index") }
            require(visibleByFrame.keys == observable.map { it.long("frame_index") }.toSet())
            visibleByFrame.values.forEach { pair ->
                require(pair.size == 2)
                require(pair.count {
                    it.string("candidate_role") == "PRIMARY" &&
                        it.string("surface") == "H2_PRIMARY_C3B_AVATAR_RENDER"
                } == 1)
                require(pair.count {
                    it.string("candidate_role") == "COMPARATOR" &&
                        it.string("surface") == "H2_SECONDARY_C2_PLUS_C1_AVATAR_RENDER"
                } == 1)
                require(pair.all { it.string("state") == "VISIBLE_ON" || it.string("state") == "VISIBLE_OFF" })
            }
            val primaryCount = visible.count { it.string("candidate_role") == "PRIMARY" }
            val comparatorCount = visible.count { it.string("candidate_role") == "COMPARATOR" }
            require(primaryCount == receipt.primaryVisibleEventCount)
            require(comparatorCount == receipt.comparatorVisibleEventCount)
        }
        val expectedFrames = joined.map { it.long("frame_index") to it.long("take_elapsed_millis") }
        val camera = parseFramed(File(directory, "camera.rgba"))
        val action = parseFramed(File(directory, "participant-action.rgba"))
        val primary = parseFramed(File(directory, "avatar-a.rgba"))
        val comparator = parseFramed(File(directory, "avatar-b.rgba"))
        require(camera.map(FrameHeader::identity) == expectedFrames)
        require(action.map(FrameHeader::identity) == expectedFrames)
        require(primary.map(FrameHeader::identity) == expectedFrames)
        require(comparator.map(FrameHeader::identity) == expectedFrames)
        require(camera.all(FrameHeader::present) && action.all(FrameHeader::present))
        require(camera.zip(action).all { (left, right) ->
            left.payloadLength > 0 && left.payloadLength == right.payloadLength && left.payloadSha256 == right.payloadSha256
        })
        val visibleFrames = visible.map { it.long("frame_index") }.toSet()
        require(primary.all { it.present == (it.frameIndex in visibleFrames) })
        require(comparator.all { it.present == (it.frameIndex in visibleFrames) })
        require(primary.filter(FrameHeader::present).all { it.payloadLength == AVATAR_RGBA_BYTES })
        require(comparator.filter(FrameHeader::present).all { it.payloadLength == AVATAR_RGBA_BYTES })
        val primaryByFrame = primary.associateBy(FrameHeader::frameIndex)
        val comparatorByFrame = comparator.associateBy(FrameHeader::frameIndex)
        visible.forEach { event ->
            val role = H2CandidateRole.valueOf(event.string("candidate_role"))
            val expected = checkNotNull(
                H2BlindAvatarRenderer(role).render(
                    route = H2AvatarRouteState(
                        active = event.string("state") == "VISIBLE_ON",
                        available = true,
                    ),
                    clock = mapping,
                    eventSequence = event.long("sequence"),
                    blockId = attempt.blockId,
                    monotonicNanos = event.long("monotonic_nanos"),
                ),
            )
            val retained = if (role == H2CandidateRole.PRIMARY) {
                checkNotNull(primaryByFrame[event.long("frame_index")])
            } else {
                checkNotNull(comparatorByFrame[event.long("frame_index")])
            }
            require(retained.payloadSha256 == sha256(expected.rgba)) {
                "H2 retained avatar pixels do not match their visible-output state"
            }
        }
        joined.forEach { frame ->
            val frameIndex = frame.long("frame_index")
            val elapsed = frame.long("take_elapsed_millis")
            val kinds = events.filter { it.optionalLong("frame_index") == frameIndex }.map { it.string("event_kind") }
            if (elapsed <= 3_000) {
                require(kinds == listOf(
                    H2ControllerEventKind.FRAME_JOINED.name,
                    H2ControllerEventKind.CALIBRATION_ONLY.name,
                ))
            } else {
                val expectedKinds = mutableListOf(
                        H2ControllerEventKind.FRAME_JOINED.name,
                        H2ControllerEventKind.ROUTE_AVAILABLE.name,
                        "VISIBLE_OUTPUT",
                        "VISIBLE_OUTPUT",
                        H2ControllerEventKind.PARTICIPANT_ROUTE_SELECTED.name,
                )
                if (visibility == H2SurfaceVisibilityMode.PRIMARY_ONLY) {
                    expectedKinds += H2ControllerEventKind.COMPOSE_DRAW_ACKNOWLEDGED.name
                }
                require(kinds == expectedKinds) { "H2 per-frame route/render/draw event order is not exact" }
                val scoped = events.filter { it.optionalLong("frame_index") == frameIndex }
                scoped.filter {
                    it.string("event_kind") in setOf(
                        H2ControllerEventKind.FRAME_JOINED.name,
                        H2ControllerEventKind.ROUTE_AVAILABLE.name,
                        H2ControllerEventKind.PARTICIPANT_ROUTE_SELECTED.name,
                    )
                }.forEach {
                    require(it.long("monotonic_nanos") == mapping.cameraOriginNanos + elapsed * NANOS_PER_MILLI)
                }
                val participant = scoped.single {
                    it.string("event_kind") == H2ControllerEventKind.PARTICIPANT_ROUTE_SELECTED.name
                }
                require(participant.string("participant_visibility") == visibility.name)
            }
        }
    }

    private fun parseFramed(file: File): List<FrameHeader> {
        val bytes = file.readBytes()
        val result = mutableListOf<FrameHeader>()
        var offset = 0
        while (offset < bytes.size) {
            require(bytes.size - offset >= 20)
            val frame = bytes.readLongLe(offset)
            val elapsed = bytes.readLongLe(offset + 8)
            val length = bytes.readIntLe(offset + 16)
            require(length >= -1)
            offset += 20
            if (length >= 0) {
                require(bytes.size - offset >= length)
                val payloadSha = sha256(bytes.copyOfRange(offset, offset + length))
                offset += length
                result += FrameHeader(frame, elapsed, true, length, payloadSha)
            } else {
                result += FrameHeader(frame, elapsed, false, length, null)
            }
        }
        require(offset == bytes.size)
        return result
    }

    private fun verifyEventClocks(attempt: ExpectedAttempt, events: List<JsonObject>, mapping: H2ClockMapping) {
        var lastFrame = -1L
        var lastElapsed = -1L
        val blockEnd = H2ConfirmationContract.blocks.single { it.id == attempt.blockId }.totalDurationSeconds * 1_000L
        events.forEach { event ->
            val elapsed = event.optionalLong("take_elapsed_millis")
            elapsed?.let { require(it in 0..blockEnd) }
            when (event.string("event_kind")) {
                H2ControllerEventKind.FRAME_JOINED.name -> {
                    val frame = event.long("frame_index")
                    require(frame > lastFrame && requireNotNull(elapsed) > lastElapsed)
                    require(event.long("monotonic_nanos") == mapping.cameraOriginNanos + elapsed * NANOS_PER_MILLI)
                    lastFrame = frame
                    lastElapsed = elapsed
                }
                H2ControllerEventKind.CALIBRATION_ONLY.name,
                H2ControllerEventKind.CANDIDATE_MISSING_OR_NOT_READY.name,
                H2ControllerEventKind.ROUTE_AVAILABLE.name,
                H2ControllerEventKind.PARTICIPANT_ROUTE_SELECTED.name,
                -> require(
                    event.long("monotonic_nanos") == mapping.cameraOriginNanos + requireNotNull(elapsed) * NANOS_PER_MILLI,
                )
                "VISIBLE_OUTPUT" -> require(
                    event.long("monotonic_nanos") == mapping.visibleOutputOriginNanos + requireNotNull(elapsed) * NANOS_PER_MILLI,
                )
                H2ControllerEventKind.CUE_ROUTE_ACTIVATED.name -> require(
                    event.long("monotonic_nanos") == mapping.cueOriginNanos + requireNotNull(elapsed) * NANOS_PER_MILLI,
                )
                H2ControllerEventKind.COMPOSE_DRAW_ACKNOWLEDGED.name -> require(
                    event.long("monotonic_nanos") >= mapping.visibleOutputOriginNanos + requireNotNull(elapsed) * NANOS_PER_MILLI &&
                        event.long("monotonic_nanos") <= mapping.visibleOutputOriginNanos + blockEnd * NANOS_PER_MILLI,
                )
            }
        }
    }

    private fun verifyCueSchedule(attempt: ExpectedAttempt, events: List<JsonObject>, mapping: H2ClockMapping) {
        val cueEvents = events.filter {
            it.string("event_kind") == H2ControllerEventKind.CUE_ROUTE_ACTIVATED.name ||
                it.string("event_kind") == H2ControllerEventKind.CUE_PRESENTATION_ACKNOWLEDGED.name
        }
        val expected = H2CueContract.segmentsFor(attempt.blockId)
        require(cueEvents.size == expected.size * 2)
        expected.forEachIndexed { index, segment ->
            val route = cueEvents[index * 2]
            val ack = cueEvents[index * 2 + 1]
            require(route.string("event_kind") == H2ControllerEventKind.CUE_ROUTE_ACTIVATED.name)
            require(ack.string("event_kind") == H2ControllerEventKind.CUE_PRESENTATION_ACKNOWLEDGED.name)
            require(route.string("cue_segment") == segment.name.name && ack.string("cue_segment") == segment.name.name)
            require(route.long("take_elapsed_millis") == segment.startMillis)
            require(route.long("monotonic_nanos") == mapping.cueOriginNanos + segment.startMillis * NANOS_PER_MILLI)
            require(ack.long("monotonic_nanos") in route.long("monotonic_nanos")..(
                mapping.cueOriginNanos + segment.endMillis * NANOS_PER_MILLI
            ))
        }
    }

    private fun verifyReadinessContinuity(events: List<JsonObject>, mapping: H2ClockMapping) {
        val observations = events.filter { it.string("event_kind") == H2ControllerEventKind.READINESS_OBSERVATION.name }
        require(observations.isNotEmpty())
        require(observations.first().long("monotonic_nanos") == mapping.cueOriginNanos)
        require(observations.last().long("monotonic_nanos") == mapping.cueOriginNanos + 15_000L * NANOS_PER_MILLI)
        observations.zipWithNext().forEach { (left, right) ->
            require(right.long("monotonic_nanos") - left.long("monotonic_nanos") in 1..250L * NANOS_PER_MILLI)
        }
        observations.forEach {
            require(it.string("primary_availability") == H2ProviderRouteAvailability.AVAILABLE.name)
            require(it.string("comparator_availability") == H2ProviderRouteAvailability.AVAILABLE.name)
        }
    }

    private data class ExpectedAttempt(
        val blockId: String,
        val attempt: Int,
        val terminated: Boolean,
        val failureCode: H2FailureCode?,
    ) {
        val directoryName: String = if (terminated) ".$blockId.$attempt.terminated" else "$blockId.$attempt"
        val identityLine: String = "$blockId\t$attempt\t${if (terminated) "TERMINATED" else "PUBLISHED"}\t"
    }

    private data class StreamRecord(val name: String, val length: Long, val sha256: String)
    private data class FrameHeader(
        val frameIndex: Long,
        val elapsedMillis: Long,
        val present: Boolean,
        val payloadLength: Int,
        val payloadSha256: String?,
    ) {
        val identity: Pair<Long, Long> = frameIndex to elapsedMillis
    }

    private fun JsonObject.string(name: String): String = checkNotNull(this[name]).jsonPrimitive.content
    private fun JsonObject.obj(name: String): JsonObject = checkNotNull(this[name]).jsonObject
    private fun JsonObject.long(name: String): Long = string(name).toLong()
    private fun JsonObject.optionalLong(name: String): Long? = this[name]?.jsonPrimitive?.content?.toLong()
    private fun JsonObject.mapping(): H2ClockMapping = H2ClockMapping(
        clockId = string("clock_id"),
        cameraOriginNanos = long("camera_origin_nanos"),
        cueOriginNanos = long("cue_origin_nanos"),
        actionObserverOriginNanos = long("action_observer_origin_nanos"),
        visibleOutputOriginNanos = long("visible_output_origin_nanos"),
        maxMappingUncertaintyNanos = long("max_mapping_uncertainty_nanos"),
    )

    private fun H2ClockMapping.canonical(): String =
        "$clockId\t$cameraOriginNanos\t$cueOriginNanos\t$actionObserverOriginNanos\t" +
            "$visibleOutputOriginNanos\t$maxMappingUncertaintyNanos"

    private companion object {
        val TERMINAL_EVENT_MAY_BE_ABSENT: Set<H2FailureCode> = setOf(
            H2FailureCode.UNPUBLISHED_EVENT_LOG_WRITE_FAILURE,
            H2FailureCode.UNPUBLISHED_HASH_OR_LENGTH_MISMATCH,
            H2FailureCode.UNPUBLISHED_ATOMIC_PUBLICATION_PRECONDITION_FAILURE,
        )
        const val EVENT_STREAM = "controller-events.jsonl"
        const val FAILURE_CODE_FILE = "failure-code.txt"
        const val NANOS_PER_MILLI = 1_000_000L
        const val AVATAR_RGBA_BYTES = 64 * 64 * 4
        val SHA_REGEX = Regex("[0-9a-f]{64}")
    }

    private fun parseObject(bytes: ByteArray): JsonObject = json.parseToJsonElement(bytes.decodeToString()).jsonObject
}

private fun ByteArray.readLongLe(offset: Int): Long = (0 until Long.SIZE_BYTES).fold(0L) { value, index ->
    value or ((this[offset + index].toLong() and 0xff) shl (index * Byte.SIZE_BITS))
}

private fun ByteArray.readIntLe(offset: Int): Int = (0 until Int.SIZE_BYTES).fold(0) { value, index ->
    value or ((this[offset + index].toInt() and 0xff) shl (index * Byte.SIZE_BITS))
}

private fun refuseLink(file: File) {
    require(!Files.isSymbolicLink(file.toPath())) { "H2 finalized evidence cannot contain symbolic links" }
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
