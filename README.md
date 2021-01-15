# Azure Private Link Connections

This is a repo where I experiment with [Azure Java SDK](https://docs.microsoft.com/en-us/azure/developer/java/sdk/).

Specifically, I want to list incoming Private Link connections and accept them. [Learn more about Azure Private Link](https://docs.microsoft.com/en-us/azure/private-link/).

# Usage

**Note: don't use it. It's POC quality.**

## Create credentials for the app

The default role for the app -- Contributor -- is too open [src](https://docs.microsoft.com/en-us/cli/azure/create-an-azure-service-principal-azure-cli).

> The default role for a service principal is Contributor. This role has full permissions to read and write to an Azure account. The Reader role is more restrictive, with read-only access. For more information on Role-Based Access Control (RBAC) and roles, see RBAC: Built-in roles.


Let's create a new more restrictive role in the portal. Follow [this guide](https://docs.microsoft.com/en-us/azure/role-based-access-control/custom-roles-portal). Use the following json:

```js
{
    "properties": {
        "roleName": "PrivateLinkConnectionAcceptor",
        "description": "This role can list and modify Private Link connections. Main usecase it to auto-accept pending connection requests and to translate subscription ID to linkID.",
        "assignableScopes": [
            "/subscriptions/..."  // your subscription
        ],
        "permissions": [
            {
                "actions": [
                    "Microsoft.Network/privateLinkServices/read",
                    "Microsoft.Network/privateLinkServices/privateEndpointConnections/read",
                    "Microsoft.Network/privateLinkServices/privateEndpointConnections/write"
                ],
                "notActions": [],
                "dataActions": [],
                "notDataActions": []
            }
        ]
    }
}
```


Create a service user:
```
$ az ad sp create-for-rbac --name igor-test-java-sdk --role  PrivateLinkConnectionAcceptor
Changing "igor-test-java-sdk" to a valid URI of "http://igor-test-java-sdk", which is the required format used for service principal names
Creating 'PrivateLinkConnectionAcceptor' role assignment under scope '/subscriptions/...'
  Retrying role assignment creation: 1/36
The output includes credentials that you must protect. Be sure that you do not include these credentials in your code or check the credentials into your source control. For more information, see https://aka.ms/azadsp-cli
{
  "appId": "...",
  "displayName": "igor-test-java-sdk",
  "name": "http://igor-test-java-sdk",
  "password": "...",
  "tenant": "..."
}
```

Export the values to env
```
AZURE_CLIENT_ID=...  // this is "appId"
AZURE_CLIENT_SECRET=...
AZURE_TENANT_ID=...
AZURE_SUBSCRIPTION_ID=...
```

## List all connections for a given subscription

```
-s <subscription-id>
```

```
{"connection_id": "/subscriptions/.../resourceGroups/igor-test/providers/Microsoft.Network/privateLinkServices/igor-test-privatelink-service/privateEndpointConnections/igor-test-private-link.b9d40cb4-baf7-4930-bb2b-dd3e43ecf83b", "name": "...", "linkID": "123456", "status": "Pending", endpointID: "...", "resourceGroup": "igor-test", serviceName: "igor-test-privatelink-service"}
...
```

## List all pending connections

(To the subscription for which we've created the token)

```
-p
```

```
{"connection_id": "/subscriptions/.../resourceGroups/igor-test/providers/Microsoft.Network/privateLinkServices/igor-test-privatelink-service/privateEndpointConnections/igor-test-private-link.b9d40cb4-baf7-4930-bb2b-dd3e43ecf83b", "name": "...", "linkID": "123456", "status": "Pending", endpointID: "...", "resourceGroup": "igor-test", serviceName: "igor-test-privatelink-service"}

```

## Approve the connection

```
-a /subscriptions/.../resourceGroups/igor-test/providers/Microsoft.Network/privateLinkServices/igor-test-privatelink-service/privateEndpointConnections/igor-test-private-link.b9d40cb4-baf7-4930-bb2b-dd3e43ecf83b
```

# Notes

...
