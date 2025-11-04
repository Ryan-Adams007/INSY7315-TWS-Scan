/* ============================================
   PACKING: Core tables (idempotent creation)
   ============================================ */

IF OBJECT_ID(N'dbo.Packing', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Packing
    (
        PackingId     INT IDENTITY(1,1) PRIMARY KEY,
        PackageNumber NVARCHAR(50)  NOT NULL,
        Status        NVARCHAR(20)  NOT NULL CONSTRAINT DF_Packing_Status DEFAULT ('Open'), -- Open|Sealed
        PackedBy      INT           NULL,
        CreatedAt     DATETIME2(0)  NOT NULL CONSTRAINT DF_Packing_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT UQ_Packing_Package UNIQUE (PackageNumber),
        CONSTRAINT FK_Packing_User FOREIGN KEY (PackedBy) REFERENCES dbo.Users(UserId)
    );
END;

IF OBJECT_ID(N'dbo.PackingItems', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.PackingItems
    (
        PackingItemId INT IDENTITY(1,1) PRIMARY KEY,
        PackingId     INT NOT NULL,
        ProductId     INT NOT NULL,
        Quantity      INT NOT NULL,
        CONSTRAINT FK_PackingItems_Packing FOREIGN KEY (PackingId) REFERENCES dbo.Packing(PackingId) ON DELETE CASCADE,
        CONSTRAINT FK_PackingItems_Product FOREIGN KEY (ProductId) REFERENCES dbo.Products(ProductId),
        CONSTRAINT CK_PackingItems_Qty CHECK (Quantity > 0)
    );
    CREATE INDEX IX_PackingItems_Packing ON dbo.PackingItems(PackingId);
END;
GO


/* ============================================
   PACKING: Start or locate a package
   - Auto-generates PackageNumber if not provided
   ============================================ */
CREATE OR ALTER PROCEDURE dbo.usp_Pack_StartOrSet
    @PackageNumber NVARCHAR(50) = NULL
AS
BEGIN
    SET NOCOUNT ON;

    -- Auto-generate new package number if none provided
    IF @PackageNumber IS NULL OR LTRIM(RTRIM(@PackageNumber)) = ''
    BEGIN
        INSERT INTO dbo.Packing (PackageNumber, Status, CreatedAt)
        VALUES ('__TEMP__', 'Open', SYSUTCDATETIME());

        DECLARE @PackingId INT = SCOPE_IDENTITY();
        DECLARE @Gen NVARCHAR(50) = CONCAT('PKG-', RIGHT('000000' + CAST(@PackingId AS VARCHAR(20)), 6));

        UPDATE dbo.Packing
           SET PackageNumber = @Gen
         WHERE PackingId = @PackingId;

        SELECT * FROM dbo.Packing WHERE PackingId = @PackingId;
        RETURN;
    END;

    -- If an explicit number is provided, return existing or create new
    IF EXISTS (SELECT 1 FROM dbo.Packing WHERE PackageNumber = @PackageNumber)
    BEGIN
        SELECT TOP (1) * FROM dbo.Packing WHERE PackageNumber = @PackageNumber ORDER BY PackingId DESC;
        RETURN;
    END;

    INSERT INTO dbo.Packing (PackageNumber, Status, CreatedAt)
    VALUES (@PackageNumber, 'Open', SYSUTCDATETIME());

    SELECT * FROM dbo.Packing WHERE PackingId = SCOPE_IDENTITY();
END;
GO


/* ============================================
   PACKING: Add item into a package
   - Accepts barcode or serial
   ============================================ */
CREATE OR ALTER PROCEDURE dbo.usp_Pack_AddItem
    @PackingId       INT,
    @BarcodeOrSerial NVARCHAR(100),
    @Qty             INT = 1
AS
BEGIN
    SET NOCOUNT ON;

    IF @Qty IS NULL OR @Qty <= 0
        THROW 52010, 'Qty must be > 0.', 1;

    IF NOT EXISTS (SELECT 1 FROM dbo.Packing WHERE PackingId = @PackingId AND Status = 'Open')
        THROW 52011, 'Packing not found or not Open.', 1;

    DECLARE @ProductId INT = NULL;

    -- Serial match (if serials table exists)
    IF OBJECT_ID(N'dbo.ProductSerials', N'U') IS NOT NULL
    BEGIN
        SELECT TOP (1) @ProductId = ProductId
        FROM dbo.ProductSerials
        WHERE SerialNumber = @BarcodeOrSerial;
    END;

    -- Fallback: product barcode
    IF @ProductId IS NULL
    BEGIN
        SELECT TOP (1) @ProductId = ProductId
        FROM dbo.Products
        WHERE Barcode = @BarcodeOrSerial;
    END;

    IF @ProductId IS NULL
        THROW 52012, 'No product found for barcode/serial.', 1;

    INSERT INTO dbo.PackingItems (PackingId, ProductId, Quantity)
    VALUES (@PackingId, @ProductId, @Qty);

    -- Return last line for UI refresh
    SELECT TOP (1)
           pi.PackingItemId, pi.PackingId, pi.ProductId, pi.Quantity,
           p.Sku, p.Name
    FROM dbo.PackingItems AS pi
    JOIN dbo.Products     AS p ON p.ProductId = pi.ProductId
    WHERE pi.PackingId = @PackingId
    ORDER BY pi.PackingItemId DESC;
END;
GO


/* ============================================
   PACKING: List items in a package
   ============================================ */
CREATE OR ALTER PROCEDURE dbo.usp_Pack_GetItems
    @PackingId INT
AS
BEGIN
    SET NOCOUNT ON;

    SELECT
        pi.PackingItemId, pi.ProductId, p.Sku, p.Name, pi.Quantity
    FROM dbo.PackingItems AS pi
    JOIN dbo.Products     AS p ON p.ProductId = pi.ProductId
    WHERE pi.PackingId = @PackingId
    ORDER BY pi.PackingItemId DESC;
END;
GO


/* ============================================
   PACKING: Undo last item added
   ============================================ */
CREATE OR ALTER PROCEDURE dbo.usp_Pack_UndoLast
    @PackingId INT
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @LastId INT =
    (
        SELECT TOP (1) PackingItemId
        FROM dbo.PackingItems
        WHERE PackingId = @PackingId
        ORDER BY PackingItemId DESC
    );

    IF @LastId IS NULL
    BEGIN
        SELECT CAST(0 AS INT) AS Removed;  -- nothing to undo
        RETURN;
    END;

    DELETE FROM dbo.PackingItems WHERE PackingItemId = @LastId;

    SELECT CAST(1 AS INT) AS Removed, @LastId AS PackingItemId;
END;
GO


/* ============================================
   PACKING: Clear all items in a package
   ============================================ */
CREATE OR ALTER PROCEDURE dbo.usp_Pack_Clear
    @PackingId INT
AS
BEGIN
    SET NOCOUNT ON;

    DELETE FROM dbo.PackingItems WHERE PackingId = @PackingId;

    SELECT CAST(1 AS INT) AS Cleared;
END;
GO


/* ============================================
   PACKING: Seal package (simple version)
   - Marks package as Sealed
   ============================================ */
CREATE OR ALTER PROCEDURE dbo.usp_Pack_Seal
    @PackingId INT
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE dbo.Packing
       SET Status = 'Sealed'
     WHERE PackingId = @PackingId;

    SELECT * FROM dbo.Packing WHERE PackingId = @PackingId;
END;
GO


/* ============================================
   PACKING: Summary per package
   ============================================ */
CREATE OR ALTER PROCEDURE dbo.usp_Pack_Summary
    @PackingId INT
AS
BEGIN
    SET NOCOUNT ON;

    SELECT
        p.PackingId,
        p.PackageNumber,
        p.Status,
        COUNT(*)                         AS ItemLines,
        ISNULL(SUM(pi.Quantity), 0)      AS TotalQty,
        p.CreatedAt
    FROM dbo.Packing AS p
    LEFT JOIN dbo.PackingItems AS pi ON pi.PackingId = p.PackingId
    WHERE p.PackingId = @PackingId
    GROUP BY p.PackingId, p.PackageNumber, p.Status, p.CreatedAt;
END;
GO


/* ============================================
   STAGING (Pick -> Pack): Views & helpers
   - Derive required quantities for a staging batch
   ============================================ */

-- Staging view to resolve quantities required per StagingId
IF OBJECT_ID(N'dbo.vPackStagingLines', N'V') IS NOT NULL
    DROP VIEW dbo.vPackStagingLines;
GO

CREATE VIEW dbo.vPackStagingLines
AS
SELECT
    l.StagingId,
    l.ProductId,
    p.Sku,
    p.Name,
    SUM(l.Qty) AS Required
FROM dbo.PickToPackLines AS l
JOIN dbo.Products       AS p ON p.ProductId = l.ProductId
GROUP BY
    l.StagingId,
    l.ProductId,
    p.Sku,
    p.Name;
GO


-- Expose staged lines for client apps (per staging batch)
CREATE OR ALTER PROCEDURE dbo.usp_Pack_GetStagedLines
    @StagingId INT
AS
BEGIN
    SET NOCOUNT ON;

    SELECT
        ProductId,
        Sku,
        Name,
        Required
    FROM dbo.vPackStagingLines
    WHERE StagingId = @StagingId
    ORDER BY Name;
END;
GO


/* ============================================
   VALIDATION: Verify packed vs staged
   ============================================ */

-- Aggregate packed quantity per (PackingId, ProductId)
CREATE OR ALTER VIEW dbo.vPackingQty AS
SELECT
    pi.PackingId,
    pi.ProductId,
    SUM(pi.Quantity) AS Packed
FROM dbo.PackingItems AS pi
GROUP BY pi.PackingId, pi.ProductId;
GO

-- Compare staged requirements to current package contents
CREATE OR ALTER PROCEDURE dbo.usp_Pack_ValidateAgainstStaging
    @PackingId INT
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @StagingId INT;

    SELECT TOP (1) @StagingId = StagingId
    FROM dbo.PickToPack
    WHERE PackedIntoId = @PackingId;

    IF @StagingId IS NULL
    BEGIN
        THROW 54021, 'No staging linked to this package.', 1;
        RETURN;
    END;

    ;WITH Required AS
    (
        SELECT ProductId, Sku, Name, Required
        FROM dbo.vPackStagingLines
        WHERE StagingId = @StagingId
    ),
    Packed AS
    (
        SELECT ProductId, Packed
        FROM dbo.vPackingQty
        WHERE PackingId = @PackingId
    ),
    Missing AS
    (
        SELECT
            r.ProductId, r.Sku, r.Name,
            r.Required,
            ISNULL(p.Packed, 0) AS Packed,
            (r.Required - ISNULL(p.Packed, 0)) AS Delta
        FROM Required r
        LEFT JOIN Packed p ON p.ProductId = r.ProductId
        WHERE (r.Required - ISNULL(p.Packed, 0)) > 0
    ),
    OverOrExtra AS
    (
        SELECT
            p.ProductId,
            pr.Sku,
            pr.Name,
            pr.Required,
            p.Packed,
            (p.Packed - ISNULL(pr.Required, 0)) AS Delta
        FROM Packed p
        LEFT JOIN Required pr ON pr.ProductId = p.ProductId
        WHERE pr.ProductId IS NULL           -- extra product
           OR p.Packed > pr.Required         -- over-packed
    )
    SELECT
        'Missing' AS Issue,
        m.ProductId, m.Sku, m.Name,
        m.Required, m.Packed, m.Delta
    FROM Missing m
    UNION ALL
    SELECT
        CASE WHEN pr.ProductId IS NULL THEN 'Extra' ELSE 'Over' END AS Issue,
        o.ProductId, o.Sku, o.Name,
        ISNULL(o.Required, 0) AS Required,
        o.Packed,
        o.Delta
    FROM OverOrExtra o
    LEFT JOIN Required pr ON pr.ProductId = o.ProductId
    ORDER BY Issue, Sku, Name;

    -- If no rows returned above, surface a positive confirmation
    IF @@ROWCOUNT = 0
        SELECT CAST(1 AS bit) AS Ok, @StagingId AS StagingId, @PackingId AS PackingId;
END;
GO


/* ============================================
   SEAL (integrated): Mark sealed and ensure
   DeliveryPackages entry exists (To Load)
   ============================================ */

-- Ensure nullable FK and correct FK name for DeliveryPackages(DeliveryId)
DECLARE @fk sysname;

SELECT TOP (1) @fk = fk.name
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
JOIN sys.columns c ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
WHERE fk.parent_object_id = OBJECT_ID(N'dbo.DeliveryPackages')
  AND c.name = N'DeliveryId';

IF @fk IS NOT NULL
BEGIN
    DECLARE @sql nvarchar(max) =
        N'ALTER TABLE dbo.DeliveryPackages DROP CONSTRAINT ' + QUOTENAME(@fk) + N';';
    EXEC (@sql);
END;

ALTER TABLE dbo.DeliveryPackages
ALTER COLUMN DeliveryId INT NULL;

ALTER TABLE dbo.DeliveryPackages
ADD CONSTRAINT FK_DeliveryPackages_Delivery
FOREIGN KEY (DeliveryId) REFERENCES dbo.Delivery(DeliveryId);
GO

-- Final (kept) version: seal and sync to DeliveryPackages
CREATE OR ALTER PROCEDURE dbo.usp_Pack_Seal
    @PackingId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @PackageNumber NVARCHAR(50);

    -- Seal the package
    UPDATE dbo.Packing
       SET Status = 'Sealed'
     WHERE PackingId = @PackingId;

    -- Read package number
    SELECT @PackageNumber = PackageNumber
    FROM dbo.Packing
    WHERE PackingId = @PackingId;

    -- Ensure a DeliveryPackages row exists (new or update to 'To Load')
    IF NOT EXISTS (SELECT 1 FROM dbo.DeliveryPackages WHERE PackageNumber = @PackageNumber)
    BEGIN
        INSERT INTO dbo.DeliveryPackages (DeliveryId, PackageNumber, Status, Destination)
        VALUES (NULL, @PackageNumber, 'To Load', NULL);
    END
    ELSE
    BEGIN
        UPDATE dbo.DeliveryPackages
           SET Status = 'To Load'
         WHERE PackageNumber = @PackageNumber;
    END

    -- Return sealed packing row
    SELECT * FROM dbo.Packing WHERE PackingId = @PackingId;
END;
GO


/* ============================================
   DIAGNOSTICS: Introspection for procedures
   ============================================ */
SELECT 
    p.name AS ProcedureName,
    SCHEMA_NAME(p.schema_id) AS SchemaName,
    p.create_date AS CreatedOn,
    p.modify_date AS LastModified,
    p.type_desc AS ObjectType
FROM sys.procedures AS p
WHERE p.name LIKE 'usp_Pack_%'
ORDER BY p.name;