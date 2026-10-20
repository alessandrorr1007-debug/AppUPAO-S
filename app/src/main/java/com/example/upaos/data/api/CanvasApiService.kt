package com.example.upaos.data.api

import com.example.upaos.data.model.CanvasAssignment
import com.example.upaos.data.model.CanvasConversation
import com.example.upaos.data.model.CanvasCourse
import com.example.upaos.data.model.CanvasDiscussionTopic
import com.example.upaos.data.model.CanvasFileUploadInitResponse
import com.example.upaos.data.model.CanvasGroup
import com.example.upaos.data.model.CanvasModule
import com.example.upaos.data.model.CanvasNotificationItem
import com.example.upaos.data.model.CanvasTodoItem
import com.example.upaos.data.model.CanvasUser
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

import com.example.upaos.data.model.CanvasFileDetail
import com.example.upaos.data.model.CanvasTab
import retrofit2.http.Url

interface CanvasApiService {

    @GET
    suspend fun getListPage(@Header("Authorization") authHeader: String, @Url pageUrl: String): Response<List<com.google.gson.JsonObject>>

    @GET("api/v1/courses/{courseId}/modules/{moduleId}/items")
    suspend fun getModuleItems(@Header("Authorization") authHeader: String,
        @Path("courseId") courseId: Long, @Path("moduleId") moduleId: Long,
        @Query("per_page") perPage: Int = 100, @Query("page") page: Int = 1): Response<List<com.example.upaos.data.model.CanvasModuleItem>>

    @GET("api/v1/users/self")
    suspend fun getUserSelf(
        @Header("Authorization") authHeader: String
    ): Response<CanvasUser>

    @GET("api/v1/users/self/todo")
    suspend fun getTodoList(
        @Header("Authorization") authHeader: String,
        @Query("per_page") perPage: Int = 100
    ): Response<List<CanvasTodoItem>>

    @GET("api/v1/courses")
    suspend fun getActiveCourses(
        @Header("Authorization") authHeader: String,
        @Query("enrollment_state") state: String = "active",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): Response<List<CanvasCourse>>

    @GET("api/v1/conversations")
    suspend fun getConversations(
        @Header("Authorization") authHeader: String,
        @Query("per_page") perPage: Int = 100
    ): Response<List<CanvasConversation>>

    @GET("api/v1/conversations/{id}")
    suspend fun getConversationDetail(
        @Header("Authorization") authHeader: String,
        @Path("id") conversationId: Long
    ): Response<CanvasConversation>

    @GET("api/v1/courses/{courseId}/modules")
    suspend fun getCourseModules(
        @Header("Authorization") authHeader: String,
        @Path("courseId") courseId: Long,
        @Query("include[]") include: String = "items",
        @Query("per_page") perPage: Int = 100
    ): Response<List<CanvasModule>>

    @GET
    suspend fun getFileByUrl(
        @Header("Authorization") authHeader: String,
        @Url fileUrl: String
    ): Response<CanvasFileDetail>

    @GET("api/v1/courses/{courseId}/files/{fileId}")
    suspend fun getCourseFile(
        @Header("Authorization") authHeader: String,
        @Path("courseId") courseId: Long,
        @Path("fileId") fileId: Long
    ): Response<CanvasFileDetail>

    @GET("api/v1/courses/{courseId}/assignments?include[]=submission&include[]=all_dates&include[]=overrides")
    suspend fun getCourseAssignments(
        @Header("Authorization") authHeader: String,
        @Path("courseId") courseId: Long,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): Response<List<CanvasAssignment>>

    @GET("api/v1/users/self/activity_stream")
    suspend fun getActivityStream(
        @Header("Authorization") authHeader: String,
        @Query("per_page") perPage: Int = 50
    ): Response<List<CanvasNotificationItem>>

    @GET("api/v1/users/self/groups")
    suspend fun getSelfGroups(
        @Header("Authorization") authHeader: String,
        @Query("per_page") perPage: Int = 50
    ): Response<List<CanvasGroup>>

    @GET("api/v1/groups/{groupId}/users")
    suspend fun getGroupUsers(
        @Header("Authorization") authHeader: String,
        @Path("groupId") groupId: Long,
        @Query("per_page") perPage: Int = 100
    ): Response<List<CanvasUser>>

    @GET("api/v1/courses/{courseId}/discussion_topics")
    suspend fun getCourseDiscussions(
        @Header("Authorization") authHeader: String,
        @Path("courseId") courseId: Long,
        @Query("per_page") perPage: Int = 50
    ): Response<List<CanvasDiscussionTopic>>

    @GET("api/v1/courses/{courseId}/users")
    suspend fun getCourseUsers(
        @Header("Authorization") authHeader: String,
        @Path("courseId") courseId: Long,
        @Query("include[]") include: String = "enrollments",
        @Query("per_page") perPage: Int = 100
    ): Response<List<CanvasUser>>

    @GET("api/v1/courses/{courseId}/groups")
    suspend fun getCourseGroups(
        @Header("Authorization") authHeader: String,
        @Path("courseId") courseId: Long,
        @Query("per_page") perPage: Int = 100
    ): Response<List<CanvasGroup>>

    @GET("api/v1/courses/{courseId}/files")
    suspend fun getCourseFiles(
        @Header("Authorization") authHeader: String,
        @Path("courseId") courseId: Long,
        @Query("sort") sort: String = "updated_at",
        @Query("order") order: String = "desc",
        @Query("per_page") perPage: Int = 100
    ): Response<List<CanvasFileDetail>>

    @GET("api/v1/courses/{courseId}")
    suspend fun getCourseDetail(
        @Header("Authorization") authHeader: String,
        @Path("courseId") courseId: Long,
        @Query("include[]") include: String = "syllabus_body"
    ): Response<CanvasCourse>

    @GET("api/v1/courses/{courseId}/tabs")
    suspend fun getCourseTabs(
        @Header("Authorization") authHeader: String,
        @Path("courseId") courseId: Long,
        @Query("include[]") include: String = "external",
        @Query("per_page") perPage: Int = 100
    ): Response<List<CanvasTab>>

    @FormUrlEncoded
    @POST("api/v1/courses/{courseId}/assignments/{assignmentId}/submissions/self/files")
    suspend fun initSubmissionFileUpload(
        @Header("Authorization") authHeader: String,
        @Path("courseId") courseId: Long,
        @Path("assignmentId") assignmentId: Long,
        @Field("name") name: String,
        @Field("size") size: Long
    ): Response<CanvasFileUploadInitResponse>

    @FormUrlEncoded
    @POST("api/v1/courses/{courseId}/assignments/{assignmentId}/submissions")
    suspend fun submitAssignment(
        @Header("Authorization") authHeader: String,
        @Path("courseId") courseId: Long,
        @Path("assignmentId") assignmentId: Long,
        @Field("submission[submission_type]") submissionType: String = "online_upload",
        @Field("submission[file_ids][]") fileId: Long
    ): Response<CanvasAssignment>
}

object CanvasApiClient {
    private const val BASE_URL = "https://upao.instructure.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    private val destinationGuard = okhttp3.Interceptor { chain ->
        val request = chain.request()
        if (request.header("Authorization") != null && !TrustedServers.isCanvas(request.url)) {
            throw java.io.IOException("Destino de Canvas no autorizado")
        }
        chain.proceed(request)
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(destinationGuard)
        .addNetworkInterceptor(destinationGuard)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(35, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    val pagedApiService: CanvasApiService by lazy { CanvasPagedApi(apiService) }

    val apiService: CanvasApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CanvasApiService::class.java)
    }
}
