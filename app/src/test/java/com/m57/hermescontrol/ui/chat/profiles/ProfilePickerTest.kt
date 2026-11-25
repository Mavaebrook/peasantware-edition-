package com.m57.hermescontrol.ui.chat.profiles

import com.m57.hermescontrol.data.model.ProfileInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfilePickerTest {
    @Test
    fun profiles_dropHiddenOnesAndMarkTheMixture() {
        val profiles =
            listOf(
                ProfileInfo(name = "default", model = "deepseek-v4-pro", provider = "deepseek"),
                ProfileInfo(name = "moe", display_name = "MoE", model = "moe", provider = "moa"),
                ProfileInfo(name = "pete", model = " "),
            ).toPickerProfiles(hidden = setOf("pete"))

        assertEquals(
            listOf(
                PickerProfile("default", "default", "deepseek-v4-pro", isMixture = false),
                PickerProfile("moe", "MoE", "moe", isMixture = true),
            ),
            profiles,
        )
    }

    @Test
    fun activeProfile_fallsBackToDefault() {
        assertEquals("default", activeProfileName(null))
        assertEquals("default", activeProfileName(" "))
        assertEquals("forge", activeProfileName("forge"))
    }
}
