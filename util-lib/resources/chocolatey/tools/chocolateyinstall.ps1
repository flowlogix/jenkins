# Downloads the release's Windows zip from dl.flowlogix.com, checks it, and unpacks it into this package.
# Chocolatey puts every .exe in the package on the PATH, so the executable works right after install.
$ErrorActionPreference = 'Stop'
$toolsDir = Split-Path -Parent $MyInvocation.MyCommand.Definition

Install-ChocolateyZipPackage -PackageName $env:ChocolateyPackageName -UnzipLocation $toolsDir `
    -Url64bit '@url@' -Checksum64 '@checksum@' -ChecksumType64 'sha256'
