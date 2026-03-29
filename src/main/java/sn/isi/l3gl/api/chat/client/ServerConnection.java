package sn.isi.l3gl.api.chat.client;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import sn.isi.l3gl.api.chat.util.ChatProtocol;

import java.net.URI;
import java.util.function.Consumer;

/**
 * ServerConnection WebSocket : remplace la connexion TCP.
 * Meme interface que l'ancienne version -> aucun changement dans les Controllers.
 */
public class ServerConnection {

    private WebSocketClient wsClient;
    private volatile Consumer<String> messageHandler;
    private volatile boolean running = false;

    public ServerConnection(Consumer<String> messageHandler) {
        this.messageHandler = messageHandler;
    }

    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    /**
     * Ouvre la connexion WebSocket avec le serveur.
     *
     * @param host ex: "ton-serveur.onrender.com" ou "localhost"
     * @param port ex: 8080
     * @throws Exception si la connexion echoue
     */
    public void connect(String host, int port) throws Exception {
        // ws:// en local, wss:// en production (Render)
        String scheme = host.equals("localhost") ? "ws" : "wss";
        URI uri = new URI(scheme + "://" + host + ":" + port);

        wsClient = new WebSocketClient(uri) {

            @Override
            public void onOpen(ServerHandshake handshake) {
                running = true;
            }

            @Override
            public void onMessage(String message) {
                Consumer<String> h = messageHandler;
                if (h != null) h.accept(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                running = false;
                Consumer<String> h = messageHandler;
                if (h != null) h.accept("DISCONNECTED|Connexion perdue");
            }

            @Override
            public void onError(Exception ex) {
                running = false;
                Consumer<String> h = messageHandler;
                if (h != null) h.accept("DISCONNECTED|Erreur: " + ex.getMessage());
            }
        };

        // Connexion bloquante avec timeout
        wsClient.connectBlocking();
    }

    /**
     * Envoie une commande brute au serveur
     */
    public void send(String command) {
        if (wsClient != null && wsClient.isOpen()) {
            wsClient.send(command);
        }
    }

    /**
     * Deconnexion propre
     */
    public void disconnect() {
        running = false;
        try {
            if (wsClient != null && wsClient.isOpen()) {
                wsClient.send(ChatProtocol.LOGOUT);
                wsClient.closeBlocking();
            }
        } catch (InterruptedException ignored) {}
    }

    public boolean isConnected() {
        return wsClient != null && wsClient.isOpen() && running;
    }

    // ===== Methodes de commodite : inchangees =====

    public void sendLogin(String username, String password) {
        send(ChatProtocol.LOGIN + ChatProtocol.SEP_WRITE + username + ChatProtocol.SEP_WRITE + password);
    }

    public void sendRegister(String username, String password, String role) {
        send(ChatProtocol.REGISTER + ChatProtocol.SEP_WRITE + username
                + ChatProtocol.SEP_WRITE + password + ChatProtocol.SEP_WRITE + role);
    }

    public void sendMessage(String receiver, String content) {
        send(ChatProtocol.MSG + ChatProtocol.SEP_WRITE + receiver + ChatProtocol.SEP_WRITE + content);
    }

    public void requestUserList() {
        send(ChatProtocol.LIST_USERS);
    }

    public void requestHistory(String interlocuteur) {
        send(ChatProtocol.HISTORY + ChatProtocol.SEP_WRITE + interlocuteur);
    }

    public void requestAllUsers() {
        send(ChatProtocol.ALL_USERS);
    }
}