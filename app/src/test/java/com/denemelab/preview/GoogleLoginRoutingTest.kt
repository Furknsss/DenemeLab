package com.denemelab.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleLoginRoutingTest {
    @Test fun android16RoutesToGooglePlayServicesAccountPicker() {
        assertEquals(
            GoogleAccountAuth.Flow.COMPATIBILITY,
            GoogleAccountAuth.preferredFlow(36)
        )
        assertEquals(
            GoogleAccountAuth.Flow.COMPATIBILITY,
            GoogleAccountAuth.preferredFlow(37)
        )
    }

    @Test fun android15AndEarlierStillUseCredentialManager() {
        assertEquals(
            GoogleAccountAuth.Flow.CREDENTIAL_MANAGER,
            GoogleAccountAuth.preferredFlow(35)
        )
        assertEquals(
            GoogleAccountAuth.Flow.CREDENTIAL_MANAGER,
            GoogleAccountAuth.preferredFlow(24)
        )
    }
}
