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

    // --- ask jowi ---
    @POST("workis/ask/")
    suspend fun ask(@Body body: AskBody): AskResponse
}
