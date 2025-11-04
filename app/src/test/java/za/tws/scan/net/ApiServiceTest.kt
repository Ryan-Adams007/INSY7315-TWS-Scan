package za.tws.scan.net

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.URLDecoder

@OptIn(ExperimentalCoroutinesApi::class)
class ApiServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ApiService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ApiService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ---------------- DELIVERY ----------------

    @Test
    fun delivery_list_includes_query_params_and_parses_counts() = runTest {
        val body = """
            {
              "items": [
                {
                  "DeliveryPackageId": 1,
                  "PackageNumber": "PKG-000001",
                  "Status": "To Load",
                  "DeliveryId": null,
                  "Destination": "Warehouse B, Johannesburg",
                  "Driver": null,
                  "CreatedAt": "2025-11-01T10:11:12Z"
                }
              ],
              "counts": { "Total": 12, "ToLoad": 9, "Loaded": 3 }
            }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val resp = api.deliveryList(search = "PKG-1", status = "To Load", top = 50)

        val recorded = server.takeRequest()
        assertEquals("/delivery/list?search=PKG-1&status=To%20Load&top=50", recorded.path)

        assertEquals(1, resp.items.size)
        assertEquals("PKG-000001", resp.items[0].PackageNumber)
        assertEquals(12, resp.counts.Total)
        assertEquals(9, resp.counts.ToLoad)
        assertEquals(3, resp.counts.Loaded)
    }

    @Test
    fun delivery_get_package_parses() = runTest {
        val body = """
            {
              "DeliveryPackageId": 42,
              "PackageNumber": "PKG-000042",
              "Status": "Loaded",
              "DeliveryId": 7,
              "Destination": "Pretoria Hub",
              "Driver": "Sipho",
              "CreatedAt": "2025-10-31T08:00:00Z"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val resp = api.deliveryGetPackage("PKG-000042")

        val recorded = server.takeRequest()
        assertEquals("/delivery/PKG-000042", recorded.path)
        assertEquals("PKG-000042", resp.PackageNumber)
        assertEquals("Loaded", resp.Status)
        assertEquals("Pretoria Hub", resp.Destination)
    }

    @Test
    fun delivery_mark_loaded_hits_correct_path_and_parses() = runTest {
        val body = """
            {
              "DeliveryPackageId": 2,
              "PackageNumber": "PKG-000002",
              "Status": "Loaded",
              "DeliveryId": null,
              "Destination": null,
              "Driver": null,
              "CreatedAt": "2025-11-01T12:00:00Z"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val resp = api.deliveryMarkLoaded("PKG-000002")

        val recorded = server.takeRequest()
        assertEquals("/delivery/PKG-000002/mark-loaded", recorded.path)
        assertEquals("Loaded", resp.Status)
    }

    @Test
    fun delivery_mark_to_load_path_and_parses() = runTest {
        val body = """
            {
              "DeliveryPackageId": 2,
              "PackageNumber": "PKG-000002",
              "Status": "To Load",
              "DeliveryId": null,
              "Destination": null,
              "Driver": null,
              "CreatedAt": "2025-11-01T12:00:00Z"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val resp = api.deliveryMarkToLoad("PKG-000002")

        val recorded = server.takeRequest()
        assertEquals("/delivery/PKG-000002/mark-to-load", recorded.path)
        assertEquals("To Load", resp.Status)
    }

    @Test
    fun delivery_scan_to_load_sends_body_and_parses() = runTest {
        val body = """
            {
              "DeliveryPackageId": 3,
              "PackageNumber": "PKG-000003",
              "Status": "Loaded",
              "DeliveryId": null,
              "Destination": null,
              "Driver": null,
              "CreatedAt": "2025-11-01T12:00:00Z"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val req = ScanToLoadRequest(scannedNumber = "PKG-000003")
        val resp = api.deliveryScanToLoad(req)

        val recorded = server.takeRequest()
        assertEquals("/delivery/scan-to-load", recorded.path)
        val sentJson = recorded.body.readUtf8().trim()
        assertTrue(sentJson.contains("\"scannedNumber\":\"PKG-000003\""))
        assertEquals("Loaded", resp.Status)
    }

    @Test
    fun delivery_mark_delivered_path_and_parses() = runTest {
        val body = """
            {
              "DeliveryPackageId": 9,
              "PackageNumber": "PKG-000009",
              "Status": "Delivered",
              "DeliveryId": 1,
              "Destination": "Cape Town DC",
              "Driver": "Thabo",
              "CreatedAt": "2025-11-01T12:00:00Z"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val resp = api.deliveryMarkDelivered("PKG-000009")

        val recorded = server.takeRequest()
        assertEquals("/delivery/PKG-000009/mark-delivered", recorded.path)
        assertEquals("Delivered", resp.Status)
    }

    // ---------------- PACKING ----------------

    @Test
    fun packing_start_or_set_generates_when_null() = runTest {
        val body = """
            {
              "PackingId": 12,
              "PackageNumber": "PKG-000012",
              "Status": "Open",
              "CreatedAt": "2025-11-01T10:00:00Z"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val res = api.packStartOrSet(null)

        val recorded = server.takeRequest()
        assertEquals("/packing/start-or-set", recorded.path)
        assertEquals("PKG-000012", res["PackageNumber"])
    }

    @Test
    fun packing_add_item_includes_query_params() = runTest {
        val body = """
            {
              "PackingItemId": 1,
              "PackingId": 99,
              "ProductId": 1001,
              "Quantity": 2,
              "Sku": "SKU-1001",
              "Name": "Widget"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        api.packAddItem(packingId = 99, barcodeOrSerial = "ABC123", qty = 2)

        val recorded = server.takeRequest()
        val decoded = URLDecoder.decode(recorded.requestUrl!!.encodedQuery ?: "", "UTF-8")
        assertTrue(decoded.contains("packingId=99"))
        assertTrue(decoded.contains("barcodeOrSerial=ABC123"))
        assertTrue(decoded.contains("qty=2"))
        assertEquals("/packing/add-item?$decoded", recorded.path)
    }

    @Test
    fun packing_get_items_path_and_parses_list() = runTest {
        val body = """
            [
              { "PackingItemId": 10, "ProductId": 1001, "Sku": "SKU-1001", "Name": "Widget", "Quantity": 3 },
              { "PackingItemId": 11, "ProductId": 1002, "Sku": "SKU-1002", "Name": "Gizmo",  "Quantity": 1 }
            ]
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val list = api.packGetItems(88)

        val recorded = server.takeRequest()
        assertEquals("/packing/items?packingId=88", recorded.path)
        assertEquals(2, list.size)
        assertEquals("SKU-1002", list[1]["Sku"])
    }

    @Test
    fun packing_validate_ok_true() = runTest {
        val body = """{ "ok": true, "issues": [] }"""
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val res = api.packValidate(55)

        val recorded = server.takeRequest()
        assertEquals("/packing/validate?packingId=55", recorded.path)
        assertTrue(res.ok)
        assertEquals(0, res.issues.size)
    }

    // ---------------- PICKING ----------------

    @Test
    fun picking_recent_uses_path_and_top_param() = runTest {
        val body = """
            {
              "items": [
                { "ScanId": 1, "BarcodeOrSerial": "ABC", "Qty": 1, "ScannedAt": "2025-11-01T10:00:00Z" }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        api.recentScans(sessionId = 77, top = 10)

        val recorded = server.takeRequest()
        assertEquals("/picking/77/recent?top=10", recorded.path)
    }

    // ---------------- STOCK ----------------

    @Test
    fun stock_start_has_query_params_user_and_name() = runTest {
        val body = """{ "StockTakeId": 5, "UserId": 1, "CreatedAt": "2025-11-01T09:00:00Z", "Status": "Active" }"""
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        api.stockStart(userId = 1, name = "CycleCount-Nov")

        val recorded = server.takeRequest()
        val decoded = URLDecoder.decode(recorded.requestUrl!!.encodedQuery ?: "", "UTF-8")
        assertTrue(decoded.contains("userId=1"))
        assertTrue(decoded.contains("name=CycleCount-Nov"))
        assertEquals("/stock/start?$decoded", recorded.path)
    }
}