# tests/test_delivery.py
def test_delivery_list_with_counts(client, fake_multi):
    def _multi(sp, params):
        # set 1: items
        items = [
            {"DeliveryPackageId": 1, "PackageNumber": "PKG-000001", "Status": "To Load", "Destination": "A"},
            {"DeliveryPackageId": 2, "PackageNumber": "PKG-000002", "Status": "Loaded", "Destination": "B"},
        ]
        # set 2: counts
        counts = [{"Total": 2, "ToLoad": 1, "Loaded": 1}]
        return [items, counts]
    fake_multi("app.routers.delivery", _multi)

    r = client.get("/delivery/list?top=100", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    j = r.json()
    assert len(j["items"]) == 2
    assert j["counts"]["Loaded"] == 1

def test_delivery_get_details(client, fake_exec_sp):
    row = {"PackageNumber": "PKG-000001", "Status": "Loaded", "Destination": "A"}
    fake_exec_sp("app.routers.delivery", lambda sp, params: [row])
    r = client.get("/delivery/PKG-000001", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json()["Status"] == "Loaded"

def test_mark_loaded_ok(client, fake_exec_sp):
    row = {"PackageNumber": "PKG-000001", "Status": "Loaded"}
    fake_exec_sp("app.routers.delivery", lambda sp, params: [row])
    r = client.post("/delivery/PKG-000001/mark-loaded", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json()["Status"] == "Loaded"

def test_mark_to_load_ok(client, fake_exec_sp):
    row = {"PackageNumber": "PKG-000001", "Status": "To Load"}
    fake_exec_sp("app.routers.delivery", lambda sp, params: [row])
    r = client.post("/delivery/PKG-000001/mark-to-load", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json()["Status"] == "To Load"

def test_scan_to_load_ok(client, fake_exec_sp):
    row = {"PackageNumber": "PKG-000001", "Status": "Loaded"}
    fake_exec_sp("app.routers.delivery", lambda sp, params: [row])
    r = client.post("/delivery/scan-to-load", json={"scannedNumber": "PKG-000001"}, headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json()["Status"] == "Loaded"

def test_mark_delivered_ok(client, fake_exec_sp):
    row = {"PackageNumber": "PKG-000001", "Status": "Delivered"}
    fake_exec_sp("app.routers.delivery", lambda sp, params: [row])
    r = client.post("/delivery/PKG-000001/mark-delivered", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json()["Status"] == "Delivered"