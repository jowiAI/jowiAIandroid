package ai.workis.jowi.data

import kotlinx.serialization.Serializable

/**
 * "The missing doors, all at once" (API_CONTRACT · Django docs/WORKIS_PARTNER_API.md,
 * 2026-09-30): the menu badges, Cari (`ledger/`), Takvim (`calendar/`) and the
 * carrier seat (`cargo/`). Every door sits on the service the web page calls;
 * the app renders what comes back and never decides a state. Money here is a
 * JSON number (FlexDouble also takes a string); ids through FlexString.
 * §7.1: a carrier payload carries names, streets, phones and the carrier's OWN
 * fee — never the goods' prices.
 */

// --- badges (feed.badges — the web menu poll verbatim) ---

@Serializable
data class PartnerBadges(
    val ordersUnread: Int? = null,
    /** page tag → unread bubbles: panel · quotes · lists · kargo */
    val pages: Map<String, Int>? = null,
    /** invitation id → unread doorbells */
    val invUnread: Map<String, Int>? = null,
)

// --- Cari (ledger.serialize_sections) ---

@Serializable
data class LedgerReply(val accounts: List<LedgerAccount>? = null)

@Serializable
data class LedgerAccount(
    val role: String? = null, // buyer | seller
    val counterparty: String? = null,
    @Serializable(with = FlexString::class) val accountId: String? = null,
    val currency: String? = null,
    @Serializable(with = FlexDouble::class) val balance: Double? = null,
    val virtual: Boolean? = null,
    val orders: List<LedgerOrderGroup>? = null,
    val openInvoices: List<LedgerOpenInvoice>? = null,
)

/** ONE row per order on screen; `rows` are its movements, record per line. */
@Serializable
data class LedgerOrderGroup(
    @Serializable(with = FlexString::class) val orderId: String? = null,
    val po: String? = null,
    val at: String? = null,
    val last: String? = null,
    val movements: Int? = null,
    @Serializable(with = FlexDouble::class) val invoiced: Double? = null,
    @Serializable(with = FlexDouble::class) val paid: Double? = null,
    @Serializable(with = FlexDouble::class) val balance: Double? = null,
    /** the server's words: "açık" / "vade 12.10.2026" / "kapandı" */
    val state: String? = null,
    val rows: List<LedgerRow>? = null,
)

@Serializable
data class LedgerRow(
    val at: String? = null,
    val type: String? = null,
    val typeLabel: String? = null,
    val note: String? = null,
    @Serializable(with = FlexDouble::class) val amount: Double? = null,
    @Serializable(with = FlexDouble::class) val balance: Double? = null,
    val extra: String? = null,
)

@Serializable
data class LedgerOpenInvoice(
    @Serializable(with = FlexString::class) val id: String? = null,
    val number: String? = null,
    @Serializable(with = FlexDouble::class) val total: Double? = null,
    val currency: String? = null,
    @Serializable(with = FlexString::class) val orderId: String? = null,
    val dueDate: String? = null,
    val overdue: Boolean? = null,
    val disputed: Boolean? = null,
)

// --- Takvim (market.calendar_events) ---

@Serializable
data class CalendarBoard(
    val events: List<CalendarEvent>? = null,
    val orders: List<CalendarOrderOption>? = null,
)

/** The extendedProps are flattened onto the event; only the read-only fields are decoded. */
@Serializable
data class CalendarEvent(
    val title: String? = null,
    /** a bare date ('YYYY-MM-DD') or an ISO datetime */
    val start: String? = null,
    /** "#RRGGBB" — tints a dot, never carries the kind */
    val color: String? = null,
    val kind: String? = null, // delivery | quote | invoice | warranty | reminder
    val number: String? = null,
    val status: String? = null,
    val overdue: Boolean? = null,
)

@Serializable
data class CalendarOrderOption(@Serializable(with = FlexString::class) val id: String? = null, val label: String? = null)

// --- carrier seat (carrier_seat.board) ---

@Serializable
data class CargoBoard(
    val jobs: List<CargoJob>? = null,
    val shipments: List<CargoShipment>? = null,
)

@Serializable
data class CargoJob(
    @Serializable(with = FlexString::class) val orderId: String? = null,
    val serviceLevel: String? = null,
    val serviceLabel: String? = null,
    /** the carrier's OWN fee — the only money on the Kargo screen (§7.1) */
    @Serializable(with = FlexDouble::class) val fee: Double? = null,
    val currency: String? = null,
    val pickupDistrict: String? = null,
    val dropoffDistrict: String? = null,
    val ageMinutes: Int? = null,
    val dueAt: String? = null,
    val pickupAfter: String? = null,
    val dayLoad: Int? = null,
    val dayCapacity: Int? = null,
    val dayFull: Boolean? = null,
    val packages: Int? = null,
    // consolidation (2026-10-03, carrier_seat._job) — absent on an older server
    /** how many of this carrier's shipments driven that day stop in `fitWhere` */
    val fitN: Int? = null,
    val fitWhere: String? = null,
    /** same day + same dropoff district; `bundleCount` jobs share it (1 = alone) */
    val bundle: String? = null,
    val bundleCount: Int? = null,
    /** the fee a fitting ertesi-gün job pays (the carrier's OWN fee); null otherwise */
    @Serializable(with = FlexDouble::class) val feeConsolidated: Double? = null,
)

@Serializable
data class CargoParty(
    val name: String? = null,
    val company: String? = null,
    val street: String? = null,
    val district: String? = null,
    val city: String? = null,
    val phone: String? = null,
)

@Serializable
data class CargoShipment(
    @Serializable(with = FlexString::class) val id: String? = null,
    val number: String? = null,
    val status: String? = null,
    val statusLabel: String? = null,
    val serviceLevel: String? = null,
    val packages: Int? = null,
    val packageNote: String? = null,
    val promisedAt: String? = null,
    val pickedUpAt: String? = null,
    val trackingNo: String? = null,
    val pickup: CargoParty? = null,
    val dropoff: CargoParty? = null,
    @Serializable(with = FlexString::class) val orderId: String? = null,
    val canDeliver: Boolean? = null,
    val canDrop: Boolean? = null,
    /** the ONLY source of status buttons — the app keeps no table of hops */
    val nextStatuses: List<String>? = null,
    val labelUrl: String? = null,
    /** the order's PO number (2026-10-01) */
    val poNumber: String? = null,
    /** one next step (2026-10-01): scan · out · deliver · retry · null when done */
    val primaryAction: String? = null,
    /** behind "Daha fazla": received · deliver_now · photo · failed · return · drop · label; null = an older server */
    val moreActions: List<String>? = null,
)

@Serializable
data class CargoScanReply(val shipment: CargoShipment? = null)

/** accept · status · drop · deliver — the reply's `message` is the note; a refusal's `error` the server's sentence. */
@Serializable
data class CargoActionResult(
    val success: Boolean? = null,
    val error: String? = null,
    val message: String? = null,
    val shipment: CargoShipment? = null,
)

@Serializable
data class CargoStatusBody(val status: String, val note: String? = null)

@Serializable
data class CargoDropBody(val reason: String? = null)

@Serializable
data class CargoDeliverBody(val otp: String)

// --- vehicles + runs (2026-10-03, slice 5 — carrier_seat._vehicle / _run). Plain fleet data, no money. ---

@Serializable
data class CargoVehicle(
    @Serializable(with = FlexString::class) val id: String? = null,
    val plate: String? = null,
    val kind: String? = null, // motosiklet | panelvan | kamyonet | kamyon
    val kindLabel: String? = null,
    val capacityKg: Int? = null,
    @Serializable(with = FlexDouble::class) val capacityM3: Double? = null,
    val koliCapacity: Int? = null,
    /** decoded only — drivers are not edited on the app */
    val driverUserIds: List<Int>? = null,
    val isActive: Boolean? = null,
)

@Serializable
data class CargoVehiclesReply(val vehicles: List<CargoVehicle>? = null)

/** Add (no id) or update; the server upper-cases the plate and refuses a duplicate with its sentence. */
@Serializable
data class CargoVehicleBody(val plate: String, val kind: String, val capacityKg: Int? = null)

/** One vehicle's run for one day. */
@Serializable
data class CargoRun(
    @Serializable(with = FlexString::class) val id: String? = null,
    val runDate: String? = null,
    val day: String? = null, // today | tomorrow
    @Serializable(with = FlexString::class) val vehicleId: String? = null,
    val plate: String? = null,
    val status: String? = null, // planned | active | done
    val driverUserId: Int? = null,
    val mine: Boolean? = null,
    val note: String? = null,
    val doneCount: Int? = null,
    val stops: List<CargoStop>? = null,
)

@Serializable
data class CargoStop(
    val seq: Int? = null,
    val kind: String? = null, // pickup | dropoff
    @Serializable(with = FlexString::class) val shipmentId: String? = null,
    val number: String? = null,
    val place: String? = null,
    val district: String? = null,
    val street: String? = null,
    val serviceLevel: String? = null,
    /** "HH:MM" or "" */
    val deadline: String? = null,
    val late: Boolean? = null,
    val packages: Int? = null,
    /** ISO when ticked, null otherwise */
    val doneAt: String? = null,
)

@Serializable
data class CargoRunsReply(val runs: List<CargoRun>? = null)

/** `vehicleId` = a vehicle's id or "all"; `day` = today | tomorrow. */
@Serializable
data class CargoPlanBody(val vehicleId: String, val day: String)

/** Re-planning replaces that day's runs; `leftOut` names the shipments that did not fit. */
@Serializable
data class CargoPlanReply(
    val runs: List<CargoRun>? = null,
    val leftOut: List<@Serializable(with = FlexString::class) String>? = null,
)

@Serializable
data class CargoStopBody(val shipmentId: String, val kind: String)

@Serializable
data class CargoStopReply(val runStatus: String? = null, val shipmentStatus: String? = null)
