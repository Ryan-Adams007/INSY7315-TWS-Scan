/* ============================
   USER TABLE UPDATE SCRIPT
   ============================ */

-- Drop old Username constraint if it exists
IF EXISTS (
    SELECT 1
    FROM sys.key_constraints
    WHERE name = 'UQ_Users_Username'
      AND parent_object_id = OBJECT_ID('dbo.Users')
)
BEGIN
    ALTER TABLE dbo.Users DROP CONSTRAINT UQ_Users_Username;
END
GO

-- Drop default constraint on Username if it exists
DECLARE @dc_name sysname;
SELECT @dc_name = dc.name
FROM sys.default_constraints dc
JOIN sys.columns c
  ON c.object_id = dc.parent_object_id
 AND c.column_id = dc.parent_column_id
WHERE dc.parent_object_id = OBJECT_ID('dbo.Users')
  AND c.name = 'Username';

IF @dc_name IS NOT NULL
BEGIN
    EXEC('ALTER TABLE dbo.Users DROP CONSTRAINT ' + QUOTENAME(@dc_name) + ';');
END
GO

-- Make Username column nullable
IF COL_LENGTH('dbo.Users','Username') IS NOT NULL
BEGIN
    DECLARE @is_nullable bit;
    SELECT @is_nullable = is_nullable
    FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.Users')
      AND name = 'Username';

    IF @is_nullable = 0
    BEGIN
        ALTER TABLE dbo.Users ALTER COLUMN Username NVARCHAR(50) NULL;
    END
END
GO

-- Add unique index on Username (only if not null)
IF COL_LENGTH('dbo.Users','Username') IS NOT NULL
AND NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'UX_Users_Username_NotNull'
      AND object_id = OBJECT_ID('dbo.Users')
)
BEGIN
    CREATE UNIQUE INDEX UX_Users_Username_NotNull
    ON dbo.Users(Username)
    WHERE Username IS NOT NULL;
END
GO

-- Add Email column if missing
IF COL_LENGTH('dbo.Users','Email') IS NULL
    ALTER TABLE dbo.Users ADD Email NVARCHAR(256) NOT NULL;

-- Add PasswordHash column if missing
IF COL_LENGTH('dbo.Users','PasswordHash') IS NULL
    ALTER TABLE dbo.Users ADD PasswordHash NVARCHAR(255) NOT NULL;

-- Add Name column if missing
IF COL_LENGTH('dbo.Users','Name') IS NULL
    ALTER TABLE dbo.Users ADD Name NVARCHAR(100) NOT NULL;

-- Add Role column with default 'User'
IF COL_LENGTH('dbo.Users','Role') IS NULL
BEGIN
    ALTER TABLE dbo.Users ADD Role NVARCHAR(50) NOT NULL CONSTRAINT DF_Users_Role DEFAULT('User');
END
ELSE IF NOT EXISTS (
    SELECT 1 FROM sys.default_constraints dc
    JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
    WHERE dc.parent_object_id = OBJECT_ID('dbo.Users') AND c.name='Role'
)
BEGIN
    ALTER TABLE dbo.Users ADD CONSTRAINT DF_Users_Role DEFAULT('User') FOR Role;
END

-- Add IsActive column with default 1
IF COL_LENGTH('dbo.Users','IsActive') IS NULL
BEGIN
    ALTER TABLE dbo.Users ADD IsActive BIT NOT NULL CONSTRAINT DF_Users_IsActive DEFAULT(1);
END
ELSE IF NOT EXISTS (
    SELECT 1 FROM sys.default_constraints dc
    JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
    WHERE dc.parent_object_id = OBJECT_ID('dbo.Users') AND c.name='IsActive'
)
BEGIN
    ALTER TABLE dbo.Users ADD CONSTRAINT DF_Users_IsActive DEFAULT(1) FOR IsActive;
END

-- Add CreatedAt column with default timestamp
IF COL_LENGTH('dbo.Users','CreatedAt') IS NULL
BEGIN
    ALTER TABLE dbo.Users ADD CreatedAt DATETIME2(0) NOT NULL CONSTRAINT DF_Users_CreatedAt DEFAULT(SYSUTCDATETIME());
END
ELSE IF NOT EXISTS (
    SELECT 1 FROM sys.default_constraints dc
    JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
    WHERE dc.parent_object_id = OBJECT_ID('dbo.Users') AND c.name='CreatedAt'
)
BEGIN
    ALTER TABLE dbo.Users ADD CONSTRAINT DF_Users_CreatedAt DEFAULT(SYSUTCDATETIME()) FOR CreatedAt;
END
GO

-- Add unique constraint on Email
IF NOT EXISTS (
    SELECT 1
    FROM sys.key_constraints
    WHERE name = 'UQ_Users_Email'
      AND parent_object_id = OBJECT_ID('dbo.Users')
)
BEGIN
    ALTER TABLE dbo.Users ADD CONSTRAINT UQ_Users_Email UNIQUE (Email);
END
GO


/* ============================
   STORED PROCEDURES
   ============================ */

-- Create new user by email
CREATE OR ALTER PROCEDURE dbo.usp_User_CreateByEmail
    @Name         NVARCHAR(100),
    @Email        NVARCHAR(256),
    @PasswordHash NVARCHAR(255)
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (SELECT 1 FROM dbo.Users WHERE Email = @Email)
        THROW 54001, 'Email already exists.', 1;

    INSERT INTO dbo.Users (Name, Email, PasswordHash)
    VALUES (@Name, @Email, @PasswordHash);

    SELECT TOP (1) UserId, Name, Email, CreatedAt
    FROM dbo.Users
    WHERE Email = @Email;
END
GO

-- Fetch user by email (for login)
CREATE OR ALTER PROCEDURE dbo.usp_User_GetByEmail
    @Email NVARCHAR(256)
AS
BEGIN
    SET NOCOUNT ON;

    SELECT TOP (1)
        UserId, Name, Email, PasswordHash, Role, IsActive, CreatedAt
    FROM dbo.Users
    WHERE Email = @Email;
END
GO