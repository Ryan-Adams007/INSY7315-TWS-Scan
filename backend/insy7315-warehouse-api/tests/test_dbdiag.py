# tests/test_dbdiag.py
import types

def test_db_ping_ok(client, monkeypatch):
    # Fake DB connection & cursor for /diag/db-ping
    class Cur:
        def execute(self, *_args): pass
        def fetchone(self): return ["master"]
    class Conn:
        def __enter__(self): return self
        def __exit__(self, *args): pass
        def cursor(self): return Cur()

    from app import db
    monkeypatch.setattr(db, "get_conn", lambda: Conn(), raising=True)

    r = client.get("/diag/db-ping", headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    assert r.json() == {"ok": True, "sample_db": "master"}