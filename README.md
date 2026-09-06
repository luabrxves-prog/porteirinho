# RondaSafe

Aplicativo Android para controle e auditoria de rondas de porteiros em prédio comercial, com leitura de QR Codes, operação offline, sincronização com Supabase e acesso administrativo no mesmo APK.

> Nome provisório do produto: **RondaSafe**. O repositório permanece como `porteirinho`.

## Status

Projeto em especificação inicial.

## Documentação

- [Especificação funcional e técnica do MVP](docs/RONDASAFE_SPEC.md)

## Stack definida para o MVP

- Kotlin + Android nativo
- Jetpack Compose
- CameraX + ML Kit Barcode Scanning
- Room
- WorkManager
- Android Keystore + DataStore
- Supabase (Postgres, Auth, RLS, Edge Functions)
- Firebase Cloud Messaging (somente push notifications)

## Referência anterior

O projeto `yasminbrevesr/credenciapass` foi analisado como referência de fluxo de leitura de QR Code, feedback de leitura, prevenção de duplicidade e auditoria. O RondaSafe, porém, será uma aplicação Android nativa e offline-first.
