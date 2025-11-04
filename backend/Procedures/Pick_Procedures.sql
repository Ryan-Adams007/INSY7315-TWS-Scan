/* ===============================
   Picking: Tables (idempotent)
   =============================== */

-- Sessions table
IF OBJECT_ID(N'dbo.PickSessions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.PickSessions
    (
        SessionId INT IDENTITY(1,1) PRIMARY KEY,
        UserId    INT        NOT NULL REFERENCES dbo.Users(UserId),
        StartedAt DATETIME2  NOT NULL CONSTRAINT DF_PickSessions_StartedAt DEFAULT SYSUTCDATETIME(),
        EndedAt   DATETIME2  NULL,
        Status    NVARCHAR(20) NOT NULL CONSTRAINT DF_PickSessions_Status DEFAULT 'Active'  -- Active|Completed|Cancelled
    );
END;

-- Scan events
IF OBJECT_ID(N'dbo.PickScans', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.PickScans
    (
        ScanId       BIGINT IDENTITY(1,1) PRIMARY KEY,
        SessionId    INT           NOT NULL REFERENCES dbo.PickSessions(SessionId),
        ProductId    INT           NOT NULL REFERENCES dbo.Products(ProductId),
        SerialNumber NVARCHAR(100) NULL,             -- if serialised, otherwise NULL
        Qty          INT           NOT NULL,         -- positive increments
        ScannedAt    DATETIME2     NOT NULL CONSTRAINT DF_PickScans_ScannedAt DEFAULT SYSUTCDATETIME(),
        Note         NVARCHAR(200) NULL
    );
END;

-- Picking workload (optional planning table)
IF OBJECT_ID(N'dbo.Picking', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Picking
    (
        PickingId       INT IDENTITY(1,1) PRIMARY KEY,
        ProductId       INT           NOT NULL,
        QuantityToPick  INT           NOT NULL,
        QuantityPicked  INT           NOT NULL CONSTRAINT DF_Picking_QtyPicked DEFAULT (0),
        Status          NVARCHAR(20)  NOT NULL CONSTRAINT DF_Picking_Status DEFAULT ('Pending'), -- Pending|InProgress|Done
        PickedBy        INT           NULL,
        CreatedAt       DATETIME2(0)  NOT NULL CONSTRAINT DF_Picking_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_Picking_Product FOREIGN KEY (ProductId) REFERENCES dbo.Products(ProductId),
        CONSTRAINT FK_Picking_User    FOREIGN KEY (PickedBy)  REFERENCES dbo.Users(UserId)
    );
    CREATE INDEX IX_Picking_Product ON dbo.Picking(ProductId);
END;

-- Staging picked items for packing
IF OBJECT_ID(N'dbo.PickToPack', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.PickToPack
    (
        StagingId    INT IDENTITY(1,1) PRIMARY KEY,
        SessionId    INT          NOT NULL REFERENCES dbo.PickSessions(SessionId),
        Status       NVARCHAR(20) NOT NULL CONSTRAINT DF_PickToPack_Status DEFAULT 'Queued', -- Queued|Claimed|Consumed|Cancelled
        CreatedAt    DATETIME2(0) NOT NULL CONSTRAINT DF_PickToPack_CreatedAt DEFAULT SYSUTCDATETIME(),
        ClaimedAt    DATETIME2(0) NULL,
        ClaimedBy    INT          NULL REFERENCES dbo.Users(UserId),
        PackedIntoId INT          NULL REFERENCES dbo.Packing(PackingId),  -- set by packing flow
        Note         NVARCHAR(200) NULL
    );
    CREATE INDEX IX_PickToPack_Status  ON dbo.PickToPack(Status);
    CREATE INDEX IX_PickToPack_Session ON dbo.PickToPack(SessionId);
END;

IF OBJECT_ID(N'dbo.PickToPackLines', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.PickToPackLines
    (
        StagingLineId BIGINT IDENTITY(1,1) PRIMARY KEY,
        StagingId     INT          NOT NULL REFERENCES dbo.PickToPack(StagingId) ON DELETE CASCADE,
        ProductId     INT          NOT NULL REFERENCES dbo.Products(ProductId),
        SerialNumber  NVARCHAR(100) NULL,
        Qty           INT          NOT NULL CHECK (Qty > 0)
    );
    CREATE INDEX IX_PickToPackLines_Staging ON dbo.PickToPackLines(StagingId);
END;
GO


/* ===============================
   Picking: Procedures
   =============================== */

-- Start a picking session and return header
CREATE OR ALTER PROCEDURE dbo.usp_Pick_StartSession
    @UserId INT
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE UserId = @UserId)
        THROW 51001, 'User does not exist.', 1;

    INSERT INTO dbo.PickSessions (UserId, Status, StartedAt)
    VALUES (@UserId, 'Active', SYSUTCDATETIME());

    DECLARE @SessionId INT = SCOPE_IDENTITY();

    SELECT SessionId, UserId, Status, StartedAt, EndedAt
    FROM dbo.PickSessions
    WHERE SessionId = @SessionId;
END;
GO


-- Add a scan to a session; decrements on-hand and logs serial if applicable
CREATE OR ALTER PROCEDURE dbo.usp_Pick_AddScan
    @SessionId       INT,
    @BarcodeOrSerial NVARCHAR(100),
    @Qty             INT = 1
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @Qty IS NULL OR @Qty <= 0
        THROW 51010, 'Qty must be > 0.', 1;

    IF NOT EXISTS
    (
        SELECT 1 FROM dbo.PickSessions
        WHERE SessionId = @SessionId AND Status = 'Active'
    )
        THROW 51011, 'Session not Active or not found.', 1;

    DECLARE @ProductId INT = NULL,
            @Serial    NVARCHAR(100) = NULL;

    -- Try serialised product first (if table exists and serial is available)
    IF OBJECT_ID(N'dbo.ProductSerials', N'U') IS NOT NULL
    BEGIN
        SELECT TOP (1)
            @ProductId = ProductId,
            @Serial    = SerialNumber
        FROM dbo.ProductSerials
        WHERE SerialNumber = @BarcodeOrSerial
          AND IsAvailable  = 1;
    END;

    -- Fallback to product by barcode
    IF @ProductId IS NULL
    BEGIN
        SELECT TOP (1) @ProductId = ProductId
        FROM dbo.Products
        WHERE Barcode = @BarcodeOrSerial;
    END;

    IF @ProductId IS NULL
        THROW 51012, 'No product found for barcode/serial.', 1;

    BEGIN TRY
        BEGIN TRAN;

        -- Lock product row for on-hand check/update
        DECLARE @OnHand INT;

        SELECT @OnHand = QuantityInStock
        FROM dbo.Products WITH (UPDLOCK, ROWLOCK)
        WHERE ProductId = @ProductId;

        IF @OnHand IS NULL
        BEGIN
            ROLLBACK TRAN;
            THROW 51014, 'Product not found during update.', 1;
        END;

        IF @OnHand < @Qty
        BEGIN
            ROLLBACK TRAN;
            THROW 51013, 'Insufficient stock.', 1;
        END;

        -- Decrement inventory
        UPDATE dbo.Products
           SET QuantityInStock = QuantityInStock - @Qty
         WHERE ProductId = @ProductId;

        -- Log scan
        INSERT INTO dbo.PickScans (SessionId, ProductId, SerialNumber, Qty)
        VALUES (@SessionId, @ProductId, @Serial, @Qty);

        -- Mark serial as consumed (if applicable)
        IF @Serial IS NOT NULL AND OBJECT_ID(N'dbo.ProductSerials', N'U') IS NOT NULL
        BEGIN
            UPDATE dbo.ProductSerials
               SET IsAvailable = 0,
                   LastUpdated = SYSUTCDATETIME()
             WHERE SerialNumber = @Serial;
        END;

        COMMIT TRAN;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRAN;
        THROW;
    END CATCH;

    -- Return the most recent scan and new on-hand for UI refresh
    SELECT TOP (1)
           ps.ScanId,
           ps.SessionId,
           ps.ProductId,
           p.Sku,
           p.Name,
           ps.SerialNumber,
           ps.Qty,
           ps.ScannedAt,
           p.QuantityInStock AS NewOnHand
    FROM dbo.PickScans AS ps
    INNER JOIN dbo.Products  AS p ON p.ProductId = ps.ProductId
    WHERE ps.SessionId = @SessionId
    ORDER BY ps.ScanId DESC;
END;
GO


-- Recent scans for a session
CREATE OR ALTER PROCEDURE dbo.usp_Pick_GetRecentScans
    @SessionId INT,
    @TopN      INT = 25
AS
BEGIN
    SET NOCOUNT ON;

    SELECT TOP (@TopN)
           ps.ScanId,
           ps.ScannedAt,
           ps.SerialNumber,
           ps.Qty,
           ps.ProductId,
           p.Sku,
           p.Name
    FROM dbo.PickScans AS ps
    INNER JOIN dbo.Products AS p ON p.ProductId = ps.ProductId
    WHERE ps.SessionId = @SessionId
    ORDER BY ps.ScanId DESC;
END;
GO


-- Complete session and return a summary
CREATE OR ALTER PROCEDURE dbo.usp_Pick_Complete
    @SessionId INT
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE dbo.PickSessions
       SET Status  = 'Completed',
           EndedAt = SYSUTCDATETIME()
     WHERE SessionId = @SessionId;

    IF @@ROWCOUNT = 0
        THROW 51021, 'Session not found.', 1;

    ;WITH S AS
    (
        SELECT
            ps.SessionId,
            COUNT(*)                     AS Lines,
            COUNT(DISTINCT ps.ProductId) AS DistinctProducts,
            SUM(ps.Qty)                  AS TotalQty,
            MIN(ps.ScannedAt)            AS FirstScanAt,
            MAX(ps.ScannedAt)            AS LastScanAt
        FROM dbo.PickScans AS ps
        WHERE ps.SessionId = @SessionId
        GROUP BY ps.SessionId
    )
    SELECT
        p.SessionId,
        p.UserId,
        p.Status,
        p.StartedAt,
        p.EndedAt,
        ISNULL(S.Lines, 0)            AS Lines,
        ISNULL(S.DistinctProducts, 0) AS DistinctProducts,
        ISNULL(S.TotalQty, 0)         AS TotalQty,
        S.FirstScanAt,
        S.LastScanAt
    FROM dbo.PickSessions AS p
    LEFT JOIN S ON S.SessionId = p.SessionId
    WHERE p.SessionId = @SessionId;
END;
GO


-- Stage a completed pick session for packing (aggregates by product)
CREATE OR ALTER PROCEDURE dbo.usp_Pick_StageForPack
    @SessionId INT
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT EXISTS (SELECT 1 FROM dbo.PickSessions WHERE SessionId = @SessionId)
        THROW 53001, 'Pick session not found.', 1;

    -- If already staged, return latest staging record
    IF EXISTS
    (
        SELECT 1 FROM dbo.PickToPack
        WHERE SessionId = @SessionId
          AND Status IN ('Queued','Claimed','Consumed')
    )
    BEGIN
        SELECT TOP (1) *
        FROM dbo.PickToPack
        WHERE SessionId = @SessionId
        ORDER BY StagingId DESC;
        RETURN;
    END;

    DECLARE @StagingId INT;

    INSERT INTO dbo.PickToPack (SessionId, Status)
    VALUES (@SessionId, 'Queued');

    SET @StagingId = SCOPE_IDENTITY();

    INSERT INTO dbo.PickToPackLines (StagingId, ProductId, SerialNumber, Qty)
    SELECT
        @StagingId,
        ps.ProductId,
        NULL,                          -- aggregate by product (serials omitted here)
        SUM(ps.Qty)
    FROM dbo.PickScans AS ps
    WHERE ps.SessionId = @SessionId
    GROUP BY ps.ProductId;

    SELECT * FROM dbo.PickToPack WHERE StagingId = @StagingId;
END;
GO


/* ===============================
   Introspection helpers (optional)
   =============================== */

-- Procedures with usp_Pick_ prefix
SELECT name AS ProcedureName, create_date AS CreatedOn, modify_date AS LastModified, type_desc AS ObjectType
FROM sys.objects
WHERE type = 'P' AND name LIKE 'usp_Pick_%'
ORDER BY name;

-- Parameters for usp_Pick_StartSession
SELECT p.name AS ProcName, prm.name AS ParamName, prm.is_output
FROM sys.procedures AS p
JOIN sys.parameters AS prm ON p.object_id = prm.object_id
WHERE p.name = 'usp_Pick_StartSession' AND SCHEMA_NAME(p.schema_id) = 'dbo';