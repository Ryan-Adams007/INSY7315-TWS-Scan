# tests/test_pack_staging.py
def test_claim_next_ok(client, fake_exec_sp):
    row = {"StagingId": 1, "PackingId": 22, "PackageNumber": "PKG-000022"}
    fake_exec_sp("app.routers.pack_staging", lambda sp, params: [row])
    r = client.post("/staging/claim-next?packedBy=5", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json()["PackingId"] == 22

def test_get_lines_ok(client, fake_exec_sp):
    rows = [{"ProductId": 9, "Sku": "ABC", "Name": "Widget", "Required": 2}]
    fake_exec_sp("app.routers.pack_staging", lambda sp, params: rows)
    r = client.get("/staging/123/lines", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json()[0]["Required"] == 2