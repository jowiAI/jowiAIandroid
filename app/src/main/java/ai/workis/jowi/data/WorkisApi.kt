package ai.workis.jowi.data

import retrofit2.http.Body
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

    @POST("auth/login/")
    suspend fun login(@Body body: LoginBody): AuthResponse

    @POST("auth/validate-token/")
    suspend fun validateToken(): AuthResponse

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

    @POST("workis/ask/history/clear/")
    suspend fun askHistoryClear(): SimpleResult

    /** 👍/👎 on a knowledge-backed answer; first verdict wins (rated: false afterwards). */
    @POST("workis/ask/like/")
    suspend fun askLike(@Body body: AskLikeBody): AskLikeResult

    /** The earnings guide as Markdown sections (public). */
    @GET("workis/expert/guide/")
    suspend fun expertGuide(): ExpertGuide
}
