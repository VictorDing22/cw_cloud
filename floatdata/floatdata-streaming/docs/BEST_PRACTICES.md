# Best Practices for Script Development

## Rules to Avoid Encoding and Syntax Errors

### Rule 1: English Only - No Chinese Characters

**DO:**
```powershell
Write-Host "Starting server..."
echo Starting Kafka...
```

**DON'T:**
```powershell
Write-Host "启动服务器..."
echo 启动 Kafka...
```

**Why:** Chinese characters cause encoding issues in Windows CMD/PowerShell.

---

### Rule 2: Use Short Paths and Relative Paths

**DO:**
```powershell
cd kafka_3.6.0
& .\bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092
```

**DON'T:**
```powershell
& "e:\Code\floatdata\floatdata-streaming\kafka_3.6.0\bin\windows\kafka-topics.bat" --list --bootstrap-server localhost:9092
```

**Why:** Windows command line has a length limit (~8191 characters).

---

### Rule 3: Write Complex Commands as Script Files

**DO:**
```powershell
# Save as check-status.ps1
Get-Process java
netstat -ano | Select-String ":9090"

# Execute
.\check-status.ps1
```

**DON'T:**
```powershell
powershell -Command "Get-Process java; netstat -ano | findstr :9090"
```

**Why:** Complex one-liners are hard to debug and have escaping issues.

---

### Rule 4: Use Correct Syntax for Each Shell

**PowerShell:**
```powershell
cd path; command1; command2
Get-Process | Where-Object {$_.Name -eq "java"}
```

**CMD/Batch:**
```batch
cd path && command1 && command2
for /f %%i in ('command') do echo %%i
```

**DON'T Mix:**
```powershell
cd /d path && command  # Wrong! This is CMD syntax in PowerShell
```

---

### Rule 5: Proper Error Handling

**DO:**
```powershell
$ErrorActionPreference = "Continue"
Get-Process java -ErrorAction SilentlyContinue
if ($LASTEXITCODE -ne 0) {
    Write-Host "Command failed" -ForegroundColor Red
}
```

**DON'T:**
```powershell
Get-Process java  # Will crash if process not found
```

---

### Rule 6: Use UTF-8 Encoding Without BOM

All script files should be saved as:
- Encoding: UTF-8
- Without BOM (Byte Order Mark)

This ensures compatibility across different systems.

---

### Rule 7: Test Commands Incrementally

**DO:**
1. Test basic command first
2. Add complexity gradually
3. Test each addition

**Example:**
```powershell
# Step 1: Basic
Get-Process

# Step 2: Filter
Get-Process java

# Step 3: Format
Get-Process java | Format-Table ProcessName, Id
```

---

## Corrected Scripts in This Project

### English-Only Scripts (✅ Correct)
- `start-system.ps1` - Startup script
- `test-system.ps1` - Status check
- `monitor.ps1` - Kafka monitor
- `stop.bat` - Stop script
- `verify.ps1` - Verification

### Scripts with Issues (❌ Need Fixing)
- `run-all.bat` - Contains Chinese
- `start-all.bat` - Contains Chinese

---

## Summary Checklist

Before running any script, verify:

- [ ] No Chinese characters in file
- [ ] Paths are relative or short
- [ ] Using correct shell syntax
- [ ] File encoding is UTF-8 without BOM
- [ ] Commands tested incrementally
- [ ] Error handling in place

---

## Quick Reference

### PowerShell
- Command separator: `;` or newline
- Variable: `$variable`
- Execute script: `.\script.ps1`
- Change directory: `cd path` or `Set-Location path`

### CMD/Batch
- Command separator: `&&` (success) or `&` (always)
- Variable: `%variable%` or `!variable!`
- Execute script: `script.bat` or `.\script.bat`
- Change directory: `cd /d path`

### File Naming
- Use lowercase or PascalCase
- No spaces (use hyphens or underscores)
- Examples: `start-system.ps1`, `check_status.ps1`

---

**Version:** 1.0  
**Last Updated:** 2025-11-14
