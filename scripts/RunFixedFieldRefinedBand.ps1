$ErrorActionPreference = 'Stop'

$ComsolCompile = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\bin\win64\comsolcompile.exe'
$Java = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\java\win64\jre\bin\java.exe'
$Plugins = 'I:\Program Files\COMSOL\COMSOL63\Multiphysics\plugins\*'
$Source = 'H:\comsolcc\z120_xsphere26_air200_mesh1.mph'
$Root = 'H:\comsolcc\fixed_field_refined_scan_138_145'
$RunnerJava = 'H:\comsolcc\RunFixedFieldCycle.java'

New-Item -ItemType Directory -Force -Path $Root | Out-Null
$runnerLog = Join-Path $Root 'runner.log'
"START $(Get-Date -Format o)" | Set-Content -LiteralPath $runnerLog -Encoding UTF8

& $ComsolCompile -classpath $Plugins $RunnerJava 2>&1 | Tee-Object -FilePath (Join-Path $Root 'compile.log') -Append
$compileExit = $LASTEXITCODE
Add-Content -LiteralPath $runnerLog -Value ("COMPILE_EXIT={0}" -f $compileExit)
if ($compileExit -ne 0) { throw 'RunFixedFieldCycle.java compile failed' }

# 按用户要求先补齐 140--145，再向较小 z 回探 139、138。
$summary = [System.Collections.Generic.List[object]]::new()
Add-Content -LiteralPath $runnerLog -Value 'TARGET_COUNT=6; HEIGHTS=141,142,143,144,139,138'

foreach ($z in @(141, 142, 143, 144, 139, 138)) {
    $success = $false
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $out = Join-Path $Root ("z{0}_attempt{1}" -f $z, $attempt)
        New-Item -ItemType Directory -Force -Path $out | Out-Null
        $stdout = Join-Path $out 'stdout.log'
        $stderr = Join-Path $out 'stderr.log'
        $argLine = '-cp "H:\comsolcc;{0}" RunFixedFieldCycle "{1}" {2} "{3}"' -f $Plugins, $Source, $z, $out
        Add-Content -LiteralPath $runnerLog -Value ("START z={0} attempt={1} {2}" -f $z, $attempt, (Get-Date -Format o))
        $p = Start-Process -FilePath $Java -ArgumentList $argLine -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru -WindowStyle Hidden
        $null = Wait-Process -Id $p.Id -Timeout 1800 -ErrorAction SilentlyContinue
        if (Get-Process -Id $p.Id -ErrorAction SilentlyContinue) {
            Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
            Add-Content -LiteralPath $runnerLog -Value ("TIMEOUT z={0} attempt={1}" -f $z, $attempt)
        }

        $csv = Join-Path $out 'cycle.csv'
        if (Test-Path -LiteralPath $csv) {
            $rows = @(Import-Csv -LiteralPath $csv)
            $vals = @($rows | ForEach-Object { [double]$_.Fx_mN })
            if ($vals.Count -gt 0) {
                $min = ($vals | Measure-Object -Minimum).Minimum
                $max = ($vals | Measure-Object -Maximum).Maximum
                $mean = ($vals | Measure-Object -Average).Average
                $mesh = 0
                $ml = Select-String -LiteralPath $stdout -Pattern 'MESH_DONE elements=' | Select-Object -Last 1
                if ($ml -and $ml.Line -match 'elements=([0-9]+)') { $mesh = [int]$Matches[1] }
                $status = if ($max -lt 0) { 'ALL_NEGATIVE' } else { 'HAS_POSITIVE' }
                $summary.Add([pscustomobject]@{
                    z_sphere_mm = $z; attempt = $attempt; rows = $rows.Count
                    Fmin_mN = $min; Fmax_mN = $max; mean_mN = $mean
                    S_mN = -$max; peak_to_peak_mN = $max - $min
                    mesh_elements = $mesh; status = $status
                })
                Add-Content -LiteralPath $runnerLog -Value ("DONE z={0} attempt={1} mesh={2} Fmin={3:R} Fmax={4:R} mean={5:R} {6}" -f $z, $attempt, $mesh, $min, $max, $mean, $status)
                $success = $true
                break
            }
        }
        Add-Content -LiteralPath $runnerLog -Value ("ERROR z={0} attempt={1}; retrying if available" -f $z, $attempt)
    }
    if (-not $success) {
        $summary.Add([pscustomobject]@{
            z_sphere_mm = $z; attempt = 3; rows = 0
            Fmin_mN = 'ERROR'; Fmax_mN = 'ERROR'; mean_mN = 'ERROR'
            S_mN = 'UNKNOWN'; peak_to_peak_mN = 'UNKNOWN'
            mesh_elements = 0; status = 'ERROR'
        })
        Add-Content -LiteralPath $runnerLog -Value ("SKIP z={0} after three attempts" -f $z)
    }
    $summary | Export-Csv -LiteralPath (Join-Path $Root 'refined_scan_summary.csv') -NoTypeInformation -Encoding UTF8
}

$summary | Format-Table -AutoSize | Out-String | Set-Content -LiteralPath (Join-Path $Root 'summary.txt') -Encoding UTF8
Add-Content -LiteralPath $runnerLog -Value ("FINISH $(Get-Date -Format o)")
