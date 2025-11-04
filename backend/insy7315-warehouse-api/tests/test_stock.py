# tests/test_stock.py
def test_stock_start(client, fake_exec_sp):
    row = {"StockTakeId": 10, "CreatedBy": 5}
    fake_exec_sp("app.routers.stock", lambda sp, params: [row])

    r = client.post("/stock/start?userId=5&name=Cycle%20Count", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json()["StockTakeId"] == 10

def test_stock_list_items(client, fake_exec_sp):
    rows = [{"ProductId": 1, "Sku": "ABC", "Name": "Widget", "Counted": 3}]
    fake_exec_sp("app.routers.stock", lambda sp, params: rows)

    r = client.get("/stock/10/items?search=Wid", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json()["items"][0]["Sku"] == "ABC"

def test_stock_add_count(client, fake_exec_sp):
    row = {"ProductId": 1, "Sku": "ABC", "Qty": 2}
    fake_exec_sp("app.routers.stock", lambda sp, params: [row])

    r = client.post("/stock/add?stockTakeId=10&barcodeOrSku=ABC&qty=2", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json()["Qty"] == 2

def test_stock_undo_last(client, fake_exec_sp):
    row = {"Undone": 1}
    fake_exec_sp("app.routers.stock", lambda sp, params: [row])

    r = client.post("/stock/10/undo-last", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200

def test_stock_finish_multi_sets(client, fake_multi):
    def _multi(sp, params):
        header = [{"StockTakeId": 10, "Name": "Cycle"}]
        totals = [{"TotalCounted": 15}]
        discrepancies = [{"ProductId": 1, "Delta": -2}]
        return [header, totals, discrepancies]
    fake_multi("app.routers.stock", _multi)

    r = client.post("/stock/10/finish", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    j = r.json()
    assert j["header"]["StockTakeId"] == 10
    assert j["totals"]["TotalCounted"] == 15
    assert j["discrepancies"][0]["Delta"] == -2