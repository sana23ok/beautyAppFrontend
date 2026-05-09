package com.example.beautyappfrontend.utils

import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

object GoogleSignInHelper {

    private const val TAG = "GoogleSignIn"

    /**
     * Returns the SHA-1 fingerprint of the first signing certificate in the APK.
     * Use this value when registering the Android OAuth client in Google Cloud Console.
     */
    fun getApkSha1(context: Context): String {
        return try {
            val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager
                    .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo
                    ?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                context.packageManager
                    .getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                    .signatures
            }
            val bytes = sigs?.firstOrNull()?.toByteArray() ?: return "unavailable"
            MessageDigest.getInstance("SHA-1")
                .digest(bytes)
                .joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read APK signature", e)
            "unavailable"
        }
    }

    /**
     * Handles a GoogleSignIn ApiException:
     *  - statusCode 10 (DEVELOPER_ERROR): shows a dialog with the exact SHA-1 and
     *    instructions to create an Android OAuth client in Google Cloud Console.
     *  - other codes: shows a short Toast with the status code.
     */
    fun handleApiException(
        context: Context,
        statusCode: Int,
        onDismiss: (() -> Unit)? = null,
    ) {
        Log.e(TAG, "ApiException statusCode=$statusCode")

        if (statusCode == 10) {
            val sha1 = getApkSha1(context)
            val webClientId = try {
                context.getString(context.resources.getIdentifier("server_client_id", "string", context.packageName))
            } catch (_: Exception) { "not set" }

            val msg = buildString {
                appendLine("Google Cloud Console → Credentials → Create credentials → OAuth client ID → Android")
                appendLine()
                appendLine("Package name:")
                appendLine("  ${context.packageName}")
                appendLine()
                appendLine("SHA-1 certificate fingerprint:")
                appendLine("  $sha1")
                appendLine()
                appendLine("Web client ID in use:")
                appendLine("  $webClientId")
                appendLine()
                appendLine("After creating the Android client, rebuild and try again.")
            }

            Log.e(TAG, "DEVELOPER_ERROR setup info:\n$msg")

            AlertDialog.Builder(context)
                .setTitle("Google Sign-In — Setup Required")
                .setMessage(msg)
                .setPositiveButton("OK") { _, _ -> onDismiss?.invoke() }
                .show()
        } else {
            val text = when (statusCode) {
                12500 -> "Sign-In failed (12500). Try again."
                12501 -> "Sign-In cancelled."
                7     -> "Network error. Check your connection."
                else  -> "Google Sign-In failed (code $statusCode)."
            }
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
            onDismiss?.invoke()
        }
    }
}
