package za.tws.scan.net

data class PickingSession(
    val SessionId: Int,
    val UserId: Int,
    val StartedAt: String,
    val Status: String
)

data class ScanItem(
    val ScanId: Int,
    val BarcodeOrSerial: String,
    val Qty: Int,
    val ScannedAt: String
)

data class RecentScansResponse(
    val items: List<ScanItem>
)

data class CompletePickResponse(
    val ok: Boolean,
    val summary: List<Map<String, Any>>?
)

data class ConsumeResponse(
    val items: List<Map<String, Any>>
)

data class ValidationIssue(
    val Issue: String,      // "Missing" | "Over" | "Extra"
    val ProductId: Int?,
    val Sku: String?,
    val Name: String?,
    val Required: Int?,
    val Packed: Int?,
    val Delta: Int?
)

data class ValidationResponse(
    val ok: Boolean,
    val issues: List<ValidationIssue>
)