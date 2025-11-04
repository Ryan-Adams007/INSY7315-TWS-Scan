-- Table: log individual scan events (supports undo and audit)
IF OBJECT_ID(N'dbo.StockTakeScans', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.StockTakeScans
    (
        ScanId      INT IDENTITY(1,1) PRIMARY KEY,
        StockTakeId INT NOT NULL,
        ProductId   INT NOT NULL,
        Qty         INT NOT NULL,
        ScannedAt   DATETIME2(0) NOT NULL
            CONSTRAINT DF_StockTakeScans_ScannedAt DEFAULT SYSUTCDATETIME(),

        CONSTRAINT FK_StockTakeScans_StockTake
            FOREIGN KEY (StockTakeId) REFERENCES dbo.StockTake(StockTakeId),
        CONSTRAINT FK_StockTakeScans_Products
            FOREIGN KEY (ProductId)   REFERENCES dbo.Products(ProductId)
    );
END;
GO


-- Start a stock-take session and return its details
CREATE OR ALTER PROCEDURE dbo.usp_Stock_StartSession
    @UserId INT,
    @Name   NVARCHAR(100) = NULL
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE UserId = @UserId)
        THROW 52001, 'User does not exist.', 1;

    INSERT INTO dbo.StockTake (Name, CreatedBy, Status)
    VALUES
    (
        ISNULL(@Name, CONCAT('StockTake ', CONVERT(VARCHAR(19), SYSUTCDATETIME(), 120))),
        @UserId,
        'In Progress'
    );

    DECLARE @StockTakeId INT = SCOPE_IDENTITY();

    SELECT StockTakeId, Name, Status, CreatedBy, CreatedAt
    FROM dbo.StockTake
    WHERE StockTakeId = @StockTakeId;
END;
GO


-- List items in a session, optional search by SKU/Name
CREATE OR ALTER PROCEDURE dbo.usp_Stock_ListItems
    @StockTakeId INT,
    @Search      NVARCHAR(100) = NULL
AS
BEGIN
    SET NOCOUNT ON;

    SELECT 
        sti.StockTakeItemId,
        sti.StockTakeId,
        p.ProductId,
        p.Sku,
        p.Name,
        sti.ExpectedQty,
        sti.CountedQty
    FROM dbo.StockTakeItems AS sti
    INNER JOIN dbo.Products     AS p ON p.ProductId = sti.ProductId
    WHERE sti.StockTakeId = @StockTakeId
      AND (
            @Search IS NULL
            OR p.Sku  LIKE '%' + @Search + '%'
            OR p.Name LIKE '%' + @Search + '%'
          )
    ORDER BY p.Name;
END;
GO


-- Add to counted quantity by barcode or SKU, log scan for undo
CREATE OR ALTER PROCEDURE dbo.usp_Stock_AddCount
    @StockTakeId    INT,
    @BarcodeOrSku   NVARCHAR(100),
    @Qty            INT = 1
AS
BEGIN
    SET NOCOUNT ON;

    IF @Qty IS NULL OR @Qty <= 0
        THROW 52010, 'Qty must be > 0.', 1;

    IF NOT EXISTS
    (
        SELECT 1
        FROM dbo.StockTake
        WHERE StockTakeId = @StockTakeId
          AND Status      = 'In Progress'
    )
        THROW 52011, 'Stock take not In Progress or not found.', 1;

    DECLARE @ProductId INT;

    -- Resolve product: prefer barcode, then SKU
    SELECT TOP (1) @ProductId = ProductId
    FROM dbo.Products
    WHERE Barcode = @BarcodeOrSku;

    IF @ProductId IS NULL
        SELECT TOP (1) @ProductId = ProductId
        FROM dbo.Products
        WHERE Sku = @BarcodeOrSku;

    IF @ProductId IS NULL
        THROW 52012, 'No product found for barcode/SKU.', 1;

    -- Ensure row exists in StockTakeItems (seed expected from current stock)
    IF NOT EXISTS
    (
        SELECT 1
        FROM dbo.StockTakeItems
        WHERE StockTakeId = @StockTakeId
          AND ProductId   = @ProductId
    )
    BEGIN
        INSERT INTO dbo.StockTakeItems (StockTakeId, ProductId, ExpectedQty, CountedQty)
        VALUES
        (
            @StockTakeId,
            @ProductId,
            (SELECT QuantityInStock FROM dbo.Products WHERE ProductId = @ProductId),
            0
        );
    END

    -- Increment counted quantity
    UPDATE dbo.StockTakeItems
       SET CountedQty = CountedQty + @Qty
     WHERE StockTakeId = @StockTakeId
       AND ProductId   = @ProductId;

    -- Log scan for audit/undo
    INSERT INTO dbo.StockTakeScans (StockTakeId, ProductId, Qty)
    VALUES (@StockTakeId, @ProductId, @Qty);

    -- Return updated item to refresh UI
    SELECT 
        sti.StockTakeItemId,
        sti.StockTakeId,
        p.ProductId,
        p.Sku,
        p.Name,
        sti.ExpectedQty,
        sti.CountedQty
    FROM dbo.StockTakeItems AS sti
    INNER JOIN dbo.Products     AS p ON p.ProductId = sti.ProductId
    WHERE sti.StockTakeId = @StockTakeId
      AND sti.ProductId   = @ProductId;
END;
GO


-- Undo the most recent scan for a session
CREATE OR ALTER PROCEDURE dbo.usp_Stock_UndoLast
    @StockTakeId INT
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @ScanId INT, @ProductId INT, @Qty INT;

    SELECT TOP (1)
        @ScanId    = ScanId,
        @ProductId = ProductId,
        @Qty       = Qty
    FROM dbo.StockTakeScans
    WHERE StockTakeId = @StockTakeId
    ORDER BY ScanId DESC;

    IF @ScanId IS NULL
        THROW 52020, 'Nothing to undo for this session.', 1;

    -- Decrement, clamp at zero
    UPDATE dbo.StockTakeItems
       SET CountedQty = CASE WHEN CountedQty - @Qty < 0 THEN 0 ELSE CountedQty - @Qty END
     WHERE StockTakeId = @StockTakeId
       AND ProductId   = @ProductId;

    -- Remove the scan log entry
    DELETE FROM dbo.StockTakeScans
    WHERE ScanId = @ScanId;

    -- Return updated item
    SELECT 
        sti.StockTakeItemId,
        sti.StockTakeId,
        p.ProductId,
        p.Sku,
        p.Name,
        sti.ExpectedQty,
        sti.CountedQty
    FROM dbo.StockTakeItems AS sti
    INNER JOIN dbo.Products     AS p ON p.ProductId = sti.ProductId
    WHERE sti.StockTakeId = @StockTakeId
      AND sti.ProductId   = @ProductId;
END;
GO


-- Complete a stock-take and return summary sets
CREATE OR ALTER PROCEDURE dbo.usp_Stock_Finish
    @StockTakeId INT
AS
BEGIN
    SET NOCOUNT ON;

    -- Mark completed (no-op if already completed)
    UPDATE dbo.StockTake
       SET Status = 'Completed'
     WHERE StockTakeId = @StockTakeId
       AND Status      = 'In Progress';

    -- Session header
    SELECT StockTakeId, Name, Status, CreatedBy, CreatedAt
    FROM dbo.StockTake
    WHERE StockTakeId = @StockTakeId;

    -- Totals
    SELECT 
        COUNT(*) AS Items,
        SUM(ExpectedQty) AS TotalExpected,
        SUM(CountedQty)  AS TotalCounted,
        SUM(CountedQty - ExpectedQty) AS TotalVariance,
        SUM(CASE WHEN CountedQty <> ExpectedQty THEN 1 ELSE 0 END) AS MismatchedItems
    FROM dbo.StockTakeItems
    WHERE StockTakeId = @StockTakeId;

    -- Top variances (cap at 50)
    SELECT TOP (50)
           p.Sku,
           p.Name,
           sti.ExpectedQty,
           sti.CountedQty,
           (sti.CountedQty - sti.ExpectedQty) AS Variance
    FROM dbo.StockTakeItems AS sti
    INNER JOIN dbo.Products     AS p ON p.ProductId = sti.ProductId
    WHERE sti.StockTakeId = @StockTakeId
      AND sti.CountedQty <> sti.ExpectedQty
    ORDER BY ABS(sti.CountedQty - sti.ExpectedQty) DESC, p.Name;
END;
GO


-- Quick check: confirm stock procedures exist
SELECT 
    p.name        AS ProcedureName,
    p.create_date AS CreatedOn,
    p.modify_date AS LastModified,
    p.type_desc   AS ObjectType
FROM sys.procedures AS p
WHERE p.name LIKE 'usp_Stock_%'
ORDER BY p.name;
GO


-- Minimal seed example for testing
IF NOT EXISTS (SELECT 1 FROM dbo.Products WHERE Sku = 'SKU-0002')
BEGIN
    INSERT INTO dbo.Products (Sku, Name, Barcode, QuantityInStock)
    VALUES ('SKU-0002', 'Black Pen', '9876543210987', 25);
END;
GO