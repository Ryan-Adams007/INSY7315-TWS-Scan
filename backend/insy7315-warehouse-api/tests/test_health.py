# tests/test_health.py
def test_root_healthz(client):
    r = client.get("/healthz", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json()["ok"] is True