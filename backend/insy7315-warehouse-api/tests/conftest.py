# tests/conftest.py
import os
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

# Ensure repo root is importable so `from app...` works
ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

# Minimal env for the app during tests
os.environ.setdefault("API_KEY", "test-key")
os.environ.setdefault("JWT_SECRET", "unit-test-secret")
os.environ.setdefault("JWT_ISS", "insy7315-warehouse")
os.environ.setdefault("JWT_AUD", "insy7315-mobile")

@pytest.fixture(scope="session")
def app_instance():
    # Import after sys.path is prepared
    from app.main import app
    return app

@pytest.fixture()
def client(app_instance, monkeypatch):
    # Bypass X-API-Key dependency everywhere
    from app import deps
    monkeypatch.setattr(deps, "require_key", lambda *a, **k: True, raising=True)

    # Prevent real DB calls unless a test overrides it
    from app import db
    def _boom(*args, **kwargs):
        raise RuntimeError("get_conn should be mocked in tests that need it.")
    monkeypatch.setattr(db, "get_conn", _boom, raising=True)

    return TestClient(app_instance)

# --- Helpers for specific tests to stub DB calls ---

@pytest.fixture()
def fake_exec_sp(monkeypatch):
    """
    Patch a router module's `exec_sp` with a provided implementation.

    Usage:
        def my_exec_sp(sp, params): return [...]
        fake_exec_sp("app.routers.packing", my_exec_sp)
    """
    def _apply(module_path: str, impl):
        mod = __import__(module_path, fromlist=["*"])
        monkeypatch.setattr(mod, "exec_sp", impl, raising=True)
        return impl
    return _apply

@pytest.fixture()
def fake_multi(monkeypatch):
    """
    Patch a router module's `_exec_sp_multi` helper.

    Usage:
        def my_multi(sp, params): return [[...], [...]]
        fake_multi("app.routers.delivery", my_multi)
    """
    def _apply(module_path: str, impl):
        mod = __import__(module_path, fromlist=["*"])
        monkeypatch.setattr(mod, "_exec_sp_multi", impl, raising=True)
        return impl
    return _apply