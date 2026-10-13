# Script de preparação e empacotamento do Frontend para a Hostinger
# 1. Compila o Angular com a configuração de produção
# 2. Gera 404.html e copia .htaccess com UTF-8 sem BOM (compatível Apache)
# 3. Atualiza a pasta deploy-wbagency limpa
# 4. Gera deploy-hostinger.zip com separadores Linux '/' (100% compatível com hPanel Hostinger)

$ErrorActionPreference = 'Stop'

Write-Host "===> 1. Compilando o Frontend em modo de produção..." -ForegroundColor Cyan
Set-Location -Path "$PSScriptRoot/../frontend"
npm run build:prod

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERRO: A compilação do Angular falhou!" -ForegroundColor Red
    exit 1
}

$distBrowser = "$PSScriptRoot/../frontend/dist/frontend/browser"
$deployDir = "$PSScriptRoot/../deploy-wbagency"
$zipPath = "$PSScriptRoot/../deploy-hostinger.zip"

if (-not (Test-Path "$distBrowser/index.html")) {
    Write-Host "ERRO CRÍTICO: index.html não foi encontrado em $distBrowser!" -ForegroundColor Red
    exit 1
}

Write-Host "===> 2. Configurando roteamento SPA (.htaccess completo com CORS, cache e suporte a vídeo...)" -ForegroundColor Cyan
Copy-Item "$distBrowser/index.html" "$distBrowser/404.html" -Force

# WB AGENCY — .htaccess PRODUÇÃO OTIMIZADO PARA HOSTINGER APACHE (UTF-8 sem BOM)
$htaccessContent = @"
<IfModule mod_rewrite.c>
  RewriteEngine On
  RewriteBase /

  # Forçar HTTPS
  RewriteCond %{HTTPS} !=on
  RewriteRule ^(.*)$ https://%{HTTP_HOST}%{REQUEST_URI} [L,R=301]

  # Ignorar arquivos e diretórios físicos existentes
  RewriteCond %{REQUEST_FILENAME} -f [OR]
  RewriteCond %{REQUEST_FILENAME} -d
  RewriteRule ^ - [L]

  # Redirecionar todas as outras requisições para o index.html da SPA
  RewriteRule ^ index.html [L]
</IfModule>

<IfModule mod_headers.c>
  # Cabeçalhos de Segurança Defensivos
  Header set X-Frame-Options "DENY"
  Header set X-Content-Type-Options "nosniff"
  Header set Referrer-Policy "strict-origin-when-cross-origin"
  Header set Permissions-Policy "camera=(), microphone=(), geolocation=()"

  # Cache agressivo para assets versionados
  <FilesMatch "\.(js|css|webp|png|jpg|jpeg|svg|woff2|woff|ico)$">
    Header set Cache-Control "max-age=31536000, public, immutable"
  </FilesMatch>

  # index.html nunca deve ser cacheado para permitir deploys instantâneos
  <FilesMatch "index\.html$">
    Header set Cache-Control "no-cache, no-store, must-revalidate"
    Header set Pragma "no-cache"
    Header set Expires 0
  </FilesMatch>
</IfModule>
"@

$htPath = "$distBrowser/.htaccess"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($htPath, ($htaccessContent -replace "`r`n", "`n"), $utf8NoBom)

Write-Host "===> 3. Atualizando pasta deploy-wbagency limpa..." -ForegroundColor Cyan
if (Test-Path $deployDir) {
    Remove-Item "$deployDir/*" -Recurse -Force -ErrorAction SilentlyContinue
} else {
    New-Item -ItemType Directory -Path $deployDir -Force | Out-Null
}
Copy-Item "$distBrowser/*" "$deployDir/" -Recurse -Force
Copy-Item $htPath "$deployDir/.htaccess" -Force

Write-Host "===> 4. Gerando pacote deploy-hostinger.zip com separadores Unix '/'..." -ForegroundColor Cyan
if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$distResolved = (Resolve-Path $distBrowser).Path
$fs = [System.IO.File]::Open($zipPath, 'Create')
$arc = New-Object System.IO.Compression.ZipArchive($fs, [System.IO.Compression.ZipArchiveMode]::Create)

Get-ChildItem $distResolved -Recurse -File -Force | ForEach-Object {
    $rel = $_.FullName.Substring($distResolved.Length + 1).Replace('\', '/')
    [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($arc, $_.FullName, $rel, [System.IO.Compression.CompressionLevel]::Optimal)
}

$arc.Dispose()
$fs.Dispose()

$zipSizeMb = [math]::Round((Get-Item $zipPath).Length / 1MB, 2)

Write-Host "==========================================================================" -ForegroundColor Green
Write-Host "✅ SUCESSO! Pacote deploy-hostinger.zip ($zipSizeMb MB) gerado na raiz do projeto." -ForegroundColor Green
Write-Host "   - 100% compatível com Linux/Apache da Hostinger (separadores Unix /)" -ForegroundColor Gray
Write-Host "   - Contém index.html, .htaccess e assets na raiz" -ForegroundColor Gray
Write-Host "   - Sem nenhuma pasta ou código-fonte do repositório" -ForegroundColor Gray
Write-Host "==========================================================================" -ForegroundColor Green
Write-Host "COMO APLICAR NA HOSTINGER:" -ForegroundColor Yellow
Write-Host "1. No hPanel da Hostinger, abra o 'Gerenciador de Arquivos'." -ForegroundColor Yellow
Write-Host "2. Acesse a pasta 'public_html'." -ForegroundColor Yellow
Write-Host "3. Se houver pastas do repositório (frontend, backend, Projeto, etc.), delete-as." -ForegroundColor Yellow
Write-Host "4. Faça upload do arquivo 'deploy-hostinger.zip' e clique com botão direito: 'Extrair'." -ForegroundColor Yellow
Write-Host "==========================================================================" -ForegroundColor Green

Set-Location -Path "$PSScriptRoot/.."
