package sn.isi.l3gl.api.chat.client;

import sn.isi.l3gl.api.chat.server.ChatServer;
import sn.isi.l3gl.api.chat.util.ChatProtocol;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * ServerConnection : gere la connexion reseau du cote CLIENT.
 * <p>
 * Responsabilites :
 * - Etablit la connexion TCP avec le serveur
 * - Envoie des commandes au serveur
 * - Ecoute les reponses en arriere-plan (thread dedie)
 * - Notifie le Controller des messages recus via un callback
 * <p>
 * POURQUOI UN THREAD SEPARE ?
 * readLine() est BLOQUANT : il attend qu'une ligne arrive du serveur.
 * Si on l'appelait dans le thread JavaFX (FX Application Thread),
 * l'interface se figerait completement pendant l'attente.
 * Le thread separe ecoute en arriere-plan sans bloquer l'UI.
 */
public class ServerConnection {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    /**
     * Consumer<String> : interface fonctionnelle Java = une fonction String -> void.
     * "volatile" garantit que tous les threads voient la valeur la plus recente.
     * On change ce handler quand on passe de LoginController a ChatController.
     */
    private volatile Consumer<String> messageHandler;

    /**
     * Flag thread-safe pour controler l'arret de l'ecoute
     */
    private volatile boolean running = false;

    public ServerConnection(Consumer<String> messageHandler) {
        this.messageHandler = messageHandler;
    }

    /**
     * Permet de remplacer le handler en cours d'execution.
     * Appele lors de la transition Login -> Chat.
     * La connexion reste ouverte, seul le destinataire des messages change.
     */
    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    /**
     * Ouvre la connexion TCP avec le serveur.
     * Un Socket represente un canal de communication bidirectionnel.
     *
     * @param host nom ou IP du serveur
     * @param port port d'ecoute (ChatServer.PORT = 9000)
     * @throws IOException si le serveur n'est pas accessible
     */
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        running = true;
        startListeningThread();
    }

    /**
     * Lance le thread d'ecoute des messages serveur.
     * Ce thread vit pendant toute la session et appelle messageHandler
     * a chaque message recu.
     */
    private void startListeningThread() {
        Thread t = new Thread(() -> {
            try {
                String line;
                while (running && (line = in.readLine()) != null) {
                    // Capture locale pour eviter une race condition
                    // si setMessageHandler() est appele au meme moment
                    Consumer<String> h = messageHandler;
                    if (h != null) h.accept(line);
                }
            } catch (IOException e) {
                if (running) {
                    Consumer<String> h = messageHandler;
                    // RG10 : notification de perte de connexion
                    if (h != null) h.accept("DISCONNECTED|Connexion perdue");
                }
            }
        });
        t.setDaemon(true);
        t.setName("ServerListener");
        t.start();
    }

    /**
     * Envoie une commande brute au serveur
     */
    public void send(String command) {
        if (out != null && socket != null && !socket.isClosed()) {
            out.println(command);
        }
    }

    /**
     * Deconnexion propre : previent le serveur puis ferme le socket
     */
    public void disconnect() {
        running = false;
        try {
            if (out != null) out.println(ChatProtocol.LOGOUT);
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {
        }
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed() && running;
    }

    // ===== Methodes de commodite : encapsulent le protocole =====

    public void sendLogin(String username, String password) {
        send(ChatProtocol.LOGIN + ChatProtocol.SEP_WRITE + username + ChatProtocol.SEP_WRITE + password);
    }

    public void sendRegister(String username, String password, String role) {
        send(ChatProtocol.REGISTER + ChatProtocol.SEP_WRITE + username + ChatProtocol.SEP_WRITE + password + ChatProtocol.SEP_WRITE + role);
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