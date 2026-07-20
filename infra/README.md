# Despliegue del backend en Azure Container Apps

Infraestructura definida en `main.bicep`: Container Apps Environment, el Container App del backend,
Azure Container Registry, Postgres Flexible Server (acceso público restringido por firewall) y Log
Analytics para los logs.

Este documento cubre el **bootstrap manual, que se hace una sola vez**. Después de eso, cada push a
`main` que toque `backend/` se despliega solo vía `.github/workflows/deploy-backend.yml`.

## Prerrequisitos

- Una suscripción de Azure activa.
- [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli) instalado y con sesión iniciada
  (`az login`).
- Permisos para crear resource groups, asignar roles y crear identidades administradas.

## 1. Crear el resource group

```bash
az group create --name wallet-rg --location brazilsouth
```

## 2. Desplegar la infraestructura

Los secretos (`postgresAdminPassword`, `jwtSecret`) **no** están en `main.parameters.json` — se pasan
en el momento, para no committearlos nunca. Generá una password fuerte para Postgres y un secreto de
al menos 32 caracteres para JWT.

```bash
az deployment group create \
  --resource-group wallet-rg \
  --template-file infra/main.bicep \
  --parameters infra/main.parameters.json \
  --parameters postgresAdminPassword='<password-fuerte>' \
  --parameters jwtSecret='<secreto-de-al-menos-32-caracteres>'
```

La primera vez, el Container App levanta con una imagen placeholder pública (el ACR todavía está
vacío) — es esperado; el primer deploy real llega con el paso siguiente.

**Nota**: si esta primera ejecución falla con `IdentityDoesNotExist` en el paso del Container App, es
una demora normal de propagación de la identidad administrada recién creada en Azure AD — simplemente
volvé a correr el mismo comando (es idempotente, solo reintenta el recurso que falló).

Guardá los outputs del deployment (`az deployment group show --resource-group wallet-rg --name main
--query properties.outputs`) — se necesitan `identityClientId`, `identityResourceId`, `acrName` y
`containerAppFqdn` para los pasos siguientes.

## 3. Configurar OIDC para GitHub Actions (sin secretos de larga duración)

**Importante**: el `subject` no es simplemente `repo:<org>/<repo>:ref:refs/heads/<branch>` — GitHub
Actions ahora incluye los IDs numéricos inmutables de la org y el repo (para que un federated
credential no quede apuntando por error a otro recurso si el repo se renombra). Para este repo, el
subject real es `repo:zeroyuno@54330662/wallet@1304442946:ref:refs/heads/main` — se confirmó leyendo
el log de un run fallido (`AADSTS700213: No matching federated identity record found for presented
assertion subject '...'` ya trae el valor exacto que hay que usar). Si cloná este setup para otro
repo, corré primero con cualquier subject, dejá que falle una vez, y copiá el subject real del error.

```bash
az identity federated-credential create \
  --name github-actions-main \
  --identity-name wallet-backend-identity \
  --resource-group wallet-rg \
  --issuer https://token.actions.githubusercontent.com \
  --subject "repo:zeroyuno@54330662/wallet@1304442946:ref:refs/heads/main" \
  --audiences api://AzureADTokenExchange
```

## 4. Darle permiso a la identidad para actualizar el Container App

El rol `AcrPull` sobre el ACR ya lo asigna `main.bicep`. Falta el permiso para que el workflow pueda
correr `az containerapp update`:

```bash
az role assignment create \
  --assignee <identityPrincipalId> \
  --role "Container Apps Contributor" \
  --scope /subscriptions/<subscription-id>/resourceGroups/wallet-rg
```

## 5. Configurar los secrets/variables del repo en GitHub

En Settings → Secrets and variables → Actions del repo `zeroyuno/wallet`:

| Nombre | Valor |
|---|---|
| `AZURE_CLIENT_ID` | `identityClientId` del output del deployment |
| `AZURE_TENANT_ID` | `az account show --query tenantId -o tsv` |
| `AZURE_SUBSCRIPTION_ID` | `az account show --query id -o tsv` |
| `AZURE_RESOURCE_GROUP` | `wallet-rg` |
| `ACR_NAME` | `acrName` del output del deployment |
| `CONTAINER_APP_NAME` | `wallet-backend` |

## Después del bootstrap

Cada push a `main` que toque `backend/**` dispara `.github/workflows/deploy-backend.yml`: corre
`mvn verify`, construye la imagen con `docker build --platform linux/amd64` y la sube al ACR
(autenticando vía el token de la identidad OIDC — `az acr build`/ACR Tasks no está disponible en
suscripciones nuevas sin verificar, ver Notas), y actualiza el Container App. No hace falta volver a
correr nada de este documento salvo que cambie la infraestructura (en ese caso, repetir el paso 2 con
el `main.bicep` actualizado).

## Verificar que quedó levantado

```bash
curl https://<containerAppFqdn>/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

## Notas

- **Si construís la imagen a mano en una Mac Apple Silicon (o cualquier host ARM64), agregá siempre
  `--platform linux/amd64` al `docker build`** — Container Apps corre en amd64, y una imagen ARM64 falla
  el pull con un error confuso ("no match for platform in manifest"), fácil de confundir con un
  problema de permisos/red. Fue la causa real de una sesión larga de troubleshooting antes de llegar a
  esta arquitectura. El workflow de CI ya lo hace bien porque corre en runners `ubuntu-latest` (amd64).
- `minReplicas: 0` — el Container App puede escalar a cero cuando no hay tráfico (ahorra costo en un
  uso personal), a costa de un cold start de unos segundos en el primer request tras estar inactivo.
  Si molesta, subir `minReplicas` a 1 en `main.bicep` y volver a desplegar.
- **Postgres Flexible Server tiene acceso público pero restringido por firewall** (solo servicios de
  Azure, regla `AllowAzureServices`) + SSL obligatorio (`sslmode=require` en `DB_URL`) + password
  fuerte. La primera versión de esta infraestructura integraba Postgres y el Container Apps Environment
  a una VNet privada (sin IP pública en Postgres), pero se revirtió: en esta suscripción, los pulls de
  imagen desde el ACR fallaban de forma consistente y confusa (`unauthorized`/`not found`) únicamente
  cuando el Container Apps Environment estaba VNet-integrado, sin relación real con la causa final (el
  mismatch de arquitectura ARM64/AMD64 de arriba). Si en el futuro se quiere volver a un Postgres 100%
  privado, revisar `git log` de este archivo para la versión con VNet.
- Para conectarse a Postgres a mano (ej. `psql`), agregar temporalmente tu IP a las reglas de firewall:
  `az postgres flexible-server firewall-rule create --name wallet-pg-<sufijo> --resource-group
  wallet-rg --rule-name mi-ip --start-ip-address <tu-ip> --end-ip-address <tu-ip>`.
- `az acr build` (ACR Tasks) devuelve `TasksOperationsNotAllowed` en suscripciones nuevas/no
  verificadas por Microsoft — por eso el workflow de CI construye la imagen con `docker build` en el
  propio runner en vez de delegarlo al ACR.
- La app Android (`android/.../di/NetworkModule.kt`) todavía apunta a la IP LAN de desarrollo — se
  actualiza en un fix aparte ahora que el backend ya está desplegado (`containerAppFqdn` del output).
