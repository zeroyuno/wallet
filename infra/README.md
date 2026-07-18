# Despliegue del backend en Azure Container Apps

Infraestructura definida en `main.bicep`: Container Apps Environment (VNet-integrado), el Container
App del backend, Azure Container Registry, Postgres Flexible Server (acceso privado, sin IP pública) y
Log Analytics para los logs.

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

Guardá los outputs del deployment (`az deployment group show --resource-group wallet-rg --name main
--query properties.outputs`) — se necesitan `identityClientId`, `identityResourceId`, `acrName` y
`containerAppFqdn` para los pasos siguientes.

## 3. Configurar OIDC para GitHub Actions (sin secretos de larga duración)

```bash
az identity federated-credential create \
  --name github-actions-main \
  --identity-name wallet-backend-identity \
  --resource-group wallet-rg \
  --issuer https://token.actions.githubusercontent.com \
  --subject repo:zeroyuno/wallet:ref:refs/heads/main \
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
`mvn verify`, construye la imagen, la sube al ACR y actualiza el Container App. No hace falta volver a
correr nada de este documento salvo que cambie la infraestructura (en ese caso, repetir el paso 2 con
el `main.bicep` actualizado).

## Verificar que quedó levantado

```bash
curl https://<containerAppFqdn>/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

## Notas

- `minReplicas: 0` — el Container App puede escalar a cero cuando no hay tráfico (ahorra costo en un
  uso personal), a costa de un cold start de unos segundos en el primer request tras estar inactivo.
  Si molesta, subir `minReplicas` a 1 en `main.bicep` y volver a desplegar.
- Postgres Flexible Server no tiene IP pública — solo es alcanzable desde dentro de la VNet (donde vive
  el Container App). Para conectarse a mano (ej. `psql`) hace falta un jump host en la misma VNet o
  usar `az postgres flexible-server connect` con VNet peering temporal.
- La app Android (`android/.../di/NetworkModule.kt`) todavía apunta a la IP LAN de desarrollo — se
  actualiza en un fix aparte una vez que este backend esté desplegado y tengamos la URL de producción.
