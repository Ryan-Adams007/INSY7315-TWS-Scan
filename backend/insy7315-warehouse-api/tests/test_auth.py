# tests/test_auth.py

def test_register_success(client, fake_exec_sp, monkeypatch):
    # Return the created user row as the proc would
    user_row = {"UserId": 1, "Name": "Ryan", "Email": "ryan@example.com", "PasswordHash": "x"}

    def _sp(sp, params):
        return [user_row]

    fake_exec_sp("app.routers.auth", _sp)

    # Hashing is used only to pass hashed pw into proc; no need to patch
    payload = {"name": "Ryan", "email": "ryan@example.com", "password": "pw"}
    r = client.post("/auth/register", json=payload, headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    j = r.json()
    assert j["ok"] is True
    assert j["user"]["UserId"] == 1


def test_login_ok(client, fake_exec_sp, monkeypatch):
    # Force verify_password to return True
    from app.routers import auth as auth_mod
    monkeypatch.setattr(auth_mod, "verify_password", lambda p, h: True, raising=True)

    row = {
        "UserId": 7,
        "Name": "Bryan",
        "Email": "b@x.com",
        "PasswordHash": "hash",
        "Role": "User",
    }

    fake_exec_sp("app.routers.auth", lambda sp, params: [row])
    r = client.post("/auth/login", json={"email": "b@x.com", "password": "pw"}, headers={"X-API-Key": "test-key"})
    assert r.status_code == 200
    j = r.json()
    assert j["ok"] is True
    assert j["user"]["UserId"] == 7
    assert j["access_token"]


def test_login_bad_password(client, fake_exec_sp, monkeypatch):
    from app.routers import auth as auth_mod

    # Force password verification to fail
    monkeypatch.setattr(auth_mod, "verify_password", lambda p, h: False, raising=True)

    # Return a fake user record for the given email
    fake_exec_sp(
        "app.routers.auth",
        lambda sp, params: [{
            "PasswordHash": "hash",
            "UserId": 99,
            "Email": "adams.ryan700@gmail.com",
            "Name": "N"
        }]
    )

    # Attempt login with wrong password
    r = client.post(
        "/auth/login",
        json={"email": "adams.ryan700@gmail.com", "password": "nope"},
        headers={"X-API-Key": "test-key"},
    )

    # Expect unauthorized
    assert r.status_code == 401
    assert r.json()["detail"] == "Invalid credentials"