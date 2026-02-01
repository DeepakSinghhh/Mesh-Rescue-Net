package com.example.offgridbridge.utils

import android.content.Context
import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair

object SecurityManager {

    private lateinit var lazySodium: LazySodiumAndroid
    private lateinit var myKeyPair: KeyPair // Holds our Prime-based keys

    // We need a "Nonce" (Number used ONCE) to make the XOR truly random every time.
    // For this hackathon, we will generate a random one for each message.

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

            // Generate a random 24-byte Nonce (Salt)
            val nonce = lazySodium.nonce(Box.NONCEBYTES)

            // The Magic: "Box Easy" does the Shared Key calc + Encryption in one go.
            val encryptedBytes = lazySodium.cryptoBoxEasy(
                message,
                nonce,
                receiverKey,             // Their Public Key
                myKeyPair.secretKey      // My Private Key
            )

            // We must send the Nonce + The Ciphertext together so they can decrypt it
            // Format: "NONCE:CIPHERTEXT"
            return lazySodium.toHexStr(nonce) + ":" + encryptedBytes

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

            val nonce = lazySodium.toBin(parts[0])
            val ciphertext = parts[1] // The hex string of the encrypted message
            val senderKey = Key.fromHexString(senderPublicKeyHex)

            val decrypted = lazySodium.cryptoBoxOpenEasy(
                ciphertext,
                nonce,
                senderKey,               // Their Public Key (Sender)
                myKeyPair.secretKey      // My Private Key
            )

            return decrypted
        } catch (e: Exception) {
            return "[Decryption Failed - Wrong Key]"
        }
    }
}