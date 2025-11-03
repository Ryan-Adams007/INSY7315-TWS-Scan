# app/main.py

from dotenv import load_dotenv
load_dotenv()  # Load environment variables from .env at startup

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import auth, picking, packing, delivery, stock, dbdiag


# Initialize FastAPI app
app = FastAPI(
    title="INSY7315 WarehouseOps API",
    version="1.0",
    description=(
        "Backend service for storeroom operations: "
        "authentication, picking, packing, delivery, and stock-taking."
    ),
)

# Enable CORS (allow everything during development)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Change to specific origins in production
    allow_methods=["*"],
    allow_headers=["*"],
)

# Health check endpoint
@app.get("/healthz")
def healthz():
    return {"ok": True, "env": "batcave"}

# Register routers
app.include_router(auth.router)
app.include_router(picking.router)
app.include_router(packing.router)
app.include_router(delivery.router)
app.include_router(stock.router)
app.include_router(dbdiag.router)

# Startup event
@app.on_event("startup")
async def startup_event():
    print("WarehouseOps API started. Environment: batcave")