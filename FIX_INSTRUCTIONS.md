# How to Fix the CREATOR Tier Payment Error

## The Problem
Users cannot upgrade to CREATOR tier because the database constraint is missing this tier.

## The Solution (Choose ONE method)

### ⭐ Method 1: Double-Click the Batch File (EASIEST)
1. Navigate to `Ai_Social_Media_Backedn` folder
2. Double-click `apply_fix.bat`
3. Enter your PostgreSQL password when prompted
4. Done! ✓

### Method 2: Using Command Prompt
```cmd
cd Ai_Social_Media_Backedn
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -d ai_social_media -f fix_payment_orders_constraint.sql
```

### Method 3: Using PowerShell
```powershell
cd Ai_Social_Media_Backedn
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -d ai_social_media -f fix_payment_orders_constraint.sql
```

### Method 4: Using pgAdmin (GUI)
1. Open pgAdmin
2. Connect to your `ai_social_media` database
3. Click Tools → Query Tool
4. Open file: `fix_payment_orders_constraint.sql`
5. Click Execute (F5)

### Method 5: Using IntelliJ Database Tool
1. Open Database tool window (View → Tool Windows → Database)
2. Right-click `ai_social_media` database
3. Select New → Query Console
4. Copy content from `fix_payment_orders_constraint.sql`
5. Paste and click Execute (Ctrl+Enter)

### Method 6: Using DBeaver
1. Open DBeaver
2. Connect to `ai_social_media` database
3. Click SQL Editor → New SQL Script
4. Open file: `fix_payment_orders_constraint.sql`
5. Click Execute (Ctrl+Enter)

## What Gets Fixed

The SQL command adds `CREATOR` to the allowed values in the database constraint:

**Before:** `['FREE', 'STANDARD', 'PRO', 'SUPER_PRO']`  
**After:** `['FREE', 'STANDARD', 'CREATOR', 'PRO', 'SUPER_PRO']`

## Verification

After applying the fix, verify it worked:

```sql
SELECT constraint_name, check_clause 
FROM information_schema.check_constraints 
WHERE constraint_name = 'payment_orders_target_tier_check';
```

You should see all 5 tiers listed.

## No Restart Needed

✓ Your Spring Boot application does NOT need to be restarted  
✓ The fix takes effect immediately  
✓ Users can now upgrade to CREATOR tier

## Test It

Try creating a CREATOR subscription order again - it should work now!

---

**Need Help?**
- Make sure PostgreSQL is running
- Check your database password
- Verify database name is `ai_social_media`
- Check PostgreSQL is installed at: `C:\Program Files\PostgreSQL\18\`
