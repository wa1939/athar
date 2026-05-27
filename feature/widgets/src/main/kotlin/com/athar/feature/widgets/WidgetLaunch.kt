package com.athar.feature.widgets

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Returns the ComponentName for the host app's main launch activity. Glance's
 * `actionStartActivity(componentName)` requires this rather than an Intent —
 * the system constructs the launch Intent internally.
 *
 * The host app's package name varies by flavor (`com.athar.personal` vs
 * `com.athar.personal.debug`) — resolving via PackageManager keeps this
 * flavor-agnostic.
 */
internal fun appLaunchComponent(context: Context): ComponentName {
    val pm = context.packageManager
    val launchIntent = pm.getLaunchIntentForPackage(context.packageName)
    val component = launchIntent?.component
    if (component != null) return component
    // Fallback for unusual flavors: hard-code the activity name. MainActivity is
    // the documented entry point in app/src/main/AndroidManifest.xml.
    return ComponentName(context.packageName, "${context.packageName}.MainActivity")
}

@Suppress("unused")
internal fun appLaunchIntent(context: Context): Intent {
    val pm = context.packageManager
    val intent = pm.getLaunchIntentForPackage(context.packageName)
        ?: Intent(Intent.ACTION_MAIN).apply {
            setPackage(context.packageName)
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    return intent
}
