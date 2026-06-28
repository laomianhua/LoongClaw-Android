package com.littlehelper.shell.transport

import android.content.Context
import android.util.Base64
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom

data class OpenClawDeviceIdentity(
    val deviceId: String,
    val publicKeyBase64Url: String,
    val privateKeyRaw: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpenClawDeviceIdentity) return false
        return deviceId == other.deviceId &&
            publicKeyBase64Url == other.publicKeyBase64Url &&
            privateKeyRaw.contentEquals(other.privateKeyRaw)
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + publicKeyBase64Url.hashCode()
        result = 31 * result + privateKeyRaw.contentHashCode()
        return result
    }
}

/**
 * 持久化 Ed25519 设备身份与配对后的 deviceToken（对齐 OpenClaw Gateway v4 device-auth）。
 */
class OpenClawDeviceIdentityStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadOrCreateIdentity(): OpenClawDeviceIdentity {
        val deviceId = prefs.getString(KEY_DEVICE_ID, null)
        val publicKey = prefs.getString(KEY_PUBLIC_KEY, null)
        val privateKeyB64 = prefs.getString(KEY_PRIVATE_KEY, null)
        if (deviceId != null && publicKey != null && privateKeyB64 != null) {
            return OpenClawDeviceIdentity(
                deviceId = deviceId,
                publicKeyBase64Url = publicKey,
                privateKeyRaw = Base64.decode(privateKeyB64, Base64.NO_WRAP)
            )
        }
        return generateAndPersist()
    }

    fun getDeviceToken(role: String = OPERATOR_ROLE): String? =
        prefs.getString(deviceTokenKey(role), null)?.takeIf { it.isNotBlank() }

    fun saveDeviceToken(role: String, token: String) {
        prefs.edit().putString(deviceTokenKey(role), token).apply()
    }

    fun signPayload(identity: OpenClawDeviceIdentity, payload: String): String {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(identity.privateKeyRaw, 0))
        val bytes = payload.toByteArray(Charsets.UTF_8)
        signer.update(bytes, 0, bytes.size)
        return base64UrlEncode(signer.generateSignature())
    }

    private fun generateAndPersist(): OpenClawDeviceIdentity {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()
        val publicRaw = (keyPair.public as Ed25519PublicKeyParameters).encoded
        val privateRaw = (keyPair.private as Ed25519PrivateKeyParameters).encoded
        val publicKeyBase64Url = base64UrlEncode(publicRaw)
        val deviceId = sha256Hex(publicRaw)
        prefs.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_PUBLIC_KEY, publicKeyBase64Url)
            .putString(KEY_PRIVATE_KEY, Base64.encodeToString(privateRaw, Base64.NO_WRAP))
            .apply()
        return OpenClawDeviceIdentity(deviceId, publicKeyBase64Url, privateRaw)
    }

    companion object {
        private const val PREFS_NAME = "openclaw_device_identity"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PUBLIC_KEY = "public_key_b64url"
        private const val KEY_PRIVATE_KEY = "private_key_raw"
        private const val KEY_DEVICE_TOKEN_PREFIX = "device_token_"
        const val OPERATOR_ROLE = "operator"

        fun base64UrlEncode(data: ByteArray): String =
            Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        fun sha256Hex(data: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

        private fun deviceTokenKey(role: String) = KEY_DEVICE_TOKEN_PREFIX + role
    }
}
