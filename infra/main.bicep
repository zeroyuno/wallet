// Infraestructura del backend en Azure Container Apps. Ver infra/README.md para el bootstrap manual
// (una sola vez) y specs de referencia en el plan de despliegue de esta feature.
//
// Nota sobre la ausencia de VNet: la primera versión de este archivo integraba el Container Apps
// Environment y Postgres Flexible Server a una VNet privada (Postgres sin IP pública). Se revirtió
// porque, en esta suscripción, los pulls de imagen desde el ACR fallaban consistentemente
// ("unauthorized"/"not found") únicamente cuando el Container Apps Environment estaba VNet-integrado
// — con identidad administrada, con credenciales admin del ACR, y con un tag nuevo (se descartó
// caché); el propio `az acr check-health` confirmaba el registro sano. No se pudo abrir un ticket de
// soporte de Azure para confirmar la causa exacta (el plan gratuito no lo permite). Postgres pasa a
// acceso público restringido por firewall (solo servicios de Azure) + password fuerte + SSL
// obligatorio en vez de aislamiento de red completo — un trade-off razonable, no ideal.

@description('Región de Azure para todos los recursos.')
param location string = 'brazilsouth'

@description('Prefijo usado para nombrar los recursos.')
param namePrefix string = 'wallet'

@description('Usuario administrador de Postgres Flexible Server.')
param postgresAdminUsername string = 'walletadmin'

@description('Password del administrador de Postgres. Se pasa en el momento del deploy, nunca se commitea.')
@secure()
param postgresAdminPassword string

@description('Clave de firma JWT (>= 32 caracteres). Se pasa en el momento del deploy, nunca se commitea.')
@secure()
param jwtSecret string

@description('Imagen inicial del Container App. La primera vez apunta a un placeholder público porque el ACR todavía está vacío; el workflow de CI la reemplaza en el primer deploy real.')
param containerImage string = 'mcr.microsoft.com/azuredocs/containerapps-helloworld:latest'

var acrName = '${namePrefix}acr${uniqueString(resourceGroup().id)}'
var postgresServerName = '${namePrefix}-pg-${uniqueString(resourceGroup().id)}'
var databaseName = 'wallet'
var identityName = '${namePrefix}-backend-identity'
var acrPullRoleId = '7f951dda-4ed3-4680-a7ca-43fe172d538d'
// AcrPush ya incluye permisos de pull, pero se mantiene el rol AcrPull explícito abajo para dejar
// claro cuál lo necesita solo para pullear (el Container App) — el workflow de CI usa AcrPush.
var acrPushRoleId = '8311e382-0749-4cb8-b61a-304f252e45ec'

// --- Observabilidad ---

resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: '${namePrefix}-logs'
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: 30
  }
}

// --- Base de datos: Postgres Flexible Server, acceso público restringido por firewall ---
// (ver nota al inicio del archivo sobre por qué no está en una VNet privada)

resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2023-06-01-preview' = {
  name: postgresServerName
  location: location
  sku: {
    name: 'Standard_B1ms'
    tier: 'Burstable'
  }
  properties: {
    version: '17'
    administratorLogin: postgresAdminUsername
    administratorLoginPassword: postgresAdminPassword
    storage: {
      storageSizeGB: 32
    }
    network: {
      publicNetworkAccess: 'Enabled'
    }
    highAvailability: {
      mode: 'Disabled'
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
  }
}

resource postgresDatabase 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2023-06-01-preview' = {
  parent: postgres
  name: databaseName
}

// Regla especial de Azure (start=end=0.0.0.0): permite acceso solo desde recursos de Azure
// (incluye el Container App), no desde internet en general.
resource postgresFirewallAzureServices 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2023-06-01-preview' = {
  parent: postgres
  name: 'AllowAzureServices'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

// --- Registro de contenedores ---

resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: acrName
  location: location
  sku: {
    name: 'Basic'
  }
  properties: {
    adminUserEnabled: false
  }
}

// --- Identidad administrada, usada por el Container App (pull) y por el workflow de CI vía OIDC
// (push) — ver federated credential y roles en README ---

resource identity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: identityName
  location: location
}

resource acrPullRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(acr.id, identity.id, acrPullRoleId)
  scope: acr
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', acrPullRoleId)
    principalId: identity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

resource acrPushRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(acr.id, identity.id, acrPushRoleId)
  scope: acr
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', acrPushRoleId)
    principalId: identity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

// --- Container Apps Environment (sin VNet, ver nota al inicio del archivo) ---

resource containerAppsEnvironment 'Microsoft.App/managedEnvironments@2023-05-01' = {
  name: '${namePrefix}-env'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalytics.properties.customerId
        sharedKey: logAnalytics.listKeys().primarySharedKey
      }
    }
  }
}

// --- Container App del backend ---

resource backendApp 'Microsoft.App/containerApps@2023-05-01' = {
  name: '${namePrefix}-backend'
  location: location
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${identity.id}': {}
    }
  }
  properties: {
    managedEnvironmentId: containerAppsEnvironment.id
    configuration: {
      ingress: {
        external: true
        targetPort: 8080
        transport: 'auto'
      }
      registries: [
        {
          server: acr.properties.loginServer
          identity: identity.id
        }
      ]
      secrets: [
        {
          name: 'db-password'
          value: postgresAdminPassword
        }
        {
          name: 'jwt-secret'
          value: jwtSecret
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'backend'
          image: containerImage
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: [
            {
              name: 'DB_URL'
              value: 'jdbc:postgresql://${postgres.properties.fullyQualifiedDomainName}:5432/${databaseName}?sslmode=require'
            }
            {
              name: 'DB_USER'
              value: postgresAdminUsername
            }
            {
              name: 'DB_PASSWORD'
              secretRef: 'db-password'
            }
            {
              name: 'JWT_SECRET'
              secretRef: 'jwt-secret'
            }
          ]
          probes: [
            {
              type: 'Liveness'
              httpGet: {
                path: '/actuator/health'
                port: 8080
              }
              initialDelaySeconds: 10
              periodSeconds: 15
            }
            {
              type: 'Readiness'
              httpGet: {
                path: '/actuator/health'
                port: 8080
              }
              initialDelaySeconds: 5
              periodSeconds: 10
            }
          ]
        }
      ]
      scale: {
        minReplicas: 0
        maxReplicas: 1
      }
    }
  }
  dependsOn: [
    acrPullRoleAssignment
  ]
}

output acrLoginServer string = acr.properties.loginServer
output acrName string = acr.name
output containerAppName string = backendApp.name
output containerAppFqdn string = backendApp.properties.configuration.ingress.fqdn
output identityClientId string = identity.properties.clientId
output identityPrincipalId string = identity.properties.principalId
output identityResourceId string = identity.id
output postgresFqdn string = postgres.properties.fullyQualifiedDomainName
