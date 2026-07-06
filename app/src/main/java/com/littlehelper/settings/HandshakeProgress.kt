package com.littlehelper.settings

import com.littlehelper.shell.transport.ConnectFailureKind

enum class HandshakeStep {
    TOKEN,
    PAIRING,
    APPROVED,
    CONNECTED,
}

enum class HandshakeStepStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    WAITING_USER,
    SKIPPED,
}

data class HandshakeStepUi(
    val step: HandshakeStep,
    val status: HandshakeStepStatus,
    val hint: String? = null,
)

data class GatewayHandshakeProgress(
    val steps: List<HandshakeStepUi>,
    val deviceId: String? = null,
) {
    val pairingWaiting: Boolean
        get() = steps.any { it.step == HandshakeStep.PAIRING && it.status == HandshakeStepStatus.WAITING_USER }

    val allSucceeded: Boolean
        get() = steps.all {
            it.status == HandshakeStepStatus.DONE || it.status == HandshakeStepStatus.SKIPPED
        }
}

object HandshakeProgressMapper {

    fun onTestStarted(previous: GatewayHandshakeProgress?): GatewayHandshakeProgress {
        val base = previous ?: emptyProgress()
        return base.copy(
            steps = base.steps.map { step ->
                when (step.step) {
                    HandshakeStep.CONNECTED -> step.copy(
                        status = HandshakeStepStatus.RUNNING,
                        hint = null,
                    )
                    else -> step
                }
            },
        )
    }

    fun fromSuccess(
        previous: GatewayHandshakeProgress?,
        deviceId: String?,
    ): GatewayHandshakeProgress {
        val wasWaitingPairing = previous?.pairingWaiting == true
        return GatewayHandshakeProgress(
            steps = listOf(
                step(HandshakeStep.TOKEN, HandshakeStepStatus.DONE),
                pairingStepAfterSuccess(wasWaitingPairing),
                approvedStepAfterSuccess(wasWaitingPairing),
                step(HandshakeStep.CONNECTED, HandshakeStepStatus.DONE),
            ),
            deviceId = deviceId,
        )
    }

    fun fromFailure(
        previous: GatewayHandshakeProgress?,
        kind: ConnectFailureKind?,
        title: String?,
        detail: String?,
        deviceId: String?,
    ): GatewayHandshakeProgress {
        val wasWaitingPairing = previous?.pairingWaiting == true
        return when (kind) {
            ConnectFailureKind.BAD_SHARED_CREDENTIAL -> GatewayHandshakeProgress(
                steps = listOf(
                    step(
                        HandshakeStep.TOKEN,
                        HandshakeStepStatus.FAILED,
                        hint = joinHint(title, detail),
                    ),
                    pending(HandshakeStep.PAIRING),
                    pending(HandshakeStep.APPROVED),
                    pending(HandshakeStep.CONNECTED),
                ),
                deviceId = deviceId,
            )
            ConnectFailureKind.PAIRING_REQUIRED -> GatewayHandshakeProgress(
                steps = listOf(
                    step(HandshakeStep.TOKEN, HandshakeStepStatus.DONE),
                    step(
                        HandshakeStep.PAIRING,
                        HandshakeStepStatus.WAITING_USER,
                        hint = detail?.takeIf { it.isNotBlank() }
                            ?: "请在 Gateway Control UI → Devices 批准与本机一致的设备",
                    ),
                    pending(HandshakeStep.APPROVED),
                    pending(HandshakeStep.CONNECTED),
                ),
                deviceId = deviceId,
            )
            ConnectFailureKind.RATE_LIMITED -> GatewayHandshakeProgress(
                steps = listOf(
                    step(HandshakeStep.TOKEN, HandshakeStepStatus.DONE),
                    pairingStepAfterRateLimit(wasWaitingPairing),
                    approvedStepAfterRateLimit(wasWaitingPairing),
                    step(
                        HandshakeStep.CONNECTED,
                        HandshakeStepStatus.FAILED,
                        hint = joinHint(title, detail),
                    ),
                ),
                deviceId = deviceId,
            )
            ConnectFailureKind.STALE_DEVICE_BINDING,
            ConnectFailureKind.DEVICE_SIGNATURE,
            -> GatewayHandshakeProgress(
                steps = listOf(
                    step(HandshakeStep.TOKEN, HandshakeStepStatus.DONE),
                    step(
                        HandshakeStep.PAIRING,
                        HandshakeStepStatus.FAILED,
                        hint = joinHint(title, detail),
                    ),
                    pending(HandshakeStep.APPROVED),
                    pending(HandshakeStep.CONNECTED),
                ),
                deviceId = deviceId,
            )
            ConnectFailureKind.GATEWAY_STARTING,
            ConnectFailureKind.NETWORK,
            -> GatewayHandshakeProgress(
                steps = listOf(
                    retainOrPending(previous, HandshakeStep.TOKEN),
                    retainOrPending(previous, HandshakeStep.PAIRING),
                    retainOrPending(previous, HandshakeStep.APPROVED),
                    step(
                        HandshakeStep.CONNECTED,
                        HandshakeStepStatus.FAILED,
                        hint = joinHint(title, detail),
                    ),
                ),
                deviceId = deviceId,
            )
            else -> GatewayHandshakeProgress(
                steps = listOf(
                    retainOrPending(previous, HandshakeStep.TOKEN),
                    retainOrPending(previous, HandshakeStep.PAIRING),
                    retainOrPending(previous, HandshakeStep.APPROVED),
                    step(
                        HandshakeStep.CONNECTED,
                        HandshakeStepStatus.FAILED,
                        hint = joinHint(title, detail),
                    ),
                ),
                deviceId = deviceId,
            )
        }
    }

    private fun emptyProgress(): GatewayHandshakeProgress = GatewayHandshakeProgress(
        steps = listOf(
            pending(HandshakeStep.TOKEN),
            pending(HandshakeStep.PAIRING),
            pending(HandshakeStep.APPROVED),
            pending(HandshakeStep.CONNECTED),
        ),
    )

    private fun step(
        handshakeStep: HandshakeStep,
        status: HandshakeStepStatus,
        hint: String? = null,
    ): HandshakeStepUi = HandshakeStepUi(handshakeStep, status, hint)

    private fun pending(handshakeStep: HandshakeStep): HandshakeStepUi =
        step(handshakeStep, HandshakeStepStatus.PENDING)

    private fun pairingStepAfterSuccess(wasWaitingPairing: Boolean): HandshakeStepUi =
        if (wasWaitingPairing) {
            step(HandshakeStep.PAIRING, HandshakeStepStatus.DONE)
        } else {
            step(HandshakeStep.PAIRING, HandshakeStepStatus.SKIPPED)
        }

    private fun approvedStepAfterSuccess(wasWaitingPairing: Boolean): HandshakeStepUi =
        if (wasWaitingPairing) {
            step(HandshakeStep.APPROVED, HandshakeStepStatus.DONE)
        } else {
            step(HandshakeStep.APPROVED, HandshakeStepStatus.SKIPPED)
        }

    private fun pairingStepAfterRateLimit(wasWaitingPairing: Boolean): HandshakeStepUi =
        if (wasWaitingPairing) {
            step(HandshakeStep.PAIRING, HandshakeStepStatus.DONE)
        } else {
            step(HandshakeStep.PAIRING, HandshakeStepStatus.SKIPPED)
        }

    private fun approvedStepAfterRateLimit(wasWaitingPairing: Boolean): HandshakeStepUi =
        if (wasWaitingPairing) {
            step(HandshakeStep.APPROVED, HandshakeStepStatus.DONE)
        } else {
            step(HandshakeStep.APPROVED, HandshakeStepStatus.SKIPPED)
        }

    private fun retainOrPending(
        previous: GatewayHandshakeProgress?,
        handshakeStep: HandshakeStep,
    ): HandshakeStepUi {
        val prior = previous?.steps?.firstOrNull { it.step == handshakeStep }
        return when (prior?.status) {
            HandshakeStepStatus.DONE,
            HandshakeStepStatus.SKIPPED,
            HandshakeStepStatus.WAITING_USER,
            HandshakeStepStatus.FAILED,
            -> prior
            else -> pending(handshakeStep)
        }
    }

    private fun joinHint(title: String?, detail: String?): String? {
        val parts = listOfNotNull(
            title?.takeIf { it.isNotBlank() },
            detail?.takeIf { it.isNotBlank() },
        ).distinct()
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }
}
