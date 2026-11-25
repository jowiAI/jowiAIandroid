package ai.workis.jowi.data

import kotlinx.serialization.Serializable

// Console → Sistem (ledger 128, page key `system`) — the coordinator alone. Django:
// api/v1/workis/console_api.py console_system* → services/console_desks.system_json().

/** `GET console/system/` → `{success, pilots, pages, testerLinks, seals, tokens, webOnly}`. */
@Serializable
data class ConsoleSystemPage(
    val success: Boolean? = null,
    val pilots: List<SystemPilot>? = null,
    val pages: List<SystemPageRef>? = null,
    /** `{android, ios}` — the remembered invite links; `{}` when none is set. */
    val testerLinks: Map<String, String?>? = null,
    val seals: SystemSeals? = null,
    val tokens: SystemTokens? = null,
    val webOnly: List<String>? = null,
)

@Serializable
data class SystemPilotPage(val key: String? = null, val label: String? = null, @Serializable(with = FlexBool::class) val on: Boolean? = null)

@Serializable
data class SystemPilot(
    @Serializable(with = FlexString::class) val userId: String? = null,
    val email: String? = null,
    val name: String? = null,
    @Serializable(with = FlexBool::class) val active: Boolean? = null,
    val pages: List<SystemPilotPage>? = null,
)

@Serializable
data class SystemPageRef(val key: String? = null, val label: String? = null)

@Serializable
data class SystemBrokenSeal(
    @Serializable(with = FlexString::class) val userId: String? = null,
    val email: String? = null,
    val lastKnown: String? = null,
)

@Serializable
data class SystemSeals(@Serializable(with = FlexBool::class) val enforcing: Boolean? = null, val broken: List<SystemBrokenSeal>? = null)

@Serializable
data class SystemTokens(
    @Serializable(with = FlexInt::class) val hashed: Int? = null,
    @Serializable(with = FlexInt::class) val legacy: Int? = null,
    @Serializable(with = FlexInt::class) val legacyUsed: Int? = null,
    @Serializable(with = FlexInt::class) val plain: Int? = null,
)

/** `pilots/` → `{success, message, pilots}` · `tester-invite/` → `{success, message}`; a refusal `{success: false, error}`. */
@Serializable
data class ConsoleSystemPilotsReply(
    val success: Boolean? = null,
    val message: String? = null,
    val error: String? = null,
    val pilots: List<SystemPilot>? = null,
)
