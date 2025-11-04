package za.tws.scan.net

// -----------------------------
// STOCK TAKING MODELS (API-aligned, null-safe)
// -----------------------------

// 1) Represents a single stock-take session (/stock/start, finish header)
data class StockSession(
    val StockTakeId: Int,
    val Name: String? = null,
    val Status: String? = null,
    val CreatedBy: Int? = null,
    val CreatedAt: String? = null
)

// 2) Represents one product line within a stock-take
//    Returned by: /stock/{id}/items (list), /stock/add (single), /stock/{id}/undo-last (single)
data class StockItem(
    val StockTakeItemId: Int? = null,
    val StockTakeId: Int? = null,
    val ProductId: Int? = null,
    val Sku: String? = null,
    val Name: String? = null,
    val ExpectedQty: Int? = null,
    val CountedQty: Int? = null
)

// 3) Wrapper for /stock/{id}/items API
data class StockListResponse(
    val items: List<StockItem> = emptyList()
)

// 4) Response returned after calling /stock/add or /stock/{id}/undo-last
//    (API returns a single updated item)
typealias StockItemUpdate = StockItem

// 5) The /stock/{id}/finish API returns 3 result sets:
//    - header: StockSession
//    - totals: StockTotals
//    - discrepancies: List<StockDiscrepancy>
data class StockTotals(
    val Items: Int? = null,
    val TotalExpected: Int? = null,
    val TotalCounted: Int? = null,
    val TotalVariance: Int? = null,
    val MismatchedItems: Int? = null
)

data class StockDiscrepancy(
    val Sku: String? = null,
    val Name: String? = null,
    val ExpectedQty: Int? = null,
    val CountedQty: Int? = null,
    val Variance: Int? = null
)

data class StockFinishResponse(
    val header: StockSession? = null,
    val totals: StockTotals? = null,
    val discrepancies: List<StockDiscrepancy> = emptyList()
)