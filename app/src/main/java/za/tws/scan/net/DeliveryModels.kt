package za.tws.scan.net

// --- Network models (match FastAPI/SQL) ---

/**
 * A single delivery package row from:
 * - GET /delivery/list           (items[])
 * - GET /delivery/{pkg}          (single object)
 * - POST /delivery/{pkg}/mark-loaded
 * - POST /delivery/{pkg}/mark-to-load
 * - POST /delivery/{pkg}/mark-delivered
 * - POST /delivery/scan-to-load
 *
 * Backed by usp_Delivery_* procs.
 */
data class DeliveryPackageNet(
    val DeliveryPackageId: Int? = null,
    val PackageNumber: String? = null,
    val Status: String? = null,            // "To Load" | "Loaded" | "Delivered"
    val DeliveryId: Int? = null,
    val Destination: String? = null,       // e.g. "Warehouse B, Johannesburg"
    val Driver: String? = null,
    val CreatedAt: String? = null,         // keep as ISO string
    val UpdatedAt: String? = null          // optional; useful for Delivered timestamp
)

/**
 * The second result set from dbo.usp_Delivery_ListPackages
 * surfaced by GET /delivery/list as "counts".
 */
data class DeliveryCountsNet(
    val Total: Int? = 0,
    val ToLoad: Int? = 0,
    val Loaded: Int? = 0,
    val Delivered: Int? = 0
)

/**
 * Full response of GET /delivery/list
 */
data class DeliveryListResponse(
    val items: List<DeliveryPackageNet> = emptyList(),
    val counts: DeliveryCountsNet = DeliveryCountsNet()
)

/**
 * Body for POST /delivery/scan-to-load
 */
data class ScanToLoadRequest(
    val scannedNumber: String
)

/**
 * Body for POST /delivery/scan-to-deliver
 * (optional — future use)
 */
data class ScanToDeliverRequest(
    val scannedNumber: String
)


// --- App/UI models (stable, enum-typed) ---

/**
 * Normalised status used across the app.
 */
enum class DeliveryStatus {
    TO_LOAD,
    LOADED,
    DELIVERED,
    UNKNOWN;

    companion object {
        fun from(raw: String?): DeliveryStatus = when (raw?.trim()) {
            "To Load"    -> TO_LOAD
            "Loaded"     -> LOADED
            "Delivered"  -> DELIVERED
            else          -> UNKNOWN
        }

        /** Convert enum back to DB strings (if needed for requests). */
        fun toDb(status: DeliveryStatus): String = when (status) {
            TO_LOAD    -> "To Load"
            LOADED     -> "Loaded"
            DELIVERED  -> "Delivered"
            UNKNOWN    -> "To Load" // sensible default
        }
    }
}

/**
 * UI-friendly model for the RecyclerView & bottom sheet.
 */
data class DeliveryItem(
    val id: Int,
    val packageNumber: String,
    val status: DeliveryStatus,
    val destination: String,
    val deliveryId: Int?,
    val driver: String?,
    val createdAt: String?,
    val updatedAt: String?
)

// --- Mapping helpers ---

fun DeliveryPackageNet.toUi(): DeliveryItem =
    DeliveryItem(
        id = this.DeliveryPackageId ?: -1,
        packageNumber = this.PackageNumber.orEmpty(),
        status = DeliveryStatus.from(this.Status),
        destination = this.Destination.orEmpty(),
        deliveryId = this.DeliveryId,
        driver = this.Driver,
        createdAt = this.CreatedAt,
        updatedAt = this.UpdatedAt
    )

fun List<DeliveryPackageNet>.toUiList(): List<DeliveryItem> = map { it.toUi() }