package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class H2ConfirmationContractTest {
    @Test
    fun exactPreregisteredInventoryIsBoundWithoutHistoricalProtocolReuse() {
        val blocks = H2ConfirmationContract.blocks

        assertEquals("DK_CHEEK_S02V17_H2_SCOREFREE_PROTOCOL_PREREG_V2", H2ConfirmationContract.PROTOCOL_ID)
        assertEquals(22, blocks.size)
        assertEquals(578, blocks.sumOf(H2ConfirmationBlock::totalDurationSeconds))
        assertEquals(15, blocks.first().minimumObservableSeconds)
        assertEquals(0, blocks.first().calibrationPreludeSeconds)
        assertEquals(21, blocks.drop(1).count { it.calibrationPreludeSeconds == 3 })
        assertEquals(listOf(63, 123, 63), blocks.filter { it.kind == H2BlockKind.ORDINARY_USE }.map { it.totalDurationSeconds })
        assertEquals("B01_STARTUP_NEUTRAL_READINESS", blocks.first().id)
        assertEquals("B22_CLOSING_NEUTRAL_READY", blocks.last().id)
        assertEquals(10, blocks.count { it.kind == H2BlockKind.POSITIVE })
        assertEquals(20, blocks.filter { it.kind == H2BlockKind.POSITIVE }.sumOf { it.cycleCount })
        assertEquals(8, blocks.count {
            it.kind == H2BlockKind.PRODUCT_JUDGMENT_CONTROL ||
                it.kind == H2BlockKind.DIAGNOSTIC_CONTROL
        })
        assertEquals(
            listOf("B02_OPENING_NEUTRAL_READY", "B10_NATURAL_SPEECH", "B22_CLOSING_NEUTRAL_READY"),
            blocks.filter { it.kind == H2BlockKind.ORDINARY_USE }.map(H2ConfirmationBlock::id),
        )
        assertEquals(22, blocks.map(H2ConfirmationBlock::id).distinct().size)
    }

    @Test
    fun frozenCandidatesRemainSeparateDefaultOffAndUnableToOverwriteCanonical52() {
        assertEquals("research:c3b-static-cheek-patch/v1", H2ConfirmationContract.primary.producerId)
        assertEquals(
            "research:c2-lip-gated/v1+c1-frozen-contour/v1",
            H2ConfirmationContract.comparator.producerId,
        )
        assertEquals("UNRESOLVED", H2ConfirmationContract.primary.semanticState)
        assertFalse(H2ConfirmationContract.primary.ordinaryProductRouteEnabled)
        assertFalse(H2ConfirmationContract.primary.canonical52OverwriteAllowed)
        assertFalse(H2ConfirmationContract.comparator.ordinaryProductRouteEnabled)
        assertFalse(H2ConfirmationContract.comparator.canonical52OverwriteAllowed)
        assertEquals(0.5, H2ConfirmationContract.primary.policy.onThreshold)
        assertEquals(0.5, H2ConfirmationContract.primary.policy.offThreshold)
        assertEquals(0, H2ConfirmationContract.primary.policy.activationDwellMillis)
        assertEquals(0.05, H2ConfirmationContract.comparator.policy.onThreshold)
        assertEquals(0.04000000000000001, H2ConfirmationContract.comparator.policy.offThreshold)
        assertEquals(350, H2ConfirmationContract.comparator.policy.activationDwellMillis)
        assertEquals(200, H2ConfirmationContract.comparator.policy.releaseDwellMillis)
        assertEquals(
            "05e0d942c146d595982c8f428471dbe54eccebe690ec193c9c1825fd64928034",
            H2ConfirmationContract.primary.policy.sha256,
        )
    }

    @Test
    fun startupReadinessAcceptsExactTwoHundredFiftyMillisecondCoverage() {
        val complete = observeAvailableReadinessAt250MillisCadence()
        assertEquals(H2ReadinessPhase.READY, complete.phase)
        assertEquals(15_000_000_000, complete.readyMonotonicNanos)
        assertEquals(true, complete.fullIntervalAvailable)
    }

    @Test
    fun startupAndFifteenSecondOnlyObservationsCannotManufactureCoverage() {
        val atLaunch = availableReadinessAtLaunch()
        val sparse = observeH2Readiness(
            atLaunch,
            monotonicNanos = 15_000_000_000,
            primary = H2ProviderRouteAvailability.AVAILABLE,
            comparator = H2ProviderRouteAvailability.AVAILABLE,
        )
        assertEquals(H2ReadinessPhase.TIMED_OUT, sparse.phase)
        assertEquals(false, sparse.fullIntervalAvailable)
        assertNull(sparse.readyMonotonicNanos)
    }

    @Test
    fun startupReadinessRejectsEndpointOverrunByOneNanosecondOrOneMillisecond() {
        listOf(15_000_000_001L, 15_001_000_000L).forEach { endpoint ->
            var state = availableReadinessAtLaunch()
            for (millis in 250L until 15_000L step 250L) {
                state = observeH2Readiness(
                    state,
                    millis * 1_000_000L,
                    H2ProviderRouteAvailability.AVAILABLE,
                    H2ProviderRouteAvailability.AVAILABLE,
                )
            }
            state = observeH2Readiness(
                state,
                endpoint,
                H2ProviderRouteAvailability.AVAILABLE,
                H2ProviderRouteAvailability.AVAILABLE,
            )
            assertEquals(H2ReadinessPhase.TIMED_OUT, state.phase)
            assertNull(state.readyMonotonicNanos)
        }
    }

    @Test
    fun startupReadinessRejectsAContinuousGapOfTwoHundredFiftyOneMilliseconds() {
        var state = availableReadinessAtLaunch()
        state = observeH2Readiness(
            state,
            monotonicNanos = 250_000_000,
            primary = H2ProviderRouteAvailability.AVAILABLE,
            comparator = H2ProviderRouteAvailability.AVAILABLE,
        )
        state = observeH2Readiness(
            state,
            monotonicNanos = 501_000_000,
            primary = H2ProviderRouteAvailability.AVAILABLE,
            comparator = H2ProviderRouteAvailability.AVAILABLE,
        )
        for (millis in 751L..15_001L step 250L) {
            state = observeH2Readiness(
                state,
                monotonicNanos = millis * 1_000_000L,
                primary = H2ProviderRouteAvailability.AVAILABLE,
                comparator = H2ProviderRouteAvailability.AVAILABLE,
            )
        }
        assertEquals(H2ReadinessPhase.TIMED_OUT, state.phase)
        assertEquals(false, state.fullIntervalAvailable)
        assertNull(state.readyMonotonicNanos)
    }

    @Test
    fun startupLateAvailabilityDropAndRegainRemainTimedOut() {
        val start = H2ReadinessState(launchMonotonicNanos = 0, lastMonotonicNanos = 0)
        val late = observeH2Readiness(
            start,
            monotonicNanos = 1,
            primary = H2ProviderRouteAvailability.AVAILABLE,
            comparator = H2ProviderRouteAvailability.AVAILABLE,
        )
        val timedOut = observeH2Readiness(
            late,
            monotonicNanos = 15_000_000_000,
            primary = H2ProviderRouteAvailability.AVAILABLE,
            comparator = H2ProviderRouteAvailability.AVAILABLE,
        )
        assertEquals(H2ReadinessPhase.TIMED_OUT, timedOut.phase)
        assertNull(timedOut.readyMonotonicNanos)

        val atLaunch = observeH2Readiness(
            start,
            monotonicNanos = 0,
            primary = H2ProviderRouteAvailability.AVAILABLE,
            comparator = H2ProviderRouteAvailability.AVAILABLE,
        )
        val dropped = observeH2Readiness(
            atLaunch,
            monotonicNanos = 7_000_000_000,
            primary = H2ProviderRouteAvailability.UNAVAILABLE,
            comparator = H2ProviderRouteAvailability.AVAILABLE,
        )
        val regained = observeH2Readiness(
            dropped,
            monotonicNanos = 8_000_000_000,
            primary = H2ProviderRouteAvailability.AVAILABLE,
            comparator = H2ProviderRouteAvailability.AVAILABLE,
        )
        val failedAfterRegain = observeH2Readiness(
            regained,
            monotonicNanos = 15_000_000_000,
            primary = H2ProviderRouteAvailability.AVAILABLE,
            comparator = H2ProviderRouteAvailability.AVAILABLE,
        )
        assertEquals(H2ReadinessPhase.TIMED_OUT, failedAfterRegain.phase)
        assertEquals(false, failedAfterRegain.fullIntervalAvailable)
    }

    private fun availableReadinessAtLaunch(): H2ReadinessState = observeH2Readiness(
        H2ReadinessState(launchMonotonicNanos = 0, lastMonotonicNanos = 0),
        monotonicNanos = 0,
        primary = H2ProviderRouteAvailability.AVAILABLE,
        comparator = H2ProviderRouteAvailability.AVAILABLE,
    )

    private fun observeAvailableReadinessAt250MillisCadence(): H2ReadinessState {
        var state = availableReadinessAtLaunch()
        for (millis in 250L..15_000L step 250L) {
            state = observeH2Readiness(
                state,
                monotonicNanos = millis * 1_000_000L,
                primary = H2ProviderRouteAvailability.AVAILABLE,
                comparator = H2ProviderRouteAvailability.AVAILABLE,
            )
        }
        return state
    }

    @Test
    fun nonStartupBlocksCannotBeginBeforeBothCandidatesAreReady() {
        var state = freshState("1")
        state = beginH2Block(state, H2ConfirmationContract.blocks[0].id, providerPolicyRouteRenderAvailable = true)
        state = completeH2Block(state, receipt(state, observableSeconds = 15))

        assertFailsWith<IllegalArgumentException> {
            beginH2Block(state, H2ConfirmationContract.blocks[1].id, providerPolicyRouteRenderAvailable = false)
        }
        beginH2Block(state, H2ConfirmationContract.blocks[1].id, providerPolicyRouteRenderAvailable = true)
    }

    @Test
    fun ordinaryUseCannotPublishAnIncompleteObservableDenominator() {
        var state = freshState("2")
        state = beginH2Block(state, H2ConfirmationContract.blocks[0].id, providerPolicyRouteRenderAvailable = true)
        state = completeH2Block(state, receipt(state, 15))
        state = beginH2Block(state, H2ConfirmationContract.blocks[1].id, providerPolicyRouteRenderAvailable = true)

        assertFailsWith<IllegalArgumentException> {
            completeH2Block(state, receipt(state, 59))
        }
    }

    @Test
    fun primaryAndComparatorVisibleEventsCannotShareASurfaceIdentity() {
        H2VisibleOutputEvent(
            sequence = 0,
            blockId = H2ConfirmationContract.blocks.first().id,
            candidateRole = H2CandidateRole.PRIMARY,
            surface = H2VisibleSurface.H2_PRIMARY_C3B_AVATAR_RENDER,
            monotonicNanos = 1,
            state = H2VisibleOutputState.VISIBLE_OFF,
        )
        assertFailsWith<IllegalArgumentException> {
            H2VisibleOutputEvent(
                sequence = 0,
                blockId = H2ConfirmationContract.blocks.first().id,
                candidateRole = H2CandidateRole.COMPARATOR,
                surface = H2VisibleSurface.H2_PRIMARY_C3B_AVATAR_RENDER,
                monotonicNanos = 1,
                state = H2VisibleOutputState.VISIBLE_OFF,
            )
        }
    }

    @Test
    fun oneFrameCreatesTwoIsolatedBilateralAvatarCommands() {
        val commands = h2AvatarRenderCommands(
            frameIndex = 42,
            monotonicNanos = 99,
            primaryActive = true,
            comparatorActive = false,
        )

        assertEquals(2, commands.size)
        assertEquals(H2CandidateRole.PRIMARY, commands[0].candidateRole)
        assertEquals(H2VisibleSurface.H2_PRIMARY_C3B_AVATAR_RENDER, commands[0].surface)
        assertEquals(true, commands[0].bilateralPukuVisible)
        assertEquals(H2CandidateRole.COMPARATOR, commands[1].candidateRole)
        assertEquals(H2VisibleSurface.H2_SECONDARY_C2_PLUS_C1_AVATAR_RENDER, commands[1].surface)
        assertEquals(false, commands[1].bilateralPukuVisible)
        assertEquals(commands[0].frameIndex, commands[1].frameIndex)
        assertEquals(commands[0].monotonicNanos, commands[1].monotonicNanos)
    }

    @Test
    fun visibleOutputLedgerRejectsClockAndSequenceMutation() {
        val ledger = H2VisibleOutputLedger()
        ledger.append(event(0, 10))
        assertFailsWith<IllegalArgumentException> { ledger.append(event(2, 11)) }
        assertFailsWith<IllegalArgumentException> { ledger.append(event(1, 9)) }
        ledger.append(event(1, 10))
        assertEquals(2, ledger.events.size)
    }

    @Test
    fun globalMachineReplacementBudgetIsOneAndBehaviorNeverRetries() {
        var state = freshState("3")
        state = beginH2Block(state, H2ConfirmationContract.blocks[0].id, providerPolicyRouteRenderAvailable = true)
        val first = failH2Block(
            state,
            state.activeBlockId!!,
            H2FailureCode.UNPUBLISHED_CAMERA_STREAM_WRITE_FAILURE,
        )
        assertEquals(H2FailureDecision.RETRY_ONCE, first.second)
        state = beginH2Block(first.first, H2ConfirmationContract.blocks[0].id, providerPolicyRouteRenderAvailable = true)
        state = completeH2Block(state, receipt(state, 15))
        state = beginH2Block(state, H2ConfirmationContract.blocks[1].id, providerPolicyRouteRenderAvailable = true)
        val second = failH2Block(
            state,
            state.activeBlockId!!,
            H2FailureCode.UNPUBLISHED_EVENT_LOG_WRITE_FAILURE,
        )
        assertEquals(H2FailureDecision.TERMINATED, second.second)

        var behavior = freshState("4")
        behavior = beginH2Block(behavior, H2ConfirmationContract.blocks[0].id, true)
        val response = failH2Block(behavior, behavior.activeBlockId!!, H2FailureCode.INSTRUCTION_DEVIATION)
        assertEquals(H2FailureDecision.TERMINATED, response.second)
    }

    @Test
    fun allSevenHumanChoicesRemainFailClosedUntilExactReceiptsExist() {
        assertFailsWith<IllegalArgumentException> { H2HumanChoiceReceipts().requireAllClosed() }
        H2HumanChoiceReceipts(
            primaryUsefulResponseLineSha256 = "1".repeat(64),
            ordinaryUseAndTemporalRiskLinesSha256 = "2".repeat(64),
            actionSpecificProductJudgmentSha256 = "3".repeat(64),
            displayVisibilityAndRouteSemanticsSha256 = "4".repeat(64),
            participantSessionDeviceEnvironmentScopeSha256 = "5".repeat(64),
            privacyConsentRetentionDeletionViewersSha256 = "6".repeat(64),
            physicalPilotAndCaptureAuthoritiesSha256 = "7".repeat(64),
        ).requireAllClosed()
    }

    @Test
    fun privacyStorageConfigurationFailsClosedForAudioCloudOutputAndCapacity() {
        assertFailsWith<IllegalArgumentException> { privacy(output = false) }
        assertFailsWith<IllegalArgumentException> { privacy(audio = true) }
        assertFailsWith<IllegalArgumentException> { privacy(cloud = true) }
        assertFailsWith<IllegalArgumentException> { privacy(required = 99, worst = 100) }
        privacy()
    }

    @Test
    fun completeOneShotRunCanSealOnlyAfterAllTwentyTwoBlocks() {
        val state = completedState()
        val digests = verifiedDigests(state)
        val seal = buildH2PublicSafeSeal(
            state,
            authorityChain(),
            digests,
            missingExtraDuplicateMutationChecksPassed = true,
            sealedAtIso = "2026-09-03T00:00:00Z",
        )
        assertEquals(H2ConfirmationContract.PROTOCOL_PREREG_SHA256, seal.protocolSha256)
        assertEquals(H2PublicSafeSeal.SCORE_FREE_RETAINED_EVIDENCE_DISPOSITION, seal.disposition)
        val sealed = sealH2Run(state, seal)
        assertEquals(H2RunPhase.SEALED, sealed.phase)
        assertFailsWith<IllegalStateException> {
            beginH2Block(sealed, H2ConfirmationContract.blocks.first().id, false)
        }
    }

    @Test
    fun receiptOrderMutationIsRejectedBeforeTrustedStoreVerification() {
        val state = completedState()
        val reordered = state.receipts.toMutableList().also { receipts ->
            val first = receipts[0]
            receipts[0] = receipts[1]
            receipts[1] = first
        }
        assertFailsWith<IllegalArgumentException> { state.copy(receipts = reordered) }
    }

    @Test
    fun sealCannotAcceptCallerSuppliedProtocolOrDisposition() {
        val state = completedState()
        val digests = verifiedDigests(state)
        assertFailsWith<IllegalArgumentException> {
            unsafeSeal(state, digests, "WRONG_PROTOCOL", H2PublicSafeSeal.SCORE_FREE_RETAINED_EVIDENCE_DISPOSITION)
        }
        assertFailsWith<IllegalArgumentException> {
            unsafeSeal(state, digests, H2ConfirmationContract.PROTOCOL_ID, "EXPLORATION_SEALED")
        }
    }

    private fun receipt(state: H2RunState, observableSeconds: Int): H2BlockReceipt = H2BlockReceipt(
        blockId = state.activeBlockId!!,
        captureAttempt = state.activeAttempt!!,
        observableSeconds = observableSeconds,
        primaryVisibleEventCount = if (state.nextBlockIndex == 0) 0 else 1,
        comparatorVisibleEventCount = if (state.nextBlockIndex == 0) 0 else 1,
        retainedStreamManifestSha256 = "e".repeat(64),
        publishedAtomically = true,
    )

    private fun completedState(): H2RunState {
        var state = freshState("8")
        H2ConfirmationContract.blocks.forEach { block ->
            state = beginH2Block(state, block.id, providerPolicyRouteRenderAvailable = true)
            state = completeH2Block(state, receipt(state, block.minimumObservableSeconds))
        }
        return state
    }

    private fun verifiedDigests(state: H2RunState): H2VerifiedSealDigestBundle =
        H2VerifiedSealDigestBundle(
            state.runIdentitySha256,
            state.orderedBlockReceiptsCanonicalUtf8Lf(),
            TEST_SEAL_SHA256.digest(state.orderedBlockReceiptsCanonicalUtf8Lf().encodeToByteArray()),
            TEST_SEAL_SHA256.digest(EVENTS_BYTES),
            TEST_SEAL_SHA256.digest(CLOCK_BYTES),
            TEST_SEAL_SHA256.digest("streams\n".encodeToByteArray()),
        )

    private fun authorityChain(): H2SealAuthorityChain = H2SealAuthorityChain.fromRetainedBytes(
        "source-manifest".encodeToByteArray(),
        "source-patch".encodeToByteArray(),
        "apk".encodeToByteArray(),
        "build-config".encodeToByteArray(),
        "1".repeat(40),
        "binary-receipt".encodeToByteArray(),
        "device-preflight".encodeToByteArray(),
        "privacy-authority".encodeToByteArray(),
        "capture-authority".encodeToByteArray(),
        TEST_SEAL_SHA256,
    )

    private fun unsafeSeal(
        state: H2RunState,
        digests: H2VerifiedSealDigestBundle,
        protocolId: String,
        disposition: String,
    ): H2PublicSafeSeal = H2PublicSafeSeal(
        protocolId = protocolId,
        protocolSha256 = H2ConfirmationContract.PROTOCOL_PREREG_SHA256,
        runIdentitySha256 = state.runIdentitySha256,
        sourceManifestSha256 = "9".repeat(64),
        sourcePatchSha256 = "8".repeat(64),
        apkSha256 = "7".repeat(64),
        buildConfigSha256 = "6".repeat(64),
        baseCommitSha = "5".repeat(40),
        binaryReceiptSha256 = "a".repeat(64),
        devicePreflightSha256 = "e".repeat(64),
        privacyStorageParticipantAuthoritySha256 = "f".repeat(64),
        rioCaptureAuthoritySha256 = "1".repeat(64),
        orderedBlockReceiptsSha256 = digests.orderedBlockReceiptsSha256,
        controllerEventStreamsSha256 = digests.controllerEventStreamsSha256,
        clockMappingReceiptSha256 = digests.clockMappingReceiptSha256,
        retainedStreamManifestsSha256 = digests.retainedStreamManifestsSha256,
        failureLedger = state.failures,
        missingExtraDuplicateMutationChecksPassed = true,
        scoreOpened = false,
        terminalState = H2RunPhase.SEALED,
        disposition = disposition,
        sealedAtIso = "2026-09-03T00:00:00Z",
        blockCount = H2ConfirmationContract.EXPECTED_BLOCK_COUNT,
        orderedBlockReceiptsCanonicalUtf8Lf = digests.orderedBlockReceiptsCanonicalUtf8Lf,
    )

    private fun freshState(character: String): H2RunState = openPreparedH2Run(
        H2RunState(character.repeat(64)),
        exclusiveLeaseAcquired = true,
        noPriorOpenOrTerminalRun = true,
    )

    private fun event(sequence: Long, timestamp: Long): H2VisibleOutputEvent = H2VisibleOutputEvent(
        sequence = sequence,
        blockId = H2ConfirmationContract.blocks.first().id,
        candidateRole = H2CandidateRole.PRIMARY,
        surface = H2VisibleSurface.H2_PRIMARY_C3B_AVATAR_RENDER,
        monotonicNanos = timestamp,
        state = H2VisibleOutputState.VISIBLE_OFF,
    )

    private fun privacy(
        output: Boolean = true,
        audio: Boolean = false,
        cloud: Boolean = false,
        required: Long = 100,
        worst: Long = 100,
    ): H2PrivacyStoragePolicy = H2PrivacyStoragePolicy(
        consentReceiptSha256 = "d".repeat(64),
        physicalPilotAuthoritySha256 = "e".repeat(64),
        retentionDecisionId = "retain-until-reviewed",
        deletionOwnerId = "owner-role",
        outputCaptureEnabled = output,
        audioCaptureEnabled = audio,
        cloudTransferEnabled = cloud,
        requiredAvailableBytes = required,
        worstCaseBytes = worst,
    )

    private companion object {
        val EVENTS_BYTES: ByteArray = "events\n".encodeToByteArray()
        val CLOCK_BYTES: ByteArray = "clock\n".encodeToByteArray()
        val TEST_SEAL_SHA256: H2SealSha256 = H2SealSha256 { bytes ->
            when (bytes.decodeToString()) {
                "events\n" -> "8".repeat(64)
                "clock\n" -> "9".repeat(64)
                "mutated-events\n", "mutated-clock\n" -> "0".repeat(64)
                else -> "7".repeat(64)
            }
        }
    }
}
