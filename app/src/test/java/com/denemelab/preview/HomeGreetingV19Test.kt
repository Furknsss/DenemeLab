package com.denemelab.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGreetingV19Test {
    @Test fun greetingsRespectLocalDayPeriods() {
        assertEquals("İyi geceler", HomeGreetingV19.forHour(0))
        assertEquals("Günaydın", HomeGreetingV19.forHour(5))
        assertEquals("Günaydın", HomeGreetingV19.forHour(11))
        assertEquals("İyi öğlenler", HomeGreetingV19.forHour(12))
        assertEquals("İyi öğlenler", HomeGreetingV19.forHour(16))
        assertEquals("İyi akşamlar", HomeGreetingV19.forHour(17))
        assertEquals("İyi akşamlar", HomeGreetingV19.forHour(21))
        assertEquals("İyi geceler", HomeGreetingV19.forHour(22))
    }
}
