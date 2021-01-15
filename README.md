# Azure Private Link Connections

This is a repo where I experiment with [Azure Java SDK](https://docs.microsoft.com/en-us/azure/developer/java/sdk/).

Specifically, I want to list incoming Private Link connections and accept them. [Learn more about Azure Private Link](https://docs.microsoft.com/en-us/azure/private-link/).

# Overview

The code is in a single java file [Connections.java](./src/main/java/info/kupczynski/azure/privatelink/Connections.java).

Pass `-h` to see help:
```
Azure Private Link Connection Helper

Flags:
-h                 :  show help
-s SUBSCRIPTION_ID :  show connections for the given subscription
-p                 :  list pending connections
-a CONNECTION_ID   :  approve the connection with the given ID

Required env variables:
- AZURE_CLIENT_ID
- AZURE_CLIENT_SECRET
- AZURE_TENANT_ID
- AZURE_SUBSCRIPTION_ID
```

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

- We can't use [`AzureResourceManager`](https://github.com/Azure/azure-sdk-for-java/blob/4eb4c4b/sdk/resourcemanager/azure-resourcemanager/src/main/java/com/azure/resourcemanager/AzureResourceManager.java#L125) which is the main entry point to SDK.
    - It doesn't cover Private Link connections.
- The SDK has a huge footprint

<details>
<summary>runtimeClasspath</summary>

```shell
$ ./gradlew -q dependencies
..
runtimeClasspath - Runtime classpath of source set 'main'.
+--- com.azure:azure-identity:1.2.2
|    +--- com.azure:azure-core:1.12.0
|    |    +--- com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.11.3
|    |    |    +--- com.fasterxml.jackson.core:jackson-annotations:2.11.3
|    |    |    +--- com.fasterxml.jackson.core:jackson-core:2.11.3
|    |    |    \--- com.fasterxml.jackson.core:jackson-databind:2.11.3
|    |    |         +--- com.fasterxml.jackson.core:jackson-annotations:2.11.3
|    |    |         \--- com.fasterxml.jackson.core:jackson-core:2.11.3
|    |    +--- com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.11.3
|    |    |    +--- com.fasterxml.jackson.core:jackson-core:2.11.3
|    |    |    +--- com.fasterxml.jackson.core:jackson-annotations:2.11.3
|    |    |    +--- com.fasterxml.jackson.core:jackson-databind:2.11.3 (*)
|    |    |    +--- com.fasterxml.jackson.module:jackson-module-jaxb-annotations:2.11.3
|    |    |    |    +--- com.fasterxml.jackson.core:jackson-annotations:2.11.3
|    |    |    |    +--- com.fasterxml.jackson.core:jackson-core:2.11.3
|    |    |    |    +--- com.fasterxml.jackson.core:jackson-databind:2.11.3 (*)
|    |    |    |    +--- jakarta.xml.bind:jakarta.xml.bind-api:2.3.2
|    |    |    |    |    \--- jakarta.activation:jakarta.activation-api:1.2.1
|    |    |    |    \--- jakarta.activation:jakarta.activation-api:1.2.1
|    |    |    +--- org.codehaus.woodstox:stax2-api:4.2.1
|    |    |    \--- com.fasterxml.woodstox:woodstox-core:6.2.1
|    |    |         \--- org.codehaus.woodstox:stax2-api:4.2.1
|    |    +--- org.slf4j:slf4j-api:1.7.30
|    |    +--- io.projectreactor:reactor-core:3.3.12.RELEASE
|    |    |    \--- org.reactivestreams:reactive-streams:1.0.3
|    |    \--- io.netty:netty-tcnative-boringssl-static:2.0.35.Final
|    +--- com.azure:azure-core-http-netty:1.7.1
|    |    +--- com.azure:azure-core:1.12.0 (*)
|    |    +--- io.netty:netty-handler:4.1.54.Final
|    |    |    +--- io.netty:netty-common:4.1.54.Final
|    |    |    +--- io.netty:netty-resolver:4.1.54.Final
|    |    |    |    \--- io.netty:netty-common:4.1.54.Final
|    |    |    +--- io.netty:netty-buffer:4.1.54.Final
|    |    |    |    \--- io.netty:netty-common:4.1.54.Final
|    |    |    +--- io.netty:netty-transport:4.1.54.Final
|    |    |    |    +--- io.netty:netty-common:4.1.54.Final
|    |    |    |    +--- io.netty:netty-buffer:4.1.54.Final (*)
|    |    |    |    \--- io.netty:netty-resolver:4.1.54.Final (*)
|    |    |    \--- io.netty:netty-codec:4.1.54.Final
|    |    |         +--- io.netty:netty-common:4.1.54.Final
|    |    |         +--- io.netty:netty-buffer:4.1.54.Final (*)
|    |    |         \--- io.netty:netty-transport:4.1.54.Final (*)
|    |    +--- io.netty:netty-handler-proxy:4.1.54.Final
|    |    |    +--- io.netty:netty-common:4.1.54.Final
|    |    |    +--- io.netty:netty-buffer:4.1.54.Final (*)
|    |    |    +--- io.netty:netty-transport:4.1.54.Final (*)
|    |    |    +--- io.netty:netty-codec:4.1.54.Final (*)
|    |    |    +--- io.netty:netty-codec-socks:4.1.54.Final
|    |    |    |    +--- io.netty:netty-common:4.1.54.Final
|    |    |    |    +--- io.netty:netty-buffer:4.1.54.Final (*)
|    |    |    |    +--- io.netty:netty-transport:4.1.54.Final (*)
|    |    |    |    \--- io.netty:netty-codec:4.1.54.Final (*)
|    |    |    \--- io.netty:netty-codec-http:4.1.54.Final
|    |    |         +--- io.netty:netty-common:4.1.54.Final
|    |    |         +--- io.netty:netty-buffer:4.1.54.Final (*)
|    |    |         +--- io.netty:netty-transport:4.1.54.Final (*)
|    |    |         +--- io.netty:netty-codec:4.1.54.Final (*)
|    |    |         \--- io.netty:netty-handler:4.1.54.Final (*)
|    |    +--- io.netty:netty-buffer:4.1.54.Final (*)
|    |    +--- io.netty:netty-codec-http:4.1.54.Final (*)
|    |    +--- io.netty:netty-codec-http2:4.1.54.Final
|    |    |    +--- io.netty:netty-common:4.1.54.Final
|    |    |    +--- io.netty:netty-buffer:4.1.54.Final (*)
|    |    |    +--- io.netty:netty-transport:4.1.54.Final (*)
|    |    |    +--- io.netty:netty-codec:4.1.54.Final (*)
|    |    |    +--- io.netty:netty-handler:4.1.54.Final (*)
|    |    |    \--- io.netty:netty-codec-http:4.1.54.Final (*)
|    |    +--- io.netty:netty-transport-native-unix-common:4.1.54.Final
|    |    |    +--- io.netty:netty-common:4.1.54.Final
|    |    |    +--- io.netty:netty-buffer:4.1.54.Final (*)
|    |    |    \--- io.netty:netty-transport:4.1.54.Final (*)
|    |    +--- io.netty:netty-transport-native-epoll:4.1.54.Final
|    |    |    +--- io.netty:netty-common:4.1.54.Final
|    |    |    +--- io.netty:netty-buffer:4.1.54.Final (*)
|    |    |    +--- io.netty:netty-transport:4.1.54.Final (*)
|    |    |    \--- io.netty:netty-transport-native-unix-common:4.1.54.Final (*)
|    |    +--- io.netty:netty-transport-native-kqueue:4.1.54.Final
|    |    |    +--- io.netty:netty-common:4.1.54.Final
|    |    |    +--- io.netty:netty-buffer:4.1.54.Final (*)
|    |    |    +--- io.netty:netty-transport:4.1.54.Final (*)
|    |    |    \--- io.netty:netty-transport-native-unix-common:4.1.54.Final (*)
|    |    \--- io.projectreactor.netty:reactor-netty:0.9.15.RELEASE
|    |         +--- io.netty:netty-codec-http:4.1.54.Final (*)
|    |         +--- io.netty:netty-codec-http2:4.1.54.Final (*)
|    |         +--- io.netty:netty-handler:4.1.54.Final (*)
|    |         +--- io.netty:netty-handler-proxy:4.1.54.Final (*)
|    |         +--- io.netty:netty-transport-native-epoll:4.1.54.Final (*)
|    |         \--- io.projectreactor:reactor-core:3.3.12.RELEASE (*)
|    +--- net.minidev:json-smart:2.3
|    |    \--- net.minidev:accessors-smart:1.2
|    |         \--- org.ow2.asm:asm:5.0.4
|    +--- com.microsoft.azure:msal4j:1.8.0
|    |    +--- com.nimbusds:oauth2-oidc-sdk:7.4
|    |    |    +--- com.github.stephenc.jcip:jcip-annotations:1.0-1
|    |    |    +--- com.nimbusds:content-type:2.0
|    |    |    +--- net.minidev:json-smart:[1.3.1,2.3] -> 2.3 (*)
|    |    |    +--- com.nimbusds:lang-tag:1.4.4
|    |    |    +--- com.nimbusds:nimbus-jose-jwt:8.14.1
|    |    |    |    +--- com.github.stephenc.jcip:jcip-annotations:1.0-1
|    |    |    |    \--- net.minidev:json-smart:[1.3.1,2.3] -> 2.3 (*)
|    |    |    \--- com.sun.mail:javax.mail:1.6.1
|    |    |         \--- javax.activation:activation:1.1
|    |    +--- org.slf4j:slf4j-api:1.7.28 -> 1.7.30
|    |    \--- com.fasterxml.jackson.core:jackson-databind:2.10.1 -> 2.11.3 (*)
|    +--- com.microsoft.azure:msal4j-persistence-extension:1.0.0
|    |    +--- com.microsoft.azure:msal4j:1.4.0 -> 1.8.0 (*)
|    |    +--- net.java.dev.jna:jna:5.5.0 -> 5.6.0
|    |    +--- net.java.dev.jna:jna-platform:5.5.0 -> 5.6.0
|    |    |    \--- net.java.dev.jna:jna:5.6.0
|    |    \--- org.slf4j:slf4j-api:1.7.7 -> 1.7.30
|    +--- com.nimbusds:oauth2-oidc-sdk:7.1.1 -> 7.4 (*)
|    +--- net.java.dev.jna:jna-platform:5.6.0 (*)
|    \--- org.linguafranca.pwdb:KeePassJava2:2.1.4
|         +--- org.linguafranca.pwdb:KeePassJava2-kdb:2.1.4
|         |    +--- org.linguafranca.pwdb:database:2.1.4
|         |    |    +--- org.jetbrains:annotations:15.0
|         |    |    +--- com.google.guava:guava:19.0
|         |    |    \--- com.madgag.spongycastle:core:1.54.0.0
|         |    \--- org.jetbrains:annotations:15.0
|         +--- org.linguafranca.pwdb:KeePassJava2-dom:2.1.4
|         |    \--- org.linguafranca.pwdb:KeePassJava2-kdbx:2.1.4
|         |         +--- org.linguafranca.pwdb:database:2.1.4 (*)
|         |         +--- commons-codec:commons-codec:1.10
|         |         \--- org.jetbrains:annotations:15.0
|         +--- org.linguafranca.pwdb:KeePassJava2-jaxb:2.1.4
|         |    \--- org.linguafranca.pwdb:KeePassJava2-kdbx:2.1.4 (*)
|         \--- org.linguafranca.pwdb:KeePassJava2-simple:2.1.4
|              +--- org.linguafranca.pwdb:KeePassJava2-kdbx:2.1.4 (*)
|              +--- org.simpleframework:simple-xml:2.7.1
|              |    +--- stax:stax-api:1.0.1
|              |    +--- stax:stax:1.2.0
|              |    |    \--- stax:stax-api:1.0.1
|              |    \--- xpp3:xpp3:1.1.3.3
|              +--- org.apache.httpcomponents:httpcore:4.4.5
|              \--- com.fasterxml:aalto-xml:1.0.0
|                   \--- org.codehaus.woodstox:stax2-api:4.0.0 -> 4.2.1
+--- com.azure.resourcemanager:azure-resourcemanager-network:2.1.0
|    \--- com.azure.resourcemanager:azure-resourcemanager-resources:2.1.0
|         +--- com.azure:azure-core:1.10.0 -> 1.12.0 (*)
|         \--- com.azure:azure-core-management:1.0.0
|              \--- com.azure:azure-core:1.8.1 -> 1.12.0 (*)
\--- org.apache.logging.log4j:log4j-slf4j-impl:2.14.0
     +--- org.slf4j:slf4j-api:1.7.25 -> 1.7.30
     +--- org.apache.logging.log4j:log4j-api:2.14.0
     \--- org.apache.logging.log4j:log4j-core:2.14.0
          \--- org.apache.logging.log4j:log4j-api:2.14.0
..
```

</details>
