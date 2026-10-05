# Gera o arquivo único para um banco novo; preserva as migrações como fontes.
$ErrorActionPreference = 'Stop'
$databaseDirectory = $PSScriptRoot
$header = @'
-- MARVEL LOBBY: INSTALACAO INICIAL + FASE 1 SOCIAL
-- Banco novo: abra este arquivo no Query Tool do pgAdmin e execute inteiro.
-- Use a conexao administrativa da Aiven. Nao altere/crie senhas neste SQL.
-- Nao apaga tabelas. Execute apenas uma vez: nao e um script de reset.
-- Se o schema marvel_lobby ja existe, use SOMENTE 003_social_identity.sql.
-- Depois crie o service user marvel_lobby_api na Aiven e execute
-- 002_runtime_permissions.sql com a conexao administrativa do pgAdmin.

'@
$initialMigration = [IO.File]::ReadAllText((Join-Path $databaseDirectory '001_initial.sql'))
$socialMigration = [IO.File]::ReadAllText((Join-Path $databaseDirectory '003_social_identity.sql'))
$setupPath = Join-Path $databaseDirectory '000_setup_phase1.sql'
[IO.File]::WriteAllText($setupPath, $header + "`r`n" + $initialMigration + "`r`n`r`n" + $socialMigration, [Text.UTF8Encoding]::new($false))
Write-Output '000_setup_phase1.sql atualizado.'
