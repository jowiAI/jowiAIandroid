package ai.workis.jowi.data

import kotlinx.serialization.Serializable

// Console Gruplar — the Workis groups page (ledger 62; Django api/v1/workis/groups_api.py).
// `GET console/groups/?sector=` is the whole page; every POST answers the page re-read with
// `message` spread at the top level, or `{success: false, error}` with 400/403/404.
// Every field nullable with a default; id-like fields through FlexString.

@Serializable
data class GroupsPage(
    val success: Boolean? = null,
    /** The answer's sentence after a write (green tick under the title). */
    val message: String? = null,
    /** A refusal's sentence (never on a 2xx page, read defensively). */
    val error: String? = null,
    val sector: GroupSector? = null,
    val sectors: List<GroupSector>? = null,
    val isCoordinator: Boolean? = null,
    val canWrite: Boolean? = null,
    val leadsWrite: Boolean? = null,
    val sellers: List<GroupSeller>? = null,
    val groups: List<GroupNode>? = null,
    val parents: List<GroupRef>? = null,
    val otherSectors: List<GroupOtherSector>? = null,
    val mergeTargets: List<GroupMergeTarget>? = null,
    val unboundHeadings: List<GroupUnboundHeading>? = null,
)

@Serializable
data class GroupSector(
    @Serializable(with = FlexString::class) val id: String? = null,
    val slug: String? = null,
    val name: String? = null,
)

@Serializable
data class GroupSeller(
    @Serializable(with = FlexString::class) val id: String? = null,
    val name: String? = null,
    val shortName: String? = null,
    val familyCount: Int? = null,
    val explicit: Boolean? = null,
)

@Serializable
data class GroupRef(
    @Serializable(with = FlexString::class) val id: String? = null,
    val name: String? = null,
)

@Serializable
data class GroupOtherSector(
    @Serializable(with = FlexString::class) val id: String? = null,
    val name: String? = null,
    val parents: List<GroupRef>? = null,
)

@Serializable
data class GroupMergeTarget(
    @Serializable(with = FlexString::class) val id: String? = null,
    val name: String? = null,
    val parent: String? = null,
    val sector: String? = null,
    val sectorSlug: String? = null,
)

@Serializable
data class GroupHeading(
    val label: String? = null,
    /** Set when the heading came in through an alias — the × removes it. */
    val alias: String? = null,
    val productCount: Int? = null,
    val familyCount: Int? = null,
    /** No catalogue prints it today → drawn at 55 %. */
    val idle: Boolean? = null,
)

@Serializable
data class GroupUnboundHeading(
    val key: String? = null,
    val label: String? = null,
    val suggestedName: String? = null,
    val productCount: Int? = null,
    val familyCount: Int? = null,
    val sellers: List<String>? = null,
    /** The sectors whose sellers print it (names) and the slug a row opens on (ledger 65). */
    val sectors: List<String>? = null,
    val homeSector: String? = null,
    val isCode: Boolean? = null,
)

/** A Workis group: two levels; `Genel` is the sector's fallback and keeps its name. */
@Serializable
data class GroupNode(
    @Serializable(with = FlexString::class) val id: String? = null,
    val name: String? = null,
    val slug: String? = null,
    @Serializable(with = FlexString::class) val parentId: String? = null,
    val isGenel: Boolean? = null,
    /** Own products (a sub-row's badge). */
    val productCount: Int? = null,
    /** Own + children (a top row's badge). */
    val totalCount: Int? = null,
    val movable: Boolean? = null,
    val deletable: Boolean? = null,
    val renamable: Boolean? = null,
    val headings: List<GroupHeading>? = null,
    val children: List<GroupNode>? = null,
)

// --- request bodies: every one carries `sector` = the slug on screen ---
// (move/ is a hand-built JsonObject in GroupsViewModel: its `parentId: null` must be sent
// explicitly, and WorkisJson drops nulls.)

@Serializable data class GroupsSectorAddBody(val name: String)
@Serializable data class GroupsLeadsWriteBody(val sector: String, val on: Boolean)
@Serializable data class GroupsSellerSectorBody(val sector: String, val to: String)
@Serializable data class GroupsAddBody(val sector: String, val name: String, val parentId: String? = null)
@Serializable data class GroupsRenameBody(val sector: String, val name: String)
@Serializable data class GroupsMergeBody(val sector: String, val into: String)
@Serializable data class GroupsSectorOnlyBody(val sector: String)
@Serializable data class GroupsHeadingBindBody(
    val sector: String,
    val key: String,
    val groupId: String? = null,
    val name: String? = null,
    /** A new group in ANOTHER sector (ledger 65): the target sector's id; `parentId` is a top group there. */
    val toSectorId: String? = null,
    val parentId: String? = null,
)
@Serializable data class GroupsAliasRemoveBody(val sector: String, val alias: String)
