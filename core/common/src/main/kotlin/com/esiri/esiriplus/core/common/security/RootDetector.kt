package com.esiri.esiriplus.core.common.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Detects whether the device is rooted or running in an unsafe environment.
 * Blocks app execution on compromised devices to protect sensitive medical data.
 */
object RootDetector {

    fun isDeviceCompromised(context: Context): Boolean {
        return hasSuBinary() ||
            hasSuperuserApk(context) ||
            hasRootManagementApps(context) ||
            hasRootCloakingApps(context) ||
            hasDangerousProps() ||
            hasRWSystemPartition() ||
            isRunningOnEmulator()
    }

    private fun hasSuBinary(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/su",
            "/system/bin/.ext/su",
            "/system/usr/we-need-root/su",
            "/data/local/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/su/bin/su",
            "/su/bin",
            "/magisk/.core/bin/su",
        )
        return paths.any { File(it).exists() }
    }

    private fun hasSuperuserApk(context: Context): Boolean {
        return File("/system/app/Superuser.apk").exists() ||
            File("/system/app/Superuser/Superuser.apk").exists() ||
            isPackageInstalled(context, "com.noshufou.android.su")
    }

    private fun hasRootManagementApps(context: Context): Boolean {
        val rootPackages = arrayOf(
            "com.topjohnwu.magisk",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "me.phh.superuser",
            "com.kingouser.com",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.oasisfeng.greenify",
        )
        return rootPackages.any { isPackageInstalled(context, it) }
    }

    private fun hasRootCloakingApps(context: Context): Boolean {
        val cloakingPackages = arrayOf(
            "com.devadvance.rootcloak",
            "com.devadvance.rootcloakplus",
            "de.robv.android.xposed.installer",
            "com.saurik.substrate",
            "com.zachspong.temprootremovejb",
            "com.amphoras.hidemyroot",
            "com.amphoras.hidemyrootadfree",
            "com.formyhm.hiderootPremium",
            "com.formyhm.hideroot",
        )
        return cloakingPackages.any { isPackageInstalled(context, it) }
    }

    private fun hasDangerousProps(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "ro.debuggable"))
            val value = process.inputStream.bufferedReader().readLine()?.trim()
            process.waitFor()
            value == "1"
        } catch (_: Exception) {
            false
        }
    }

    private fun hasRWSystemPartition(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("mount")
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.lines().any { line ->
                line.contains(" /system") && line.contains(" rw")
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun isRunningOnEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.BOARD == "QC_Reference_Phone" ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.HOST.startsWith("Build") ||
            Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
            Build.PRODUCT == "google_sdk" ||
            Build.PRODUCT == "sdk_gphone64_arm64" ||
            Build.PRODUCT == "vbox86p" ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu"))
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
