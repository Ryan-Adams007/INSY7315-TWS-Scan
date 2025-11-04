/* ============================================================
   CORE SCHEMA: Users, Products, Picking/Packing/Delivery,
                Stock Take, Sessions/Scans, Serials
   Notes:
     - Uses UTC timestamps (SYSUTCDATETIME()) for auditability.
     - Basic uniqueness and FK constraints included.
     - Indexes added for common lookups.
   ============================================================ */


/* =========================
   USERS
   ========================= */
CREATE TABLE dbo.Users
(
    UserId    INT IDENTITY(1,1) PRIMARY KEY,
    Name      NVARCHAR(100) NOT NULL,
    CreatedAt DATETIME2(0)  NOT NULL
        CONSTRAINT DF_Users_CreatedAt DEFAULT (SYSUTCDATETIME())
);


/* =========================
   PRODUCTS
   ========================= */
CREATE TABLE dbo.Products
(
    ProductId       INT IDENTITY(1,1) PRIMARY KEY,
    Sku             NVARCHAR(50)  NOT NULL,
    Name            NVARCHAR(150) NOT NULL,
    Barcode         NVARCHAR(100) NULL,
    QuantityInStock INT           NOT NULL
        CONSTRAINT DF_Products_QOH DEFAULT (0),
    CreatedAt       DATETIME2(0)  NOT NULL
        CONSTRAINT DF_Products_CreatedAt DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT UQ_Products_Sku UNIQUE (Sku)
);
-- Useful lookup for search by name
CREATE INDEX IX_Products_Name ON dbo.Products(Name);


/* =========================
   PICKING (simple pick list)
   ========================= */
CREATE TABLE dbo.Picking
(
    PickingId       INT IDENTITY(1,1) PRIMARY KEY,
    ProductId       INT          NOT NULL,
    QuantityToPick  INT          NOT NULL,
    QuantityPicked  INT          NOT NULL
        CONSTRAINT DF_Picking_QtyPicked DEFAULT (0),
    Status          NVARCHAR(20) NOT NULL
        CONSTRAINT DF_Picking_Status DEFAULT ('Pending'),  -- Pending | ...
    PickedBy        INT          NULL,
    CreatedAt       DATETIME2(0) NOT NULL
        CONSTRAINT DF_Picking_CreatedAt DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT FK_Picking_Product FOREIGN KEY (ProductId)
        REFERENCES dbo.Products(ProductId),
    CONSTRAINT FK_Picking_User FOREIGN KEY (PickedBy)
        REFERENCES dbo.Users(UserId)
);
CREATE INDEX IX_Picking_Product ON dbo.Picking(ProductId);


/* =========================
   PACKING (header + lines)
   ========================= */
CREATE TABLE dbo.Packing
(
    PackingId     INT IDENTITY(1,1) PRIMARY KEY,
    PackageNumber NVARCHAR(50)  NOT NULL,
    Status        NVARCHAR(20)  NOT NULL
        CONSTRAINT DF_Packing_Status DEFAULT ('Open'),  -- Open | Sealed
    PackedBy      INT           NULL,
    CreatedAt     DATETIME2(0)  NOT NULL
        CONSTRAINT DF_Packing_CreatedAt DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT UQ_Packing_Package UNIQUE (PackageNumber),
    CONSTRAINT FK_Packing_User FOREIGN KEY (PackedBy)
        REFERENCES dbo.Users(UserId)
);

CREATE TABLE dbo.PackingItems
(
    PackingItemId INT IDENTITY(1,1) PRIMARY KEY,
    PackingId     INT NOT NULL,
    ProductId     INT NOT NULL,
    Quantity      INT NOT NULL,
    CONSTRAINT FK_PackingItems_Packing FOREIGN KEY (PackingId)
        REFERENCES dbo.Packing(PackingId) ON DELETE CASCADE,
    CONSTRAINT FK_PackingItems_Product FOREIGN KEY (ProductId)
        REFERENCES dbo.Products(ProductId),
    CONSTRAINT CK_PackingItems_Qty CHECK (Quantity > 0)
);
CREATE INDEX IX_PackingItems_Packing ON dbo.PackingItems(PackingId);


/* =========================
   DELIVERY (trips + packages)
   ========================= */
CREATE TABLE dbo.Delivery
(
    DeliveryId     INT IDENTITY(1,1) PRIMARY KEY,
    DeliveryNumber NVARCHAR(50) NOT NULL,
    Status         NVARCHAR(20) NOT NULL
        CONSTRAINT DF_Delivery_Status DEFAULT ('Pending'), -- Pending | To Load | Loaded | Delivered
    Driver         NVARCHAR(100) NULL,
    CreatedAt      DATETIME2(0)  NOT NULL
        CONSTRAINT DF_Delivery_CreatedAt DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT UQ_Delivery_Number UNIQUE (DeliveryNumber)
);

CREATE TABLE dbo.DeliveryPackages
(
    DeliveryPackageId INT IDENTITY(1,1) PRIMARY KEY,
    DeliveryId        INT NOT NULL,
    PackageNumber     NVARCHAR(50) NOT NULL, -- references Packing.PackageNumber by value
    Status            NVARCHAR(20) NOT NULL
        CONSTRAINT DF_DeliveryPackages_Status DEFAULT ('To Load'),
    CONSTRAINT FK_DeliveryPackages_Delivery FOREIGN KEY (DeliveryId)
        REFERENCES dbo.Delivery(DeliveryId) ON DELETE CASCADE
);
CREATE INDEX IX_DeliveryPackages_Delivery ON dbo.DeliveryPackages(DeliveryId);
CREATE INDEX IX_DeliveryPackages_Package  ON dbo.DeliveryPackages(PackageNumber);


/* =========================
   STOCK TAKE (header + lines)
   ========================= */
CREATE TABLE dbo.StockTake
(
    StockTakeId INT IDENTITY(1,1) PRIMARY KEY,
    Name        NVARCHAR(100) NOT NULL,
    Status      NVARCHAR(20)  NOT NULL
        CONSTRAINT DF_StockTake_Status DEFAULT ('In Progress'), -- In Progress | Closed
    CreatedBy   INT           NULL,
    CreatedAt   DATETIME2(0)  NOT NULL
        CONSTRAINT DF_StockTake_CreatedAt DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT FK_StockTake_User FOREIGN KEY (CreatedBy)
        REFERENCES dbo.Users(UserId)
);

CREATE TABLE dbo.StockTakeItems
(
    StockTakeItemId INT IDENTITY(1,1) PRIMARY KEY,
    StockTakeId     INT NOT NULL,
    ProductId       INT NOT NULL,
    ExpectedQty     INT NOT NULL
        CONSTRAINT DF_STI_Expected DEFAULT (0),
    CountedQty      INT NOT NULL
        CONSTRAINT DF_STI_Counted DEFAULT (0),
    CONSTRAINT FK_STI_StockTake FOREIGN KEY (StockTakeId)
        REFERENCES dbo.StockTake(StockTakeId) ON DELETE CASCADE,
    CONSTRAINT FK_STI_Product FOREIGN KEY (ProductId)
        REFERENCES dbo.Products(ProductId)
);
CREATE INDEX IX_STI_StockTake ON dbo.StockTakeItems(StockTakeId);
CREATE INDEX IX_STI_Product   ON dbo.StockTakeItems(ProductId);


/* =========================
   PICK SESSIONS & SCANS
   ========================= */
CREATE TABLE dbo.PickSessions
(
    SessionId INT IDENTITY(1,1) PRIMARY KEY,
    UserId    INT       NOT NULL REFERENCES dbo.Users(UserId),
    StartedAt DATETIME2 NOT NULL
        CONSTRAINT DF_PickSessions_StartedAt DEFAULT (SYSUTCDATETIME()),
    EndedAt   DATETIME2 NULL,
    Status    NVARCHAR(20) NOT NULL
        CONSTRAINT DF_PickSessions_Status DEFAULT ('Active')  -- Active | Ended | Cancelled
);

CREATE TABLE dbo.PickScans
(
    ScanId       BIGINT IDENTITY(1,1) PRIMARY KEY,
    SessionId    INT           NOT NULL REFERENCES dbo.PickSessions(SessionId),
    ProductId    INT           NOT NULL REFERENCES dbo.Products(ProductId),
    SerialNumber NVARCHAR(100) NULL,   -- present if itemised by serial, else NULL
    Qty          INT           NOT NULL,   -- positive add, negative undo/adjust
    ScannedAt    DATETIME2     NOT NULL
        CONSTRAINT DF_PickScans_ScannedAt DEFAULT (SYSUTCDATETIME()),
    Note         NVARCHAR(200) NULL
);


/* =========================
   PRODUCT SERIALS (optional)
   ========================= */
CREATE TABLE dbo.ProductSerials
(
    SerialNumber NVARCHAR(100) PRIMARY KEY,
    ProductId    INT NOT NULL REFERENCES dbo.Products(ProductId),
    IsAvailable  BIT NOT NULL
        CONSTRAINT DF_ProductSerials_IsAvailable DEFAULT (1), -- 1=in stock, 0=consumed
    LastUpdated  DATETIME2 NOT NULL
        CONSTRAINT DF_ProductSerials_LastUpdated DEFAULT (SYSUTCDATETIME())
);


/* =========================
   QUICK CHECKS (optional)
   ========================= */
SELECT * FROM dbo.Users;
SELECT * FROM dbo.Products;

SELECT * FROM dbo.Picking;

SELECT * FROM dbo.Packing;
SELECT * FROM dbo.PackingItems;

SELECT * FROM dbo.Delivery;
SELECT * FROM dbo.DeliveryPackages;

SELECT * FROM dbo.StockTake;
SELECT * FROM dbo.StockTakeItems;


/* ============================================================
   SEED / UPSERT SAMPLE PRODUCTS
   - Uses MERGE to insert/update a curated set of SKUs
   - Keeps existing rows if already present, updates name/barcode/stock
   ============================================================ */
SET XACT_ABORT ON;
BEGIN TRAN;

;WITH SourceProducts AS
(
    SELECT *
    FROM (VALUES
        -- Electronics
        (N'ELEC-001', N'Wireless Mouse',               N'6001234567890', 120),
        (N'ELEC-002', N'Mechanical Keyboard',          N'6001234567891',  85),
        (N'ELEC-003', N'27-inch 4K Monitor',           N'6001234567892',  42),
        (N'ELEC-004', N'USB-C Docking Station',        N'6001234567893',  60),
        (N'ELEC-005', N'Bluetooth Headphones',         N'6001234567894',  73),

        -- Office Supplies
        (N'OFF-001',  N'A4 Printing Paper (500 Sheets)', N'6001234567901', 200),
        (N'OFF-002',  N'Ballpoint Pen Pack (10)',        N'6001234567902', 340),
        (N'OFF-003',  N'Spiral Notebook A5',             N'6001234567903', 180),
        (N'OFF-004',  N'Stapler with Staples',           N'6001234567904',  95),
        (N'OFF-005',  N'Desk Organizer Tray',            N'6001234567905',  70),

        -- Warehouse Consumables
        (N'WH-001',   N'Handheld Barcode Scanner',     N'6001234567910',  25),
        (N'WH-002',   N'Packing Tape Roll',            N'6001234567911', 400),
        (N'WH-003',   N'Shipping Labels (1000)',       N'6001234567912', 310),
        (N'WH-004',   N'Box Cutter Knife',             N'6001234567913', 180),
        (N'WH-005',   N'Industrial Gloves (Pair)',     N'6001234567914', 250),

        -- Canteen/Consumables
        (N'CON-001',  N'Coffee Beans 1kg',             N'6001234567920',  45),
        (N'CON-002',  N'Bottled Water 500ml',          N'6001234567921', 500),
        (N'CON-003',  N'Energy Drink Can 330ml',       N'6001234567922', 320),
        (N'CON-004',  N'Tea Bags (100 pack)',          N'6001234567923', 120),
        (N'CON-005',  N'Sugar Sachets (Box of 100)',   N'6001234567924',  80),

        -- Hardware/Tools
        (N'HW-001',   N'Power Drill 18V',              N'6001234567930',  25),
        (N'HW-002',   N'Hammer 1kg',                   N'6001234567931',  50),
        (N'HW-003',   N'Screwdriver Set (6pc)',        N'6001234567932',  90),
        (N'HW-004',   N'Adjustable Spanner 200mm',     N'6001234567933',  75),
        (N'HW-005',   N'Extension Cord 5m',            N'6001234567934',  65)
    ) AS v (Sku, Name, Barcode, QuantityInStock)
)
MERGE dbo.Products AS tgt
USING SourceProducts AS src
   ON tgt.Sku = src.Sku
WHEN MATCHED THEN
    UPDATE SET
        tgt.Name            = src.Name,
        tgt.Barcode         = src.Barcode,
        tgt.QuantityInStock = src.QuantityInStock    -- overwrite with seed values
WHEN NOT MATCHED BY TARGET THEN
    INSERT (Sku, Name, Barcode, QuantityInStock)
    VALUES (src.Sku, src.Name, src.Barcode, src.QuantityInStock);
-- Note: no "WHEN NOT MATCHED BY SOURCE" (won’t delete anything)

COMMIT;

PRINT 'Products upsert complete.';