package ai.workis.jowi.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface WorkisApi {

    // --- auth (contract: docs/API_CONTRACT.md in jowiAIs) ---
    // OTP pair is the proposed contract; backend gap — password login stays for testing.
    @POST("auth/otp/request/")
    suspend fun otpRequest(@Body body: OtpRequestBody): AuthResponse

    @POST("auth/otp/verify/")
    suspend fun otpVerify(@Body body: OtpVerifyBody): AuthResponse

    /** The account is opened only after the person says so; answers otp/verify's shape. */
    @POST("auth/signup/confirm/")
    suspend fun signupConfirm(@Body body: SignupConfirmBody): AuthResponse

    @POST("auth/login/")
    suspend fun login(@Body body: LoginBody): AuthResponse

    @POST("auth/validate-token/")
    suspend fun validateToken(): AuthResponse

    // --- passkeys (API_CONTRACT §2): the system holds the key, the app carries JSON ---
    @POST("auth/passkey/register/options/")
    suspend fun passkeyRegisterOptions(): PasskeyOptions

    /** 201. A code-opened session on an account that has one answers 403 `need_passkey`. */
    @POST("auth/passkey/register/verify/")
    suspend fun passkeyRegisterVerify(@Body body: PasskeyRegisterBody): PasskeyRegisterResult

    /** No auth, no e-mail asked — the credential is discoverable. */
    @POST("auth/passkey/login/options/")
    suspend fun passkeyLoginOptions(): PasskeyOptions

    /** Answers in `otp/verify/`'s shape exactly: {success, message, token, user}. */
    @POST("auth/passkey/login/verify/")
    suspend fun passkeyLoginVerify(@Body body: PasskeyLoginBody): AuthResponse

    /** A fresh assertion for the one call that turns the e-mail-code door. */
    @POST("auth/passkey/step-up/options/")
    suspend fun passkeyStepUpOptions(): PasskeyOptions

    @POST("auth/passkey/code-sign-in/")
    suspend fun passkeyCodeSignIn(@Body body: PasskeyCodeSignInBody): CodeSignInResult

    @GET("auth/passkeys/")
    suspend fun passkeys(): PasskeyList

    @DELETE("auth/passkeys/{id}/")
    suspend fun passkeyDelete(@Path("id") id: Int): SimpleResult

    /** "Son girişler" — the last ten, refused attempts included. */
    @GET("auth/sign-ins/")
    suspend fun signIns(): SignIns

    @POST("auth/logout/")
    suspend fun logout(): AuthResponse

    @PUT("auth/profile/update/")
    suspend fun updateProfile(@Body body: ProfileUpdateBody): AuthResponse

    // --- buyer ---
    @GET("workis/cases/")
    suspend fun cases(): List<Case>

    @POST("workis/cases/")
    suspend fun createCase(@Body body: CreateCaseBody): Case

    // --- public apply lane (no auth) ---
    @Multipart
    @POST("workis/apply/parse/")
    suspend fun applyParse(@Part file: okhttp3.MultipartBody.Part): ApplyParseResponse

    @POST("workis/apply/")
    suspend fun applySubmit(@Body body: ApplySubmitBody): ApplySubmitResponse

    @POST("workis/apply/ask/")
    suspend fun applyAsk(@Body body: ApplyAskBody): ApplyAskResponse

    @Multipart
    @POST("workis/apply/")
    suspend fun applySubmitWithFile(
        @PartMap fields: Map<String, @JvmSuppressWildcards okhttp3.RequestBody>,
        @Part taxFile: okhttp3.MultipartBody.Part,
    ): ApplySubmitResponse

    /** Agreement metadata driving the paper: `?role=<wizard pick>` or `?key=supplier|carrier|expert`. */
    @GET("workis/agreement/")
    suspend fun agreement(
        @Query("role") role: String? = null,
        @Query("key") key: String? = null,
    ): AgreementMeta

    /** Raw fetch (PDFs). Goes through the same client, so the token header rides along. */
    @Streaming
    @GET
    suspend fun download(@Url url: String): okhttp3.ResponseBody

    // --- coordinator console (role 3 or 4, exact match) ---
    @GET("workis/console/summary/")
    suspend fun consoleSummary(): ConsoleSummary

    @GET("workis/console/applications/")
    suspend fun consoleApplications(): ConsoleApplications

    @GET("workis/console/applications/{id}/")
    suspend fun applicationDetail(@Path("id") id: String): ApplicationDetail

    /** ONE field per call — cell-exit autosave semantics (web detail's inline editing). */
    @POST("workis/console/applications/{id}/update/")
    suspend fun applicationUpdate(@Path("id") id: String, @Body body: UpdateFieldBody): SimpleResult

    @POST("workis/console/applications/{id}/create-partner/")
    suspend fun applicationCreatePartner(@Path("id") id: String): SimpleResult

    @POST("workis/console/applications/{id}/invite/")
    suspend fun applicationInvite(@Path("id") id: String): SimpleResult

    @POST("workis/console/applications/{id}/close/")
    suspend fun applicationClose(@Path("id") id: String): SimpleResult

    @POST("workis/console/partners/{partnerId}/approve/")
    suspend fun partnerApprove(@Path("partnerId") partnerId: String): SimpleResult

    @GET("workis/console/questions/")
    suspend fun consoleQuestions(): ConsoleQuestions

    /** "Jowi answered" rows (roles 3|4); sources/topics re-aggregate under the active filter. */
    @GET("workis/console/jowi-answered/")
    suspend fun consoleJowiAnswered(
        @Query("days") days: Int,
        @Query("source") source: String? = null,
        @Query("topic") topic: String? = null,
    ): JowiAnsweredLane

    @POST("workis/console/questions/{id}/done/")
    suspend fun questionDone(@Path("id") id: String): SimpleResult

    @POST("workis/console/questions/{id}/answer/")
    suspend fun questionAnswer(@Path("id") id: String, @Body body: AnswerBody): SimpleResult

    @GET("workis/console/partners/")
    suspend fun consolePartners(): ConsolePartners

    @GET("workis/console/conversations/")
    suspend fun consoleConversations(): ConsoleConversations

    @GET("workis/console/conversations/{id}/")
    suspend fun conversationDetail(@Path("id") id: String): ConversationDetail

    @POST("workis/console/conversations/{id}/reply/")
    suspend fun conversationReply(@Path("id") id: String, @Body body: ReplyBody): SimpleResult

    @POST("workis/console/partners/{partnerId}/conversation/")
    suspend fun startConversation(@Path("partnerId") partnerId: String, @Body body: StartConvBody): SimpleResult

    /** Expert applicant = a person, not a partner: approve makes the expert seat + invite. */
    @POST("workis/console/applications/{id}/approve-expert/")
    suspend fun applicationApproveExpert(@Path("id") id: String): SimpleResult

    // --- expert seat (role 5 only; every endpoint 403s for other seats) — slice 1 ---
    @GET("workis/expert/summary/")
    suspend fun expertSummary(): ExpertSummary

    @GET("workis/expert/questions/")
    suspend fun expertQuestions(): ConsoleQuestions

    @POST("workis/expert/questions/{id}/answer/")
    suspend fun expertAnswer(@Path("id") id: String, @Body body: ExpertAnswerBody): SimpleResult

    @GET("workis/expert/reviews/")
    suspend fun expertReviews(): ExpertReviews

    @POST("workis/expert/reviews/{sessionKey}/")
    suspend fun expertCloseReview(@Path("sessionKey") sessionKey: String, @Body body: CloseReviewBody): SimpleResult

    @GET("workis/expert/knowledge/")
    suspend fun expertKnowledge(@Query("q") q: String? = null): ExpertKnowledge

    @POST("workis/expert/knowledge/")
    suspend fun expertAddKnowledge(@Body body: KnowledgeBody): KnowledgeSaveResult

    @POST("workis/expert/knowledge/units/{id}/toggle/")
    suspend fun expertToggleUnit(@Path("id") id: String): ToggleResult

    @GET("workis/expert/consults/")
    suspend fun expertConsults(): ExpertConsults

    @GET("workis/expert/consults/{id}/")
    suspend fun expertConsult(@Path("id") id: String): ExpertConsultDetail

    @POST("workis/expert/consults/{id}/reply/")
    suspend fun expertConsultReply(@Path("id") id: String, @Body body: TextBody): SimpleResult

    @POST("workis/expert/consults/{id}/unavailable/")
    suspend fun expertConsultUnavailable(@Path("id") id: String, @Body body: UnavailableBody): SimpleResult

    // --- expert slice 2: earnings · profile · payout · departure ---
    @GET("workis/expert/earnings/")
    suspend fun expertEarnings(): ExpertEarnings

    @POST("workis/expert/statements/{id}/object/")
    suspend fun expertObjectStatement(@Path("id") id: String, @Body body: TextBody): SimpleResult

    @GET("workis/expert/profile/")
    suspend fun expertProfile(): ExpertProfile

    /** Partial update — only the given keys; the door answers with the record, not {success}. */
    @POST("workis/expert/profile/")
    suspend fun expertProfileUpdate(@Body body: kotlinx.serialization.json.JsonObject): ExpertProfile

    @GET("workis/payout/")
    suspend fun payout(): ExpertPayout

    @POST("workis/payout/")
    suspend fun payoutSave(@Body body: PayoutBody): ExpertPayout

    @POST("workis/expert/departure/")
    suspend fun expertDeparture(@Body body: DepartureBody): DepartureResult

    @POST("workis/expert/rejoin/")
    suspend fun expertRejoin(): SimpleResult

    // --- coordinator Bilgi (roles 3|4): the expert knowledge screen on the console door ---
    @GET("workis/console/knowledge/")
    suspend fun consoleKnowledge(@Query("q") q: String? = null, @Query("semantic") semantic: Int? = null): ExpertKnowledge

    @POST("workis/console/knowledge/")
    suspend fun consoleAddKnowledge(@Body body: ConsoleKnowledgeBody): KnowledgeSaveResult

    @POST("workis/console/knowledge/units/{id}/toggle/")
    suspend fun consoleToggleUnit(@Path("id") id: String): ToggleResult

    /** Product pairs (role 3; a lead gets 403 → the section stays hidden). */
    @GET("workis/console/companions/")
    suspend fun consoleCompanions(): ConsoleCompanions

    @POST("workis/console/companions/{id}/")
    suspend fun consoleCompanionVerdict(@Path("id") id: String, @Body body: VerdictBody): SimpleResult

    // --- partner seat (role 1): Firma · lists (buyer) · quotes (seller) — Django docs/WORKIS_PARTNER_API.md ---
    @GET("workis/company/")
    suspend fun company(): PartnerCompany

    @POST("workis/company/")
    suspend fun companySave(@Body body: kotlinx.serialization.json.JsonObject): CompanySaveResult

    @POST("workis/company/autonomy/")
    suspend fun companyAutonomy(@Body body: kotlinx.serialization.json.JsonObject): AutonomyResult

    @Streaming
    @GET("workis/company/tax-file/")
    suspend fun companyTaxFile(): retrofit2.Response<okhttp3.ResponseBody>

    @Multipart
    @POST("workis/company/tax-file/")
    suspend fun companyTaxFileUpload(@Part file: okhttp3.MultipartBody.Part): SimpleResult

    /** The seller opens its catalogue to its field lead — `{enabled}` (2026-09-15). */
    @POST("workis/company/catalog-helper/")
    suspend fun companyCatalogHelper(@Body body: kotlinx.serialization.json.JsonObject): SimpleResult

    // --- partner seat, slice 2: the order loop (Django §5–§8) — money here is a JSON number ---
    /** One card per order per seat the firm holds. */
    @GET("workis/orders/")
    suspend fun orders(): PartnerOrders

    /** The buyer escrows the total — only while the card says `canFund`. */
    @POST("workis/orders/{id}/fund/")
    suspend fun orderFund(@Path("id") id: String): OrderFundResult

    @POST("workis/orders/lines/{lineId}/received/")
    suspend fun orderLineReceived(@Path("lineId") lineId: String): SimpleResult

    @POST("workis/shipments/{id}/review/")
    suspend fun shipmentReview(@Path("id") id: String, @Body body: ReviewBody): SimpleResult

    // the dispatch doors (2026-09-30) — the PDFs (`orders/{id}/po-pdf/`,
    // `shipments/{id}/label/`) go through `download` with the token
    /** Seller: "Kargoya teslim" — the job opens to the carrier pool on `readyDate`. */
    @POST("workis/orders/{id}/ready/")
    suspend fun orderReady(@Path("id") id: String, @Body body: OrderReadyBody): OrderDoorResult

    @GET("workis/orders/{id}/carrier-options/")
    suspend fun orderCarrierOptions(@Path("id") id: String): CarrierOptions

    /** Buyer: the cargo level; refused once dispatch started. */
    @POST("workis/orders/{id}/service-level/")
    suspend fun orderServiceLevel(@Path("id") id: String, @Body body: ServiceLevelBody): OrderDoorResult

    /** Buyer: the carrier picked by hand. */
    @POST("workis/orders/{id}/dispatch/")
    suspend fun orderDispatch(@Path("id") id: String, @Body body: DispatchBody): OrderDoorResult

    // the two threads — one screen, two doors
    @GET("workis/orders/{id}/thread/")
    suspend fun orderThread(@Path("id") id: String): ThreadPayload

    @GET("workis/invitations/{id}/thread/")
    suspend fun invitationThread(@Path("id") id: String): ThreadPayload

    @POST("workis/orders/{id}/thread/")
    suspend fun orderThreadPost(@Path("id") id: String, @Body body: ThreadPostBody): ThreadPostResult

    @POST("workis/invitations/{id}/thread/")
    suspend fun invitationThreadPost(@Path("id") id: String, @Body body: ThreadPostBody): ThreadPostResult

    /** Polled every 8 s while the screen is open — there is no push channel yet. */
    @GET("workis/orders/{id}/thread/new/")
    suspend fun orderThreadNew(@Path("id") id: String, @Query("after") after: String): ThreadNew

    @GET("workis/invitations/{id}/thread/new/")
    suspend fun invitationThreadNew(@Path("id") id: String, @Query("after") after: String): ThreadNew

    /**
     * A written record is never deleted: the sender's own message leaves the
     * screens while no other seat has seen it, and a trace stays in its place.
     * 400 carries the sentence to show.
     */
    @POST("workis/orders/{id}/thread/{messageId}/retract/")
    suspend fun orderThreadRetract(@Path("id") id: String, @Path("messageId") messageId: String): ThreadRetractResult

    @POST("workis/invitations/{id}/thread/{messageId}/retract/")
    suspend fun invitationThreadRetract(@Path("id") id: String, @Path("messageId") messageId: String): ThreadRetractResult

    /**
     * `{action:"confirm"}` · `{action:"edit", value:"<the sentence>"}` ·
     * `{action:"withdraw"|"decline"}` (the way out of a proposal) — buyer and seller only.
     */
    @POST("workis/orders/{id}/terms/{key}/")
    suspend fun orderTerm(@Path("id") id: String, @Path("key") key: String, @Body body: kotlinx.serialization.json.JsonObject): TermActionResult

    // the order contract
    @GET("workis/orders/{id}/contract/")
    suspend fun orderContract(@Path("id") id: String): ContractResponse

    @POST("workis/orders/{id}/contract/accept/")
    suspend fun orderContractAccept(@Path("id") id: String, @Body body: ContractAcceptBody): ContractAcceptResult

    // Onaylar — nine kinds, one action per card
    @GET("workis/approvals/")
    suspend fun approvals(): Approvals

    @POST("workis/approvals/outward/{mid}/")
    suspend fun approvalOutward(@Path("mid") mid: String, @Body body: kotlinx.serialization.json.JsonObject): SimpleResult

    @POST("workis/orders/{id}/delay-cancel/")
    suspend fun orderDelayCancel(@Path("id") id: String): SimpleResult

    @POST("workis/orders/lines/{lineId}/return/")
    suspend fun lineReturn(@Path("lineId") lineId: String, @Body body: kotlinx.serialization.json.JsonObject): SimpleResult

    @POST("workis/orders/lines/{lineId}/return-decision/")
    suspend fun lineReturnDecision(@Path("lineId") lineId: String, @Body body: kotlinx.serialization.json.JsonObject): SimpleResult

    /** Releases the held payment for the line. */
    @POST("workis/orders/lines/{lineId}/confirm/")
    suspend fun lineConfirm(@Path("lineId") lineId: String): SimpleResult

    @POST("workis/orders/lines/{lineId}/objection/")
    suspend fun lineObjection(@Path("lineId") lineId: String, @Body body: kotlinx.serialization.json.JsonObject): SimpleResult

    @POST("workis/orders/lines/{lineId}/objection-withdraw/")
    suspend fun lineObjectionWithdraw(@Path("lineId") lineId: String): SimpleResult

    @POST("workis/orders/lines/{lineId}/objection-resolve/")
    suspend fun lineObjectionResolve(@Path("lineId") lineId: String, @Body body: kotlinx.serialization.json.JsonObject): SimpleResult

    @POST("workis/orders/lines/{lineId}/warranty-decision/")
    suspend fun lineWarrantyDecision(@Path("lineId") lineId: String, @Body body: kotlinx.serialization.json.JsonObject): SimpleResult

    @GET("workis/geo/provinces/")
    suspend fun geoProvinces(): GeoProvinces

    // --- seller catalogue (Django docs/WORKIS_PARTNER_API.md §4, Phases A + B) — `?seller=` for a coordinator / granted lead ---
    /** Phase C: the seller picker for the console Ürünler door. */
    @GET("workis/console/catalog/sellers/")
    suspend fun consoleCatalogSellers(): CatalogSellers

    @GET("workis/seller/catalog/")
    suspend fun catalog(@Query("seller") seller: String? = null): CatalogPage

    @Multipart
    @POST("workis/seller/catalog/upload/")
    suspend fun catalogUpload(
        @Part file: okhttp3.MultipartBody.Part,
        @PartMap fields: Map<String, @JvmSuppressWildcards okhttp3.RequestBody>,
        @Query("seller") seller: String? = null,
    ): CatalogUploadResult

    @GET("workis/seller/catalog/ingestions/{id}/")
    suspend fun catalogIngestion(@Path("id") id: String, @Query("seller") seller: String? = null): CatalogIngestion

    @GET("workis/seller/catalog/ingestions/{id}/web/")
    suspend fun catalogIngestionWeb(@Path("id") id: String, @Query("seller") seller: String? = null): CatalogWebProgress

    @POST("workis/seller/catalog/ingestions/{id}/web/")
    suspend fun catalogIngestionWebStart(@Path("id") id: String, @Body body: kotlinx.serialization.json.JsonObject, @Query("seller") seller: String? = null): CatalogWebProgress

    // the run doors (2026-09-27) — shown only by the row's can* flag; 409 `{error: code}` (resume / cancel) or a server sentence (delete)
    @POST("workis/seller/catalog/ingestions/{id}/resume/")
    suspend fun catalogIngestionResume(@Path("id") id: String, @Query("seller") seller: String? = null): SimpleResult

    @POST("workis/seller/catalog/ingestions/{id}/cancel/")
    suspend fun catalogIngestionCancel(@Path("id") id: String, @Query("seller") seller: String? = null): SimpleResult

    @POST("workis/seller/catalog/ingestions/{id}/delete/")
    suspend fun catalogIngestionDelete(@Path("id") id: String, @Query("seller") seller: String? = null): SimpleResult

    /** `{sections:[n…]}` | `{all:true}` → each ticked section becomes its own child row. */
    @POST("workis/seller/catalog/ingestions/{id}/sections/")
    suspend fun catalogIngestionSections(@Path("id") id: String, @Body body: kotlinx.serialization.json.JsonObject, @Query("seller") seller: String? = null): SimpleResult

    /** `{success, family:{…}}` on prod — decoded by `decodeFamily`. */
    @GET("workis/seller/catalog/families/{id}/")
    suspend fun catalogFamily(@Path("id") id: String, @Query("seller") seller: String? = null): kotlinx.serialization.json.JsonObject

    @POST("workis/seller/catalog/families/{id}/approve/")
    suspend fun catalogFamilyApprove(@Path("id") id: String, @Query("seller") seller: String? = null): SimpleResult

    @POST("workis/seller/catalog/families/{id}/reject/")
    suspend fun catalogFamilyReject(@Path("id") id: String, @Query("seller") seller: String? = null): SimpleResult

    @POST("workis/seller/catalog/families/{id}/withdraw/")
    suspend fun catalogFamilyWithdraw(@Path("id") id: String, @Query("seller") seller: String? = null): SimpleResult

    /** name · description · specDel · variantAdd · per-row label/price/currency/delete → the full family. */
    @POST("workis/seller/catalog/families/{id}/cell/")
    suspend fun catalogFamilyCell(@Path("id") id: String, @Body body: kotlinx.serialization.json.JsonObject, @Query("seller") seller: String? = null): kotlinx.serialization.json.JsonObject

    @Multipart
    @POST("workis/seller/catalog/families/{id}/image/")
    suspend fun catalogFamilyImage(@Path("id") id: String, @Part image: okhttp3.MultipartBody.Part, @Query("seller") seller: String? = null): SimpleResult

    /** One gallery item: `primary` (images only) · `retire` · `note` ({note} only for note). Serves proposed and approved families alike. */
    @POST("workis/seller/catalog/families/{id}/media/{mediaId}/{action}/")
    suspend fun catalogFamilyMediaAction(
        @Path("id") id: String,
        @Path("mediaId") mediaId: String,
        @Path("action") action: String,
        @Body body: kotlinx.serialization.json.JsonObject,
        @Query("seller") seller: String? = null,
    ): SimpleResult

    /** Adds to the gallery: a photo from the device … */
    @Multipart
    @POST("workis/seller/catalog/families/{id}/media/")
    suspend fun catalogFamilyMediaUpload(
        @Path("id") id: String,
        @Part image: okhttp3.MultipartBody.Part,
        @Query("seller") seller: String? = null,
    ): SimpleResult

    /** … or a YouTube link. */
    @POST("workis/seller/catalog/families/{id}/media/")
    suspend fun catalogFamilyMediaLink(
        @Path("id") id: String,
        @Body body: kotlinx.serialization.json.JsonObject,
        @Query("seller") seller: String? = null,
    ): SimpleResult

    @POST("workis/seller/catalog/families/{id}/web/")
    suspend fun catalogFamilyWeb(@Path("id") id: String, @Body body: kotlinx.serialization.json.JsonObject, @Query("seller") seller: String? = null): SimpleResult

    @POST("workis/seller/catalog/families/{id}/teach/")
    suspend fun catalogFamilyTeach(@Path("id") id: String, @Body body: kotlinx.serialization.json.JsonObject, @Query("seller") seller: String? = null): CatalogTeachResult

    /** A product with no catalogue → `{family}`. */
    @POST("workis/seller/catalog/families/")
    suspend fun catalogFamilyCreate(@Body body: kotlinx.serialization.json.JsonObject, @Query("seller") seller: String? = null): kotlinx.serialization.json.JsonObject

    @POST("workis/seller/catalog/offers/{id}/cell/")
    suspend fun catalogOfferCell(@Path("id") id: String, @Body body: kotlinx.serialization.json.JsonObject, @Query("seller") seller: String? = null): SimpleResult

    @GET("workis/lists/")
    suspend fun lists(@Query("archived") archived: Int? = null): PurchaseListIndex

    // archive a draft (never delete), bring it back, retry a stuck one — 409 carries the sentence
    @POST("workis/lists/{id}/archive/")
    suspend fun listArchive(@Path("id") id: String): ListActionResult

    @POST("workis/lists/{id}/unarchive/")
    suspend fun listUnarchive(@Path("id") id: String): ListActionResult

    @POST("workis/lists/{id}/retry/")
    suspend fun listRetry(@Path("id") id: String): ListActionResult

    @Multipart
    @POST("workis/lists/upload/")
    suspend fun listUpload(@Part file: okhttp3.MultipartBody.Part): ListCreated

    @POST("workis/lists/sample/")
    suspend fun listSample(): ListCreated

    @GET("workis/lists/{id}/")
    suspend fun listDetail(@Path("id") id: String): PurchaseListDetail

    @POST("workis/lists/{id}/items/")
    suspend fun listAddItem(@Path("id") id: String, @Body body: kotlinx.serialization.json.JsonObject): SimpleResult

    @POST("workis/lists/items/{id}/bind/")
    suspend fun listItemBind(@Path("id") id: String, @Body body: BindBody): SimpleResult

    @POST("workis/lists/items/{id}/delete/")
    suspend fun listItemDelete(@Path("id") id: String): SimpleResult

    @GET("workis/lists/items/{id}/search/")
    suspend fun listItemSearch(@Path("id") id: String, @Query("q") q: String): PLSearchResults

    @POST("workis/lists/{id}/note/")
    suspend fun listNote(@Path("id") id: String, @Body body: NoteBody): SimpleResult

    @POST("workis/lists/{id}/request-quotes/")
    suspend fun listRequestQuotes(@Path("id") id: String, @Body body: RequestQuotesBody): RequestQuotesResult

    @POST("workis/lists/{id}/chase/")
    suspend fun listChase(@Path("id") id: String): SimpleResult

    @POST("workis/lists/{id}/award/")
    suspend fun listAward(@Path("id") id: String): AwardResult

    /** `{selections: {itemId: {skip: true}}}` leaves those items on the list (2026-10-01); at least one is ordered (400). */
    @POST("workis/lists/{id}/award/")
    suspend fun listAwardSelections(@Path("id") id: String, @Body body: kotlinx.serialization.json.JsonObject): AwardResult

    @GET("workis/seller/invitations/")
    suspend fun sellerInvitations(): SellerInvitations

    @POST("workis/seller/invitations/{id}/quote/")
    suspend fun sellerQuote(@Path("id") id: String, @Body body: kotlinx.serialization.json.JsonObject): QuoteSubmitResult

    @Streaming
    @POST("workis/seller/invitations/{id}/quote/preview/")
    suspend fun sellerQuotePreview(@Path("id") id: String, @Body body: kotlinx.serialization.json.JsonObject): okhttp3.ResponseBody

    @POST("workis/seller/invitations/{id}/quote/revise/")
    suspend fun sellerQuoteRevise(@Path("id") id: String): SimpleResult

    /** The quote form as typed, kept SERVER-side (a colleague seat sees it) → `{draft, at}`; the stored draft is snake_case. */
    @GET("workis/seller/invitations/{id}/quote/draft/")
    suspend fun sellerQuoteDraft(@Path("id") id: String): kotlinx.serialization.json.JsonObject

    /** Autosave with the send body; Send clears it server-side. */
    @POST("workis/seller/invitations/{id}/quote/draft/")
    suspend fun sellerQuoteDraftSave(@Path("id") id: String, @Body body: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject

    /** Jowi's rewrite of a note / letter — a suggestion the user applies or ignores (never automatic). */
    @POST("workis/text/polish/")
    suspend fun textPolish(@Body body: TextBody): PolishResult

    // --- Ekip: a firm's colleagues (API_CONTRACT "Team seats"); dark while the switch is off ---
    @GET("workis/team/")
    suspend fun team(): Team

    @POST("workis/team/invite/")
    suspend fun teamInvite(@Body body: TeamInviteBody): Team

    @POST("workis/team/invitations/{id}/cancel/")
    suspend fun teamInvitationCancel(@Path("id") id: String): Team

    @POST("workis/team/seats/{userId}/remove/")
    suspend fun teamSeatRemove(@Path("userId") userId: String): Team

    /** Admin seat only; the admin seat's OWN scope can never be changed (400 `admin_scope`). */
    @POST("workis/team/seats/{userId}/scope/")
    suspend fun teamSeatScope(@Path("userId") userId: String, @Body body: TeamScopeBody): Team

    /** The person's own invitations — one of the writes a view seat may still make. */
    @GET("workis/team/invitations/mine/")
    suspend fun myInvitations(): MyInvitations

    @POST("workis/team/invitations/{id}/accept/")
    suspend fun acceptInvitation(@Path("id") id: String, @Body body: InvitationAcceptBody): InvitationAcceptResult

    // --- "the missing doors" (2026-09-30): badges · Cari · Takvim · the carrier seat — money a JSON number ---
    /** The web menu poll verbatim; read once per Pazar visit, no timer. */
    @GET("workis/badges/")
    suspend fun badges(): PartnerBadges

    @GET("workis/ledger/")
    suspend fun ledger(): LedgerReply

    @GET("workis/calendar/")
    suspend fun calendar(): CalendarBoard

    /** Carrier seat only: open jobs + own shipments (§7.1 — never the goods' prices). */
    @GET("workis/cargo/")
    suspend fun cargoBoard(): CargoBoard

    /** First accept wins — 409 carries the server's "somebody was faster". */
    @POST("workis/cargo/jobs/{orderId}/accept/")
    suspend fun cargoAccept(@Path("orderId") orderId: String): CargoActionResult

    /** `status` only from the shipment's `nextStatuses`; delivery is never this door. */
    @POST("workis/cargo/shipments/{id}/status/")
    suspend fun cargoStatus(@Path("id") id: String, @Body body: CargoStatusBody): CargoActionResult

    /** Before pickup only (`canDrop`). */
    @POST("workis/cargo/shipments/{id}/drop/")
    suspend fun cargoDrop(@Path("id") id: String, @Body body: CargoDropBody): CargoActionResult

    /** The label's QR is the WSH number; 403 unless the shipment is the carrier's own. */
    @GET("workis/cargo/scan/")
    suspend fun cargoScan(@Query("code") code: String): CargoScanReply

    /** The receiver's 6-digit code closes the delivery. */
    @POST("workis/shipments/{id}/deliver/")
    suspend fun cargoDeliver(@Path("id") id: String, @Body body: CargoDeliverBody): CargoActionResult

    // --- what moved (2026-10-03): one aggregate reading, compared per scope — `ChangeStamps` ---
    @GET("workis/changes/")
    suspend fun changes(): ChangeStampsReply

    // --- §10.2 agreement re-acceptance (every seat) ---
    @GET("workis/agreement/pending/")
    suspend fun agreementPending(): AgreementPending

    @POST("workis/agreement/accept/")
    suspend fun agreementAccept(@Body body: AcceptAgreementBody): SimpleResult

    // --- ask jowi (seat-aware; one AskThread per user, shared with the web drawer) ---
    @POST("workis/ask/")
    suspend fun ask(@Body body: AskBody): AskResponse

    /** Compose-time rewrite — the caller falls back to the typed text on failure/timeout. */
    @POST("workis/ask/preview/")
    suspend fun askPreview(@Body body: AskPreviewBody): AskPreview

    @GET("workis/ask/history/")
    suspend fun askHistory(): AskHistory

    /** The archive, paged backwards: `before` = the previous page's `nextBefore`, null on the first page. */
    @GET("workis/ask/history/?archived=1")
    suspend fun askHistoryArchived(@Query("before") before: String?, @Query("limit") limit: Int = 40): AskHistory

    /** "Arşivle": nothing is deleted — the open history moves to the archive and a new one starts. */
    @POST("workis/ask/history/archive/")
    suspend fun askHistoryArchive(): SimpleResult

    // a written record is never deleted (2026-09-18): a turn is WITHDRAWN into a
    // stub in its place, and can come back
    @POST("workis/ask/history/{id}/hide/")
    suspend fun askHide(@Path("id") id: String): AskHideResult

    @POST("workis/ask/history/{id}/unhide/")
    suspend fun askUnhide(@Path("id") id: String): AskUnhideResult

    /** 👍/👎 on a knowledge-backed answer; first verdict wins (rated: false afterwards). */
    @POST("workis/ask/like/")
    suspend fun askLike(@Body body: AskLikeBody): AskLikeResult

    /** The earnings guide as Markdown sections (public). */
    @GET("workis/expert/guide/")
    suspend fun expertGuide(): ExpertGuide
}
