# tests/test_packing.py
def test_start_or_set_package(client, fake_exec_sp):
    row = {"PackingId": 22, "PackageNumber": "PKG-000022", "Status": "Open"}
    fake_exec_sp("app.routers.packing", lambda sp, params: [row])

    r = client.post("/packing/start-or-set", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json()["PackageNumber"] == "PKG-000022"

def test_add_item_ok(client, fake_exec_sp):
    row = {"PackingItemId": 5, "ProductId": 9, "Sku": "ABC", "Name": "Widget", "Quantity": 2}
    fake_exec_sp("app.routers.packing", lambda sp, params: [row])

    r = client.post("/packing/add-item?packingId=22&barcodeOrSerial=ABC&qty=2", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json()["Quantity"] == 2

def test_validate_blocks_seal(client, monkeypatch):
    # Make validation report issues so seal returns 409
    from app.routers import packing as p
    monkeypatch.setattr(p, "_validate_against_staging",
                        lambda packingId: [{"Issue": "Missing", "ProductId": 1, "Required": 2, "Packed": 0, "Delta": 2}],
                        raising=True)
    r = client.post("/packing/seal?packingId=22", headers={"X-API-Key": "test-key"})
    assert r.status_code == 409
    assert r.json()["detail"]["message"].startswith("Staged requirements not satisfied")

def test_seal_happy_path(client, fake_exec_sp, monkeypatch):
    # No issues from validation
    from app.routers import packing as p
    monkeypatch.setattr(p, "_validate_against_staging", lambda _: [], raising=True)
    fake_exec_sp("app.routers.packing", lambda sp, params: [{"PackingId": 22, "Status": "Sealed"}])

    r = client.post("/packing/seal?packingId=22", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json()["Status"] == "Sealed"