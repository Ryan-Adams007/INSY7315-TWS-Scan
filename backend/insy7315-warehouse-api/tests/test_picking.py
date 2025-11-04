# tests/test_picking.py
def test_picking_start_session(client, fake_exec_sp):
    row = {"SessionId": 12, "UserId": 5, "StartedAt": "2025-11-01T10:00:00Z", "Status": "Active"}
    fake_exec_sp("app.routers.picking", lambda sp, params: [row])

    r = client.post("/picking/start?userId=5", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json()["SessionId"] == 12
    assert r.json()["Status"] == "Active"

def test_picking_add_scan(client, fake_exec_sp):
    row = {"ScanId": 1, "BarcodeOrSerial": "ABC", "Qty": 2, "ScannedAt": "2025-11-01T11:00:00Z"}
    fake_exec_sp("app.routers.picking", lambda sp, params: [row])

    r = client.post("/picking/add-scan?sessionId=12&barcodeOrSerial=ABC&qty=2", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json()["Qty"] == 2

def test_picking_recent(client, fake_exec_sp):
    rows = [
        {"ScanId": 10, "BarcodeOrSerial": "A", "Qty": 1},
        {"ScanId": 11, "BarcodeOrSerial": "B", "Qty": 3},
    ]
    fake_exec_sp("app.routers.picking", lambda sp, params: rows)

    r = client.get("/picking/12/recent?top=25", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert len(r.json()["items"]) == 2

def test_picking_complete(client, fake_exec_sp):
    fake_exec_sp("app.routers.picking", lambda sp, params: [{"ProductId": 1, "Qty": 5}])
    r = client.post("/picking/complete?sessionId=12", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json()["ok"] is True
    assert r.json()["summary"][0]["Qty"] == 5