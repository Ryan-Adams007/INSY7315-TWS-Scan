/* ============================================================
   DELIVERY: Query, state changes, and lightweight seeding
   ------------------------------------------------------------
   Assumes tables:
     - dbo.Delivery (DeliveryId, DeliveryNumber, Status, Driver, CreatedAt, ...)
     - dbo.DeliveryPackages (DeliveryPackageId, DeliveryId, PackageNumber, Status, Destination, DeliveredAt, ...)
   Status values used: 'To Load' | 'Loaded' | 'Delivered'
   ============================================================ */


---------------------------------------------------------------
-- (A) Ensure optional columns used by procs exist
---------------------------------------------------------------
IF COL_LENGTH('dbo.DeliveryPackages','Destination') IS NULL
    ALTER TABLE dbo.DeliveryPackages ADD Destination NVARCHAR(150) NULL;

IF COL_LENGTH('dbo.DeliveryPackages','DeliveredAt') IS NULL
    ALTER TABLE dbo.DeliveryPackages ADD DeliveredAt DATETIME2(0) NULL;
GO


/* ============================================================
   (B) Listing & lookups
   ============================================================ */

-- List packages with optional filtering, plus quick counts
CREATE OR ALTER PROCEDURE dbo.usp_Delivery_ListPackages
    @Search NVARCHAR(50) = NULL,          -- filter by PackageNumber (contains)
    @Status NVARCHAR(20) = NULL,          -- 'To Load' | 'Loaded' | 'Delivered'
    @Top    INT = 100                     -- cap result set size
AS
BEGIN
    SET NOCOUNT ON;

    ;WITH base AS
    (
        SELECT 
            dp.DeliveryPackageId,
            dp.PackageNumber,
            dp.Status,
            dp.DeliveryId,
            dp.Destination,
            d.Driver,
            d.CreatedAt
        FROM dbo.DeliveryPackages AS dp
        LEFT JOIN dbo.Delivery AS d
               ON d.DeliveryId = dp.DeliveryId
        WHERE (@Status IS NULL OR dp.Status = @Status)
          AND (@Search IS NULL OR dp.PackageNumber LIKE '%' + @Search + '%')
    )
    SELECT TOP (@Top) *
    FROM base
    ORDER BY DeliveryPackageId DESC;

    -- Quick totals/rollup for dashboard counters
    SELECT
        COUNT(*)                                               AS Total,
        SUM(CASE WHEN Status = 'To Load'  THEN 1 ELSE 0 END)  AS ToLoad,
        SUM(CASE WHEN Status = 'Loaded'   THEN 1 ELSE 0 END)  AS Loaded,
        SUM(CASE WHEN Status = 'Delivered' THEN 1 ELSE 0 END) AS Delivered
    FROM dbo.DeliveryPackages;
END;
GO


-- Get details for one package by its number
CREATE OR ALTER PROCEDURE dbo.usp_Delivery_GetPackageDetails
    @PackageNumber NVARCHAR(50)
AS
BEGIN
    SET NOCOUNT ON;

    SELECT TOP (1)
           dp.DeliveryPackageId,
           dp.PackageNumber,
           dp.Status,
           dp.Destination,
           dp.DeliveryId,
           d.Driver,
           d.CreatedAt
    FROM dbo.DeliveryPackages AS dp
    LEFT JOIN dbo.Delivery AS d
           ON d.DeliveryId = dp.DeliveryId
    WHERE dp.PackageNumber = @PackageNumber;
END;
GO


/* ============================================================
   (C) State transitions
   ============================================================ */

-- Set status to 'Loaded'
CREATE OR ALTER PROCEDURE dbo.usp_Delivery_MarkLoaded
    @PackageNumber NVARCHAR(50)
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE dbo.DeliveryPackages
       SET Status = N'Loaded'
     WHERE PackageNumber = @PackageNumber;

    -- Return updated row
    EXEC dbo.usp_Delivery_GetPackageDetails @PackageNumber = @PackageNumber;
END;
GO


-- Set status to 'To Load'
CREATE OR ALTER PROCEDURE dbo.usp_Delivery_MarkToLoad
    @PackageNumber NVARCHAR(50)
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE dbo.DeliveryPackages
       SET Status = N'To Load'
     WHERE PackageNumber = @PackageNumber;

    -- Return updated row
    EXEC dbo.usp_Delivery_GetPackageDetails @PackageNumber = @PackageNumber;
END;
GO


-- Convenience: scanning a number implies loading it
CREATE OR ALTER PROCEDURE dbo.usp_Delivery_ScanToLoad
    @ScannedNumber NVARCHAR(50)
AS
BEGIN
    SET NOCOUNT ON;

    EXEC dbo.usp_Delivery_MarkLoaded @PackageNumber = @ScannedNumber;
END;
GO


-- Transition from 'Loaded' -> 'Delivered'
CREATE OR ALTER PROCEDURE dbo.usp_Delivery_MarkDelivered
    @PackageNumber NVARCHAR(50)
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE dbo.DeliveryPackages
       SET Status      = N'Delivered',
           DeliveredAt = SYSUTCDATETIME()
     WHERE PackageNumber = @PackageNumber
       AND Status = N'Loaded';  -- guard against invalid transitions

    IF @@ROWCOUNT = 0
        THROW 56001, 'Cannot mark delivered (not found or not Loaded).', 1;

    SELECT TOP (1) *
    FROM dbo.DeliveryPackages
    WHERE PackageNumber = @PackageNumber;
END;
GO


/* ============================================================
   (D) Diagnostics: list all delivery procs
   ============================================================ */
SELECT 
    p.name AS ProcedureName,
    SCHEMA_NAME(p.schema_id) AS SchemaName,
    p.create_date AS CreatedOn,
    p.modify_date AS LastModified,
    p.type_desc AS ObjectType
FROM sys.procedures AS p
WHERE p.name LIKE 'usp_Delivery_%'
ORDER BY p.name;
GO


/* ============================================================
   (E) Lightweight seed/demo (optional)
   - Creates/locates a Delivery row for 'Sam Driver'
   - Seeds three demo packages under that delivery
   NOTE: Wrap in a transaction for idempotent execution per run.
   ============================================================ */
SET NOCOUNT ON;
BEGIN TRAN;

DECLARE @DeliveryId INT;

-- Create a delivery "trip" with generated DeliveryNumber if none exists for this driver
IF NOT EXISTS (SELECT 1 FROM dbo.Delivery WHERE Driver = N'Sam Driver')
BEGIN
    INSERT INTO dbo.Delivery (DeliveryNumber, Status, Driver)
    VALUES (N'__TEMP__', N'Pending', N'Sam Driver');

    SET @DeliveryId = SCOPE_IDENTITY();

    UPDATE dbo.Delivery
       SET DeliveryNumber = CONCAT(N'DEL-', RIGHT('000000' + CAST(@DeliveryId AS VARCHAR(20)), 6))
     WHERE DeliveryId = @DeliveryId;
END
ELSE
BEGIN
    SELECT TOP (1) @DeliveryId = DeliveryId
    FROM dbo.Delivery
    WHERE Driver = N'Sam Driver'
    ORDER BY DeliveryId DESC;
END

-- Seed/ensure three packages exist with basic destinations
IF NOT EXISTS (SELECT 1 FROM dbo.DeliveryPackages WHERE PackageNumber = N'PKG-10023')
    INSERT INTO dbo.DeliveryPackages (DeliveryId, PackageNumber, Status, Destination)
    VALUES (@DeliveryId, N'PKG-10023', N'To Load', N'Warehouse B, Johannesburg');

IF NOT EXISTS (SELECT 1 FROM dbo.DeliveryPackages WHERE PackageNumber = N'PKG-10024')
    INSERT INTO dbo.DeliveryPackages (DeliveryId, PackageNumber, Status, Destination)
    VALUES (@DeliveryId, N'PKG-10024', N'To Load', N'Warehouse C, Sandton');

IF NOT EXISTS (SELECT 1 FROM dbo.DeliveryPackages WHERE PackageNumber = N'PKG-10025')
    INSERT INTO dbo.DeliveryPackages (DeliveryId, PackageNumber, Status, Destination)
    VALUES (@DeliveryId, N'PKG-10025', N'Loaded',  N'Warehouse D, Midrand');

COMMIT TRAN;

-- Quick visibility checks
SELECT * FROM dbo.Delivery WHERE DeliveryId = @DeliveryId;
SELECT * FROM dbo.DeliveryPackages WHERE DeliveryId = @DeliveryId ORDER BY DeliveryPackageId DESC;