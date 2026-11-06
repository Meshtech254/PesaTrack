package ke.pesatrail.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.nio.ByteBuffer

object Vault {
    private fun wrappingKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("pesatrail.wrap.v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("pesatrail.wrap.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun databaseKey(context: Context): ByteArray {
        val file = java.io.File(context.noBackupFilesDir, "database-key.wrapped")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (file.exists()) {
            val bytes = file.readBytes()
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
            return cipher.doFinal(bytes.copyOfRange(12,bytes.size))
        }
        // Never regenerate a missing key for an existing encrypted database.
        check(!context.getDatabasePath("ledger.db").exists()) { "Database key unavailable. Restore a portable encrypted backup." }
        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val temp = java.io.File(context.noBackupFilesDir, "database-key.tmp")
        java.io.FileOutputStream(temp).use { it.write(cipher.iv + cipher.doFinal(key)); it.fd.sync() }
        check(temp.renameTo(file))
        return key
    }
}
object PortableBackup {
    private val magic = "PSTB0001".toByteArray()
    private fun derive(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, 600_000, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") } finally { spec.clearPassword() }
    }
    fun encrypt(bytes: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 12) { "Use a passphrase of at least 12 characters" }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(password,salt)); cipher.updateAAD(magic + salt)
        return magic + salt + cipher.iv + cipher.doFinal(bytes)
    }
    fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
        require(bytes.size in 52..52_428_800 && bytes.take(8).toByteArray().contentEquals(magic)) { "Invalid backup" }
        val salt = bytes.copyOfRange(8,24)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derive(password,salt), GCMParameterSpec(128,bytes.copyOfRange(24,36)))
        cipher.updateAAD(magic + salt)
        return cipher.doFinal(bytes.copyOfRange(36,bytes.size))
    }
    fun pinHash(pin: String, salt: ByteArray): ByteArray = derive(pin.toCharArray(), salt).encoded
}
