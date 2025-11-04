package za.tws.scan.net

import retrofit2.http.*

// ---------- API SERVICE ----------
interface ApiService {

    // ---------- AUTH ----------
    @Headers("Content-Type: application/json")
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse


    // ---------- PICKING ----------
    @GET("picking/health")
    suspend fun health(): Map<String, Any>

    @POST("picking/start")
    suspend fun startPick(
        @Query("userId") userId: Int
    ): PickingSession

    @POST("picking/add-scan")
    suspend fun addScan(
        @Query("sessionId") sessionId: Int,
        @Query("barcodeOrSerial") barcodeOrSerial: String,
        @Query("qty") qty: Int = 1
    ): ScanItem

    @GET("picking/{sessionId}/recent")
    suspend fun recentScans(
        @Path("sessionId") sessionId: Int,
        @Query("top") top: Int = 25
    ): RecentScansResponse

    @POST("picking/complete")
    suspend fun completePick(
        @Query("sessionId") sessionId: Int
    ): CompletePickResponse


    // ---------- STAGING (Pick → Pack bridge) ----------
    @POST("staging/from-pick/{sessionId}")
    suspend fun stageFromPick(
        @Path("sessionId") sessionId: Int
    ): Map<String, Any>

    @POST("staging/claim-next")
    suspend fun claimNext(
        @Query("packedBy") packedBy: Int,
        @Query("packageNumber") packageNumber: String? = null
    ): Map<String, Any>

    @GET("staging/{stagingId}/lines")
    suspend fun stagingGetLines(
        @Path("stagingId") stagingId: Int
    ): List<Map<String, Any>>

    @POST("staging/consume")
    suspend fun consumeStaging(
        @Query("stagingId") stagingId: Int
    ): Map<String, Any>

    @POST("staging/release")
    suspend fun releaseStaging(
        @Query("stagingId") stagingId: Int
    ): Map<String, Any>


    // ---------- PACKING ----------
    @POST("packing/start-or-set")
    suspend fun packStartOrSet(
        @Query("packageNumber") packageNumber: String? = null
    ): Map<String, Any>

    @POST("packing/add-item")
    suspend fun packAddItem(
        @Query("packingId") packingId: Int,
        @Query("barcodeOrSerial") barcodeOrSerial: String,
        @Query("qty") qty: Int = 1
    ): Map<String, Any>

    @GET("packing/items")
    suspend fun packGetItems(
        @Query("packingId") packingId: Int
    ): List<Map<String, Any>>

    @POST("packing/undo-last")
    suspend fun packUndoLast(
        @Query("packingId") packingId: Int
    ): Map<String, Any>

    @POST("packing/clear")
    suspend fun packClear(
        @Query("packingId") packingId: Int
    ): Map<String, Any>

    @POST("packing/seal")
    suspend fun packSeal(
        @Query("packingId") packingId: Int
    ): Map<String, Any>

    @GET("packing/validate")
    suspend fun packValidate(
        @Query("packingId") packingId: Int
    ): ValidationResponse


    // ---------- STOCK TAKING ----------
    @GET("stock/health")
    suspend fun stockHealth(): Map<String, Any>

    @POST("stock/start")
    suspend fun stockStart(
        @Query("userId") userId: Int,
        @Query("name") name: String? = null
    ): StockSession

    @GET("stock/{stockTakeId}/items")
    suspend fun stockListItems(
        @Path("stockTakeId") stockTakeId: Int,
        @Query("search") search: String? = null
    ): StockListResponse

    @POST("stock/add")
    suspend fun stockAdd(
        @Query("stockTakeId") stockTakeId: Int,
        @Query("barcodeOrSku") barcodeOrSku: String,
        @Query("qty") qty: Int = 1
    ): StockItemUpdate

    @POST("stock/{stockTakeId}/undo-last")
    suspend fun stockUndoLast(
        @Path("stockTakeId") stockTakeId: Int
    ): StockItemUpdate

    @POST("stock/{stockTakeId}/finish")
    suspend fun stockFinish(
        @Path("stockTakeId") stockTakeId: Int
    ): StockFinishResponse


    // ---------- DELIVERY ----------
    @GET("delivery/health")
    suspend fun deliveryHealth(): Map<String, Any>

    /**
     * GET /delivery/list
     * Query params:
     *  - search: e.g. "PKG-10"
     *  - status: "To Load" | "Loaded" (optional)
     *  - top: default 100
     */
    @GET("delivery/list")
    suspend fun deliveryList(
        @Query("search") search: String? = null,
        @Query("status") status: String? = null,
        @Query("top") top: Int = 100
    ): DeliveryListResponse

    /**
     * GET /delivery/{packageNumber}
     * Returns details for a single package (for the bottom sheet).
     */
    @GET("delivery/{packageNumber}")
    suspend fun deliveryGetPackage(
        @Path("packageNumber") packageNumber: String
    ): DeliveryPackageNet

    /**
     * POST /delivery/{packageNumber}/mark-loaded
     * Marks a package as Loaded and returns the updated row.
     */
    @POST("delivery/{packageNumber}/mark-loaded")
    suspend fun deliveryMarkLoaded(
        @Path("packageNumber") packageNumber: String
    ): DeliveryPackageNet

    /**
     * POST /delivery/{packageNumber}/mark-to-load
     * Reverts a package back to 'To Load' and returns the updated row.
     */
    @POST("delivery/{packageNumber}/mark-to-load")
    suspend fun deliveryMarkToLoad(
        @Path("packageNumber") packageNumber: String
    ): DeliveryPackageNet

    /**
     * POST /delivery/scan-to-load
     * Body: { "scannedNumber": "PKG-10023" }
     * Quick-scan handler that immediately marks loaded.
     */
    @POST("delivery/scan-to-load")
    suspend fun deliveryScanToLoad(
        @Body body: ScanToLoadRequest
    ): DeliveryPackageNet


    @POST("delivery/{packageNumber}/mark-delivered")
    suspend fun deliveryMarkDelivered(
        @Path("packageNumber") packageNumber: String
    ): DeliveryPackageNet
}