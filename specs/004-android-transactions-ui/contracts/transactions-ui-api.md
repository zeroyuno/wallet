# Contrato consumido por esta feature

Esta feature no define ningún endpoint nuevo. Consume tal cual el contrato ya publicado y ya
implementado en la feature 003:

- **Referencia**: [`specs/003-transactions/contracts/transactions-api.yaml`](../../003-transactions/contracts/transactions-api.yaml)
- Endpoints usados: `GET/POST /api/transactions`, `GET/PUT/DELETE /api/transactions/{id}`,
  `GET /api/accounts/{id}/balance`.
- Por principio I de la constitución (API Contract-First), cualquier cambio de contrato necesario para
  esta UI debería reflejarse primero en ese archivo — en la práctica no se necesitó ninguno: la API tal
  como quedó implementada en la feature 003 cubre los cuatro user stories de esta spec sin cambios.
