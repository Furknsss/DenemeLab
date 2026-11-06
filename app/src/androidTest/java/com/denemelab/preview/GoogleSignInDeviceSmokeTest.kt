package com.denemelab.preview

import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the Android 16 Google Play Services fallback without a real login. */
@RunWith(AndroidJUnit4::class)
class GoogleSignInDeviceSmokeTest {
    @Test fun googlePickerIntentCanBeBuiltAndFirebaseIsBundled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Firebase app config must be in the APK", FirebaseCloudBackup.configured(context))
        assertTrue("Google OAuth web client id must be in APK",
            GoogleAccountAuth.webClientConfigured(context))
        ActivityScenario.launch(PremiumActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val intent = GoogleAccountAuth.compatibilityIntent(activity)
                assertNotNull("Google-owned account picker intent", intent)
                assertTrue("Picker intent must contain action or component",
                    !intent.action.isNullOrBlank() || intent.component != null)
            }
        }
    }

    @Test fun android16UsesCompatibilityNotTheKnownFailingCredentialPicker() {
        if (Build.VERSION.SDK_INT >= 36) {
            assertEquals(GoogleAccountAuth.Flow.COMPATIBILITY,
                GoogleAccountAuth.preferredFlow(Build.VERSION.SDK_INT))
        }
    }
}
