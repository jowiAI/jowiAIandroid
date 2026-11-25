package ai.workis.jowi.data

import kotlinx.serialization.Serializable

// Console → Tedarik (ledger 128, page key `sourcing`). Shapes from Django
// `console_desks.sourcing_json` / `sourcing_state_json` and `sourcing_sheet.as_json` —
// every field nullable, id-like fields through FlexString, counts through FlexInt,
// money a JSON number (FlexDouble).

/** `GET console/sourcing/` — the board. */
@Serializable
data class SourcingBoard(
    val success: Boolean? = null,
    val dormant: Boolean? = null,
    val full: Boolean? = null,
    val counts: Map<String, @Serializable(with = FlexInt::class) Int?>? = null,
    /** The filter chips IN ORDER with the server's words (sourcing_board.chips). */
    val chips: List<SourcingChipRow>? = null,
    val state: SourcingState? = null,
    val rows: List<SourcingBoardRow>? = null,
)

@Serializable
data class SourcingChipRow(
    val code: String? = null,
    val label: String? = null,
    @Serializable(with = FlexInt::class) val count: Int? = null,
    val tone: String? = null,
)

@Serializable
data class SourcingBoardRow(
    @Serializable(with = FlexString::class) val id: String? = null,
    val title: String? = null,
    val buyer: String? = null,
    val sentAt: String? = null,
    val isTrial: Boolean? = null,
    val stand: SourcingStand? = null,
    /** A trial request's firms with the address each trial mail goes to; null on a real request and for the field lead. */
    val trial: List<SourcingTrialRow>? = null,
)

@Serializable
data class SourcingStand(val code: String? = null, val tone: String? = null, val words: String? = null)

@Serializable
data class SourcingTrialRow(
    @Serializable(with = FlexString::class) val key: String? = null,
    val name: String? = null,
    val email: String? = null,
    val to: String? = null,
)

/** The lane's state — the board's strip and the head of Tedarik › Ayarlar. */
@Serializable
data class SourcingState(
    val outreachOn: Boolean? = null,
    val joinOn: Boolean? = null,
    val trialBuyers: List<String>? = null,
    /** The coordinator ITSELF may turn the switches (a pilot reads them). */
    val canSet: Boolean? = null,
    val inbox: SourcingInbox? = null,
)

@Serializable
data class SourcingInbox(
    val address: String? = null,
    val connected: Boolean? = null,
    val hasConnection: Boolean? = null,
    val via: String? = null,
    val lastRunAt: String? = null,
    val lastReplyAt: String? = null,
    @Serializable(with = FlexInt::class) val taken: Int? = null,
)

/** `GET console/sourcing/settings/` — the state plus the trial accounts and addresses. */
@Serializable
data class SourcingSettings(
    val success: Boolean? = null,
    val outreachOn: Boolean? = null,
    val joinOn: Boolean? = null,
    val trialBuyers: List<String>? = null,
    val canSet: Boolean? = null,
    val inbox: SourcingInbox? = null,
    val trialAccounts: List<String>? = null,
    val trialAddresses: List<String>? = null,
)

/** `GET console/sourcing/lists/{listId}/sheet/` → `{success, sheet}`. */
@Serializable
data class SourcingSheetAnswer(val success: Boolean? = null, val sheet: SourcingSheet? = null, val error: String? = null)

@Serializable
data class SourcingSheet(
    @Serializable(with = FlexInt::class) val target: Int? = null,
    val isTrial: Boolean? = null,
    @Serializable(with = FlexInt::class) val unasked: Int? = null,
    @Serializable(with = FlexInt::class) val needs: Int? = null,
    @Serializable(with = FlexInt::class) val unsent: Int? = null,
    val full: Boolean? = null,
    /** A field lead: only the firms of its own region are listed. */
    val ownRegionOnly: Boolean? = null,
    /** The request's currency ('' = the seller is free). */
    val quoteCurrency: String? = null,
    @Serializable(with = FlexInt::class) val unplaced: Int? = null,
    val items: List<SourcingItem>? = null,
    val firms: List<SourcingFirm>? = null,
) {
    fun item(id: String): SourcingItem? = items?.firstOrNull { it.id == id }
    fun firm(key: String): SourcingFirm? = firms?.firstOrNull { it.key == key }
    val researching: Boolean get() = items?.any { it.research?.state == "running" } == true
}

@Serializable
data class SourcingItem(
    @Serializable(with = FlexInt::class) val n: Int? = null,
    @Serializable(with = FlexString::class) val id: String? = null,
    val name: String? = null,
    val spec: String? = null,
    @Serializable(with = FlexDouble::class) val qty: Double? = null,
    val unit: String? = null,
    @Serializable(with = FlexInt::class) val sellers: Int? = null,
    @Serializable(with = FlexInt::class) val asked: Int? = null,
    @Serializable(with = FlexInt::class) val quotes: Int? = null,
    @Serializable(with = FlexInt::class) val unsent: Int? = null,
    val state: String? = null,
    val tone: String? = null,
    val words: String? = null,
    @Serializable(with = FlexInt::class) val sameAs: Int? = null,
    val research: SourcingResearch? = null,
    val firmKeys: List<String>? = null,
)

@Serializable
data class SourcingResearch(
    val state: String? = null, // running | done | failed
    val at: String? = null,
    @Serializable(with = FlexInt::class) val found: Int? = null,
)

@Serializable
data class SourcingFirm(
    @Serializable(with = FlexString::class) val key: String? = null,
    @Serializable(with = FlexString::class) val profileId: String? = null,
    @Serializable(with = FlexString::class) val invitationId: String? = null,
    val name: String? = null,
    val isMember: Boolean? = null,
    val onPanel: Boolean? = null,
    val sent: Boolean? = null,
    val source: String? = null, // asked | held | queue | jowi | desk
    val email: String? = null,
    val emailBad: Boolean? = null,
    val canFixEmail: Boolean? = null,
    val sentAt: String? = null,
    @Serializable(with = FlexInt::class) val chases: Int? = null,
    val out: SourcingOut? = null,
    val hasForm: Boolean? = null,
    val formNumber: String? = null,
    val mails: List<SourcingMail>? = null,
    val hasReplyFile: Boolean? = null,
    val replyFileName: String? = null,
    val state: String? = null,
    val words: String? = null,
    val needs: Boolean? = null,
    val hasQuote: Boolean? = null,
    @Serializable(with = FlexString::class) val draftQuoteId: String? = null,
    val canApprove: Boolean? = null,
    val canUploadReply: Boolean? = null,
    val doubts: List<String>? = null,
    val itemIds: List<String>? = null,
    val quotedItemIds: List<String>? = null,
    val prices: Map<String, SourcingPrice?>? = null,
    val canSend: Boolean? = null,
    val askOutcome: Boolean? = null,
    val city: String? = null,
    val district: String? = null,
    val unplaced: Boolean? = null,
    val canPlace: Boolean? = null,
) {
    fun price(itemId: String): SourcingPrice? = prices?.get(itemId)
}

@Serializable
data class SourcingOut(val to: String? = null, val subject: String? = null, val body: String? = null)

@Serializable
data class SourcingMail(
    @Serializable(with = FlexInt::class) val n: Int? = null,
    val at: String? = null,
    val from: String? = null,
    val subject: String? = null,
    val body: String? = null,
    val files: List<SourcingMailFile>? = null,
)

@Serializable
data class SourcingMailFile(
    @Serializable(with = FlexInt::class) val n: Int? = null,
    val name: String? = null,
    val canOpen: Boolean? = null,
)

@Serializable
data class SourcingPrice(
    @Serializable(with = FlexDouble::class) val price: Double? = null,
    val currency: String? = null,
    val name: String? = null,
)
