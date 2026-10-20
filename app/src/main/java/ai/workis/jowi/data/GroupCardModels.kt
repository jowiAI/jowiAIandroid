package ai.workis.jowi.data

import kotlinx.serialization.Serializable

// The one group card (ledger 65; Django f9e65898 — api/v1/workis/groups_api.py `headings_page`,
// `heading_suggestions`, views.py `seller_catalog_group_suggestions`). Shared by Gruplar's
// headings tab and Ürünler's waiting families. Every field nullable with a default; ids
// through FlexString.

/** One of Jowi's picks: `[]` under a key = read, nothing fits; key absent = not read. */
@Serializable
data class GroupPick(
    @Serializable(with = FlexString::class) val groupId: String? = null,
    val name: String? = null,
    /** The sector's NAME (display). */
    val sector: String? = null,
    val why: String? = null,
)

/** `GET console/groups/headings/suggestions/` and `GET seller/catalog/group-suggestions/`:
 *  `suggestions` keyed by heading key, family id or product id. `groups` comes only on the
 *  seller door (every active sector's main groups, the seller's own sector first). */
@Serializable
data class GroupSuggestions(
    val success: Boolean? = null,
    val suggestions: Map<String, List<GroupPick>>? = null,
    val groups: List<GroupListRow>? = null,
    val error: String? = null,
)

@Serializable
data class GroupTargetParent(@Serializable(with = FlexString::class) val id: String? = null, val name: String? = null)

/** Where a new group may go: a sector and its top-level groups (Genel apart). */
@Serializable
data class GroupTarget(
    @Serializable(with = FlexString::class) val id: String? = null,
    val slug: String? = null,
    val name: String? = null,
    val parents: List<GroupTargetParent>? = null,
)

/** A group the search may name: `parent` and `sector` are NAMES, `sectorSlug` the slug. */
@Serializable
data class GroupListRow(
    @Serializable(with = FlexString::class) val id: String? = null,
    val name: String? = null,
    val slug: String? = null,
    val parent: String? = null,
    val sector: String? = null,
    val sectorSlug: String? = null,
)

/** `GET console/groups/headings/` — the headings tab (console seats; 403 otherwise). */
@Serializable
data class GroupsHeadingsPage(
    val success: Boolean? = null,
    val isCoordinator: Boolean? = null,
    val canWrite: Boolean? = null,
    val leadsWrite: Boolean? = null,
    val headings: List<GroupUnboundHeading>? = null,
    val targets: List<GroupTarget>? = null,
    val groups: List<GroupListRow>? = null,
    val error: String? = null,
)

/** What the card chose; the caller posts. */
sealed interface GroupCardChoice {
    data class Existing(val groupId: String, val name: String) : GroupCardChoice
    /** `sectorId`/`sectorSlug` = the chosen target; `parentId` = a top group there or null (top level). */
    data class New(val name: String, val sectorId: String, val sectorSlug: String, val parentId: String?) : GroupCardChoice
}
