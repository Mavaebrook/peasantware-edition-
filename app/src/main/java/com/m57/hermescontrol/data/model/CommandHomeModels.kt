package com.m57.hermescontrol.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Overview from the Gateway's command-home plugin; the app reads only the approvals count. */
@Serializable
data class CommandHomeOverview(
    val approvals: CommandHomeApprovals? = null,
    val kingdomOpen: Int? = null,
)

@Serializable
data class CommandHomeApprovals(
    val pending: List<JsonElement> = emptyList(),
    /** False when the Gateway did not answer, so the count is unknown rather than zero. */
    val reachable: Boolean = true,
)
