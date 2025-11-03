# app/db.py

import os, pyodbc

def get_conn():
    driver = os.getenv("ODBC_DRIVER", "ODBC Driver 18 for SQL Server")
    server = os.getenv("AZURE_SQL_SERVER")          # tcp:<server>.database.windows.net
    db     = os.getenv("AZURE_SQL_DB")
    user   = os.getenv("AZURE_SQL_USER")
    pwd    = os.getenv("AZURE_SQL_PASSWORD")

    if not all([server, db, user, pwd]):
        raise RuntimeError("Missing DB env vars.")

    return pyodbc.connect(
        f"DRIVER={{{driver}}};SERVER={server};DATABASE={db};UID={user};PWD={pwd};"
        "Encrypt=yes;TrustServerCertificate=no;Connection Timeout=30;"
    )

def exec_sp(sp_name: str, params: list):
    with get_conn() as c:
        cur = c.cursor()
        placeholders = ",".join(["?"] * len(params))
        cur.execute(f"EXEC {sp_name} {placeholders}", params)
        cols = [d[0] for d in cur.description] if cur.description else []
        rows = [dict(zip(cols, r)) for r in cur.fetchall()] if cur.description else []
        return rows