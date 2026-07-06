package com.littlehelper.settings

import com.littlehelper.shell.transport.ConnectFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandshakeProgressMapperTest {

    @Test
    fun fromFailure_badCredential_failsTokenOnly() {
        val progress = HandshakeProgressMapper.fromFailure(
            previous = null,
            kind = ConnectFailureKind.BAD_SHARED_CREDENTIAL,
            title = "Token 与 Gateway 不一致",
            detail = "请核对 Token",
            deviceId = "dev-1",
        )
        assertEquals(HandshakeStepStatus.FAILED, progress.steps[0].status)
        assertEquals(HandshakeStepStatus.PENDING, progress.steps[1].status)
        assertEquals(HandshakeStepStatus.PENDING, progress.steps[3].status)
    }

    @Test
    fun fromFailure_pairingRequired_tokenDonePairingWaiting() {
        val progress = HandshakeProgressMapper.fromFailure(
            previous = null,
            kind = ConnectFailureKind.PAIRING_REQUIRED,
            title = "设备待配对",
            detail = "请在 Control UI 批准",
            deviceId = "dev-1",
        )
        assertEquals(HandshakeStepStatus.DONE, progress.steps[0].status)
        assertEquals(HandshakeStepStatus.WAITING_USER, progress.steps[1].status)
        assertEquals(HandshakeStepStatus.PENDING, progress.steps[2].status)
        assertTrue(progress.pairingWaiting)
    }

    @Test
    fun fromSuccess_afterPairingWaiting_marksApprovalStepsDone() {
        val waiting = HandshakeProgressMapper.fromFailure(
            previous = null,
            kind = ConnectFailureKind.PAIRING_REQUIRED,
            title = "设备待配对",
            detail = null,
            deviceId = "dev-1",
        )
        val progress = HandshakeProgressMapper.fromSuccess(waiting, "dev-1")
        assertEquals(HandshakeStepStatus.DONE, progress.steps[0].status)
        assertEquals(HandshakeStepStatus.DONE, progress.steps[1].status)
        assertEquals(HandshakeStepStatus.DONE, progress.steps[2].status)
        assertEquals(HandshakeStepStatus.DONE, progress.steps[3].status)
        assertTrue(progress.allSucceeded)
    }

    @Test
    fun fromSuccess_firstTime_skipsPairingSteps() {
        val progress = HandshakeProgressMapper.fromSuccess(previous = null, deviceId = "dev-1")
        assertEquals(HandshakeStepStatus.DONE, progress.steps[0].status)
        assertEquals(HandshakeStepStatus.SKIPPED, progress.steps[1].status)
        assertEquals(HandshakeStepStatus.SKIPPED, progress.steps[2].status)
        assertEquals(HandshakeStepStatus.DONE, progress.steps[3].status)
    }

    @Test
    fun fromFailure_rateLimited_keepsEarlierStepsDone() {
        val waiting = HandshakeProgressMapper.fromFailure(
            previous = null,
            kind = ConnectFailureKind.PAIRING_REQUIRED,
            title = "设备待配对",
            detail = null,
            deviceId = "dev-1",
        )
        val progress = HandshakeProgressMapper.fromFailure(
            previous = waiting,
            kind = ConnectFailureKind.RATE_LIMITED,
            title = "请求太频繁",
            detail = "请稍等",
            deviceId = "dev-1",
        )
        assertEquals(HandshakeStepStatus.DONE, progress.steps[0].status)
        assertEquals(HandshakeStepStatus.DONE, progress.steps[1].status)
        assertEquals(HandshakeStepStatus.DONE, progress.steps[2].status)
        assertEquals(HandshakeStepStatus.FAILED, progress.steps[3].status)
    }

    @Test
    fun onTestStarted_marksConnectRunning() {
        val started = HandshakeProgressMapper.onTestStarted(null)
        assertEquals(HandshakeStepStatus.RUNNING, started.steps[3].status)
    }
}
