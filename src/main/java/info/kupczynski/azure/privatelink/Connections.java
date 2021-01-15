package info.kupczynski.azure.privatelink;

import com.azure.core.http.HttpPipeline;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.network.fluent.NetworkManagementClient;
import com.azure.resourcemanager.network.fluent.models.PrivateEndpointConnectionInner;
import com.azure.resourcemanager.network.implementation.NetworkManagementClientBuilder;
import com.azure.resourcemanager.network.models.PrivateLinkServiceConnectionState;
import com.azure.resourcemanager.resources.fluentcore.utils.HttpPipelineProvider;

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Connections lists and accepts Azure Private Link connections
 */
public class Connections {

    public static final Pattern PRIVATE_CONNECTION_ID_REGEXP = Pattern.compile("/subscriptions/([^/]+)/resourceGroups/(?<resourceGroup>[^/]+)/providers/Microsoft.Network/privateLinkServices/(?<serviceName>[^/]+)/privateEndpointConnections/(?<connectionName>.+)");

    private enum Opt {
        HELP("-h", 0),
        LIST_CONNECTIONS_FOR_SUBSCRIPTION("-s", 1),
        LIST_CONNECTIONS_IN_PENDING_STATE("-p", 0),
        APPROVE_CONNECTION("-a", 1);

        final String flag;
        final int requiredArgs;

        Opt(String flag, int requiredArgs) {
            this.flag = flag;
            this.requiredArgs = requiredArgs;
        }
    }

    private static class Config {
        public final String clientID;
        public final String clientSecret;
        public final String tenantID;
        public final String subscriptionID;

        private Config(String clientID, String clientSecret, String tenantID, String subscriptionID) {
            this.clientID = clientID;
            this.clientSecret = clientSecret;
            this.tenantID = tenantID;
            this.subscriptionID = subscriptionID;
        }

        public static Config fromEnv() {
            String clientID = System.getenv("AZURE_CLIENT_ID");
            String clientSecret = System.getenv("AZURE_CLIENT_SECRET");
            String tenantID = System.getenv("AZURE_TENANT_ID");
            String subscriptionID = System.getenv("AZURE_SUBSCRIPTION_ID");
            if (clientID == null || clientSecret == null || tenantID == null || subscriptionID == null) {
                return null;
            }
            return new Config(clientID, clientSecret, tenantID, subscriptionID);
        }
    }

    public static void main(String[] args) {
        Opt opt = parseOpts(args);
        if (opt == Opt.HELP) {
            showUsage();
            System.exit(0);
        }

        Config config = Config.fromEnv();
        if (config == null) {
            showUsage();
            System.exit(0);
        }

        Connections connections = new Connections(config);
        switch (opt) {
            case LIST_CONNECTIONS_FOR_SUBSCRIPTION -> connections.listConnectionsForSubscription(args[1]);
            case LIST_CONNECTIONS_IN_PENDING_STATE -> connections.listConnectionsInPendingState();
            case APPROVE_CONNECTION -> connections.approveConnection(args[1]);
        }
    }

    private static Opt parseOpts(String[] args) {
        if (args.length < 1) {
            return Opt.HELP;
        }

        String flag = args[0];
        for (Opt o : Opt.values()) {
            if (o.flag.equals(flag) && args.length > o.requiredArgs) {
                return o;
            }
        }

        return Opt.HELP;
    }


    private static void showUsage() {
        System.out.println("Azure Private Link Connection Helper");
        System.out.println("");
        System.out.println("Flags:");
        System.out.println("-h                 :  show help");
        System.out.println("-s SUBSCRIPTION_ID :  show connections for the given subscription");
        System.out.println("-p                 :  list pending connections");
        System.out.println("-a CONNECTION_ID   :  approve the connection with the given ID");
        System.out.println("");
        System.out.println("Required env variables:");
        System.out.println("- AZURE_CLIENT_ID");
        System.out.println("- AZURE_CLIENT_SECRET");
        System.out.println("- AZURE_TENANT_ID");
        System.out.println("- AZURE_SUBSCRIPTION_ID");
    }

    private final NetworkManagementClient client;

    public Connections(Config config) {
        client = getNetworkManagementClient(
                config.clientID,
                config.clientSecret,
                config.tenantID,
                config.subscriptionID
        );
    }

    private static NetworkManagementClient getNetworkManagementClient(
            String clientID,
            String clientSecret,
            String tenantID,
            String subscriptionID
    ) {
        ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .clientId(clientID)
                .clientSecret(clientSecret)
                .tenantId(tenantID)
                .build();

        AzureProfile profile = new AzureProfile(tenantID, subscriptionID, AzureEnvironment.AZURE);

        HttpPipeline httpPipeline = HttpPipelineProvider.buildHttpPipeline(credential, profile);

        return new NetworkManagementClientBuilder()
                .pipeline(httpPipeline)
                .endpoint(profile.getEnvironment().getResourceManagerEndpoint())
                .subscriptionId(profile.getSubscriptionId())
                .buildClient();
    }

    private void listConnectionsForSubscription(String subscriptionID) {
        listConnections(conn -> conn.isSubscription(subscriptionID))
                .map(ConnectionDetails::asJson)
                .forEach(System.out::println);
    }

    private void listConnectionsInPendingState() {
        listConnections(ConnectionDetails::isPending)
                .map(ConnectionDetails::asJson)
                .forEach(System.out::println);
    }

    private Stream<ConnectionDetails> listConnections(Predicate<ConnectionDetails> filter) {
        return client.getPrivateLinkServices().list().stream()
                .flatMap(service ->
                        service.privateEndpointConnections().stream()
                                .map(ConnectionDetails::of)
                                .filter(filter)
                );
    }

    private void approveConnection(String connectionID) {
        Matcher matcher = PRIVATE_CONNECTION_ID_REGEXP.matcher(connectionID);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(String.format(
                    "Connection id='%s' doesn't match the pattern '%s'",
                    connectionID,
                    PRIVATE_CONNECTION_ID_REGEXP.pattern()
            ));
        }

        String resourceGroup = matcher.group("resourceGroup");
        String serviceName = matcher.group("serviceName");
        String connectionName = matcher.group("connectionName");

        // TODO: check if exists
        PrivateEndpointConnectionInner conn = client.getPrivateLinkServices()
                .getPrivateEndpointConnection(resourceGroup, serviceName, connectionName);
        PrivateLinkServiceConnectionState approved = conn.privateLinkServiceConnectionState()
                .withStatus("Approved")
                .withDescription("Your linkID is " + conn.linkIdentifier());
        conn.withPrivateLinkServiceConnectionState(approved);
        client.getPrivateLinkServices().updatePrivateEndpointConnection(resourceGroup, serviceName, connectionName, conn);
    }

    private final static class ConnectionDetails {
        public final String id;
        public final String name;
        public final String linkID;
        public final String status;
        public final String endpointID;

        public final String resourceGroup;
        public final String serviceName;

        private ConnectionDetails(String id, String name, String linkID, String status, String endpointID, String resourceGroup, String serviceName) {
            this.id = id;
            this.name = name;
            this.linkID = linkID;
            this.status = status;
            this.endpointID = endpointID;
            this.resourceGroup = resourceGroup;
            this.serviceName = serviceName;
        }

        public boolean isSubscription(String subscriptionID) {
            return endpointID.startsWith("/subscriptions/" + subscriptionID);
        }

        public boolean isPending() {
            return status.equals("Pending");
        }

        public String asJson() {
            return String.format("""
                    {"connection_id": "%s", "name": "%s", "linkID": "%s", "status": "%s", endpointID: "%s", "resourceGroup": "%s", serviceName: "%s"}""",
                    id, name, linkID, status, endpointID, resourceGroup, serviceName);
        }

        public static ConnectionDetails of(PrivateEndpointConnectionInner conn) {
            Matcher matcher = PRIVATE_CONNECTION_ID_REGEXP.matcher(conn.id());
            if (!matcher.matches()) {
                throw new IllegalArgumentException(String.format(
                        "Connection id='%s' doesn't match the pattern '%s'",
                        conn.id(),
                        PRIVATE_CONNECTION_ID_REGEXP.pattern()
                ));
            }

            return new ConnectionDetails(
                    conn.id(),
                    conn.name(),
                    conn.linkIdentifier(),
                    conn.privateLinkServiceConnectionState().status(),
                    conn.privateEndpoint().id(),
                    matcher.group("resourceGroup"),
                    matcher.group("serviceName")
            );
        }
    }
}
