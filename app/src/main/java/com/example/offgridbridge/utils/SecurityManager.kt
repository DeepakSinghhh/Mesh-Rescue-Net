package com.example.offgridbridge.utils

import android.content.Context
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair

object SecurityManager {

    private lateinit var lazySodium: LazySodiumAndroid
    private lateinit var myKeyPair: KeyPair // Holds our Prime-based keys

    fun init(context: Context) {
        lazySodium = LazySodiumAndroid(SodiumAndroid())

        // 1. THE PRIMES PHASE (Key Generation)
        // This generates a Public Key (Safe to share) and Private Key (Keep secret)
        // using Curve25519 (Elliptic Curve math based on primes).
        myKeyPair = lazySodium.cryptoBoxKeypair()
    }

    // Share this with other phones so they can talk to you
    fun getMyPublicKey(): String {
        return myKeyPair.publicKey.asHexString
    }

    /**
     * ENCRYPT (The "Algo" Implementation)
     * 1. Uses Diffie-Hellman (My Private + Their Public) to make a Shared Key.
     * 2. Uses XSalsa20 (XOR Stream Cipher) to scramble the message.
     */
    fun encrypt(message: String, receiverPublicKeyHex: String): String {
        try {
            val receiverKey = Key.fromHexString(receiverPublicKeyHex)
            val nonce = lazySodium.nonce(Box.NONCEBYTES)
            val messageBytes = message.toByteArray()
            val ciphertext = ByteArray(messageBytes.size + Box.MACBYTES)

            lazySodium.cryptoBoxEasy(
                ciphertext,
                messageBytes,
                messageBytes.size.toLong(),
                nonce,
                receiverKey.asBytes,
                myKeyPair.secretKey.asBytes
            )

            return android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP) + ":" +
                    android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)

        } catch (e: Exception) {
            e.printStackTrace()
            return "ERROR"
        }
    }

    /**
     * DECRYPT
     * 1. Uses Diffie-Hellman (My Private + Their Public) to recreate the SAME Shared Key.
     * 2. Uses XOR to reverse the scrambling.
     */
    fun decrypt(encryptedPayload: String, senderPublicKeyHex: String): String {
        try {
            val parts = encryptedPayload.split(":")
            if (parts.size != 2) return "[Corrupt Message]"

            // Correct method to convert hex back to binary in LazySodium 5.x
            val nonce = android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP)
            val ciphertext = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
            val senderKey = Key.fromHexString(senderPublicKeyHex)
            val decrypted = ByteArray(ciphertext.size - Box.MACBYTES)

            val success = lazySodium.cryptoBoxOpenEasy(
                decrypted,
                ciphertext,
                ciphertext.size.toLong(),
                nonce,
                senderKey.asBytes,
                myKeyPair.secretKey.asBytes
            )

            return if (success) String(decrypted) else "[Decryption Failed - Wrong Key]"

        } catch (e: Exception) {
            return "[Decryption Failed - Wrong Key]"
        }
    }
}
