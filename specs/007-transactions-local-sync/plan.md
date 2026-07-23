# Implementation Plan: Caché local y sincronización de movimientos en Android

**Branch**: `007-transactions-local-sync` | **Date**: 2026-07-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/007-transactions-local-sync/spec.md`

## Summary

Hoy `TransactionListScreen` lee `GET /api/transactions` en vivo, que devuelve **todos** los
movimientos del usuario sin paginar (~11.000 en la cuenta real) — la pantalla falla/se pone lenta. Este
feature agrega una base de datos local (Room) en Android que pasa a ser la única fuente de datos para
la UI de movimientos (lectura paginada, instantánea, funciona sin red), sincronizada con el backend en
dos direcciones: hacia abajo mediante un endpoint nuevo de sincronización incremental por cursor
(`GET /api/transactions/sync`), y hacia arriba mediante un outbox de cambios pendientes que un worker en
segundo plano envía usando los endpoints de creación/edición/eliminación ya existentes (feature 003),
con reintento automático. El backend sigue siendo la única fuente de verdad; el local es una caché de
lectura/escritura optimista. Alcance: solo movimientos — cuentas/categorías siguen en vivo.

## Technical Context

**Language/Version**: Backend: Java 25 / Spring Boot 4.1.0 (módulo `transaction` ya existente).
Android: Kotlin 2.2.21, Jetpack Compose, sobre `android/app` ya existente. Sin cambios de plataforma.

**Primary Dependencies**: Backend: reutiliza Spring Data JPA/Flyway ya configurados, sin dependencias
nuevas. Android: se agregan **Room** (persistencia local + DAO), **Paging 3 integrado con Room**
(scroll progresivo leyendo la caché local) y **WorkManager** (disparo periódico y con reintento
automático del sync en segundo plano, sobrevive a que la app se cierre) — ninguna existe hoy en el
módulo. Se mantienen Retrofit/OkHttp (siguen siendo el transporte hacia el backend) e Hilt (provee
`AppDatabase`/DAOs igual que ya provee `TransactionApi`).

**Storage**: Backend: PostgreSQL (tabla `transactions` existente + tabla nueva de tombstones de borrado
para el feed de sincronización). Android: SQLite vía Room — nueva base de datos local, primera vez que
el módulo Android persiste datos propios (hasta ahora todo vivía solo en memoria de ViewModel).

**Testing**: Backend: JUnit 5 + Testcontainers para el nuevo endpoint de sync (igual patrón que
`TransactionControllerIT`), unitarios de `TransactionService`/`TransactionRepository` para el cursor y
los tombstones. Android: JUnit 4 + `kotlinx-coroutines-test` para el outbox/worker de sincronización y
para `TransactionViewModel` (ya tiene tests, se extienden), más un test instrumentado mínimo de Room
(DAO) si hace falta verificar queries de paginación.

**Target Platform**: Igual que features anteriores — backend en el Container App de producción, Android
en dispositivo físico contra ese mismo backend.

**Project Type**: mobile-app + api — cambios en ambos proyectos (`backend/`, `android/`), acoplados por
el contrato de sync nuevo.

**Performance Goals**: SC-001 (spec.md): la pantalla de movimientos se muestra en <1s sin importar el
volumen acumulado. SC-003: un cambio local (crear/editar/eliminar) se refleja en la UI en <200ms,
independiente de la red.

**Constraints**: Único usuario, único dispositivo activo — sin resolución de conflictos de edición
concurrente (Assumptions de spec.md). El backend sigue siendo la fuente de verdad; Room es una caché,
no un reemplazo. Mantiene el patrón MVVM (`Composable → ViewModel → Repository → Room/Retrofit`) —
`TransactionRepository` pasa a mediar entre ambas fuentes en vez de llamar solo a Retrofit.

**Scale/Scope**: ~11.000 movimientos históricos a sincronizar la primera vez para el usuario real;
alcance de UI sin cambios visibles más allá de que ahora carga rápido y con scroll progresivo (mismas
pantallas de la feature 004, sin nuevas pantallas).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. API Contract-First**: PASS — el endpoint nuevo (`GET /api/transactions/sync`) se define en
  `contracts/` antes de implementarse; los endpoints de escritura ya existentes (feature 003) se
  reutilizan tal cual, sin improvisar campos.
- **II. Arquitectura hexagonal y DDD**: PASS — el endpoint de sync vive en el módulo `transaction`
  (backend) ya existente, respetando `domain/application/infrastructure`; no se crea un bounded
  context nuevo. Android sigue MVVM sin excepción constitucional (Room vive detrás de
  `TransactionRepository`, la misma capa que ya hablaba con Retrofit).
- **III. Tests y cobertura >80%**: PASS — el trabajo nuevo en `domain`/`application` del backend
  (cursor, tombstones) lleva tests unitarios igual que el resto del módulo; el worker de sync y el DAO
  en Android llevan tests, siguiendo el mismo criterio ya aplicado en la feature 004.
- **IV. Aislamiento de datos por usuario**: PASS — el endpoint de sync filtra por `userId` autenticado
  igual que el resto de `transaction`; la base local en el dispositivo ya es de un único usuario (mismo
  supuesto que el resto de la app Android, sin multi-cuenta en el mismo dispositivo).
- **V. Simplicidad y alcance por spec (YAGNI)**: PASS con una decisión explícita — no se implementa
  resolución de conflictos multi-dispositivo ni un log de eventos genérico; el outbox es un campo de
  estado simple por fila local (ver research.md), y los tombstones de borrado son una tabla lateral
  aditiva en vez de convertir todo `transactions` a soft-delete. Cuentas/categorías quedan
  explícitamente fuera de alcance (spec.md).

No hay violaciones que requieran justificación adicional más allá de lo documentado en Complexity
Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/007-transactions-local-sync/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── transactions-sync-api.yaml
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/walletapp/backend/transaction/
├── domain/
│   ├── Transaction.java                    # [MODIFICADO] + updatedAt, touch() en update()
│   ├── DeletedTransactionTombstone.java    # [NUEVO] id + userId + deletedAt
│   └── TransactionRepository.java          # [MODIFICADO] + findChangedSince(...)/findDeletedSince(...)
├── application/
│   ├── dto/SyncCursor.java                 # [NUEVO] since + limit (entrada) / nextSince + hasMore (salida)
│   └── TransactionService.java             # [MODIFICADO] + sync(userId, cursor)
├── infrastructure/
│   ├── persistence/
│   │   ├── TransactionEntity.java          # [MODIFICADO] + updated_at
│   │   ├── DeletedTransactionEntity.java   # [NUEVO]
│   │   └── JpaTransactionRepository.java   # [MODIFICADO]
│   └── web/
│       ├── TransactionController.java      # [MODIFICADO] + GET /api/transactions/sync
│       └── dto/TransactionSyncResponse.java # [NUEVO]
└── resources/db/migration/
    └── V10__add_transaction_sync_support.sql  # [NUEVO] updated_at + tabla deleted_transactions

android/app/src/main/java/com/walletapp/android/
├── transactions/
│   ├── local/
│   │   ├── TransactionEntity.kt            # [NUEVO] entidad Room (espejo + syncState + pendingSince)
│   │   ├── TransactionDao.kt               # [NUEVO] PagingSource + upsert/delete/pending queries
│   │   └── SyncCursorStore.kt              # [NUEVO] último cursor persistido (DataStore/Room)
│   ├── sync/
│   │   ├── TransactionSyncWorker.kt        # [NUEVO] WorkManager: pull incremental + push de pendientes
│   │   └── TransactionSyncApi.kt           # [NUEVO] GET /api/transactions/sync (Retrofit)
│   ├── TransactionApi.kt                   # sin cambios (create/update/delete ya soportan id de cliente)
│   ├── TransactionRepository.kt            # [MODIFICADO] lee/escribe Room; encola en el outbox; ya no
│   │                                          llama a Retrofit directamente desde la UI
│   └── TransactionViewModel.kt             # [MODIFICADO] observa Flow paginado de Room en vez de list()
├── db/AppDatabase.kt                       # [NUEVO] Room database raíz (si no existe otra ya)
├── di/
│   ├── DatabaseModule.kt                   # [NUEVO] provee AppDatabase/DAOs
│   └── NetworkModule.kt                    # [MODIFICADO] provee TransactionSyncApi
└── WalletApplication.kt                    # [MODIFICADO] agenda el WorkManager periódico al iniciar
```

**Structure Decision**: El endpoint de sync se agrega dentro del módulo `transaction` ya existente en
el backend (mismo bounded context, no uno nuevo). En Android, se agrega un paquete `local/` y `sync/`
dentro de `transactions/` (mismo criterio de organización por feature ya usado), y `TransactionRepository`
pasa a ser el único punto que decide cuándo leer/escribir Room vs. cuándo disparar sync — la UI
(`TransactionViewModel`) no sabe que existe Room ni Retrofit por separado, solo consume un `Flow`
paginado del repositorio, preservando `Composable → ViewModel → Repository → (Room + Retrofit)`.

## Complexity Tracking

| Decisión | Por qué es necesaria | Alternativa más simple descartada porque |
|-----------|------------|---------------------------------------|
| Se agregan 3 dependencias nuevas a Android (Room, Paging 3, WorkManager) | Es el corazón de la feature: caché local paginada + sincronización resiliente en segundo plano — no hay forma de cumplir SC-001/SC-002 sin persistencia local, ni FR-007 (reintento automático) sin un mecanismo que sobreviva al cierre de la app | Implementar una caché en memoria (ViewModel) — rechazada: se pierde al cerrar la app (no cumple SC-002, usable offline) y no sobrevive para reintentar envíos pendientes en segundo plano |
| Tabla lateral `deleted_transactions` (tombstones) en vez de soft-delete en `transactions` | El feed de sincronización necesita saber qué se borró desde el último cursor; sin tombstone no hay forma de que el cliente se entere de una eliminación sin volver a traer la lista completa | Convertir `transactions` a soft-delete (`deletedAt` + filtrar en cada query existente) — rechazada: toca todas las queries/tests ya existentes del módulo (`findAllByUserId`, `sumNetAmountForAccount`, balance, imports de las features 005/006) para un caso que una tabla lateral resuelve de forma aislada, mismo patrón ya usado en `statement_import_line_hashes`/`import_external_ref` |
| Outbox como campo de estado por fila local (`syncState`) en vez de una cola de eventos genérica | Único usuario/único dispositivo (Assumptions de spec.md) — alcanza con saber, por movimiento, si tiene un cambio pendiente y de qué tipo | Una cola de eventos de sincronización separada (event sourcing local) — rechazada: resolvería reordenar/replayar múltiples cambios por fila, útil para multi-dispositivo con conflictos, que está explícitamente fuera de alcance |
| Idempotencia de reintento (FR-008) resuelta en el cliente (tratar 409 de una creación pendiente como éxito) en vez de cambiar el backend | El endpoint `POST /api/transactions` ya es estricto con ids duplicados por diseño (feature 003/004, protege contra colisiones reales) y tiene un test que fija ese contrato | Modificar `TransactionService.create()` para que un id duplicado devuelva el existente en vez de fallar — rechazada: cambiaría semántica y contrato ya establecido y testeado para todos los llamadores (no solo sync), por un caso que el cliente puede resolver interpretando el 409 que ya recibe |
