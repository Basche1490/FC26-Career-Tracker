package com.fctracker.career.utils

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {
    // 16-byte fixed key for simplicity in a local app save file
    private val KEY = "aistudio_backup_".toByteArray()
    private val IV = "1234567890123456".toByteArray()
    
    fun encrypt(data: String): String {
        return try {
            val secretKey = SecretKeySpec(KEY, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(IV))
            val encryptedBytes = cipher.doFinal(data.toByteArray())
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
    
    fun decrypt(encryptedData: String): String {
        return try {
            val secretKey = SecretKeySpec(KEY, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(IV))
            val decodedBytes = Base64.decode(encryptedData, Base64.NO_WRAP)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
