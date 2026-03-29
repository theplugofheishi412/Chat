package sn.isi.l3gl.api.chat.server;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.mindrot.jbcrypt.BCrypt;
import sn.isi.l3gl.api.chat.dao.MessageDao;
import sn.isi.l3gl.api.chat.dao.UserDao;
import sn.isi.l3gl.api.chat.model.Message;
import sn.isi.l3gl.api.chat.model.User;
import sn.isi.l3gl.api.chat.util.ChatProtocol;
import sn.isi.l3gl.api.chat.util.HibernateUtil;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer extends WebSocketServer {

    // username -> WebSocket
    private final Map<String, WebSocket> connectedClients = new ConcurrentHashMap<>();
    // WebSocket -> User (pour retrouver l'utilisateur a la deconnexion)
    private final Map<WebSocket, User> socketToUser = new ConcurrentHashMap<>();

    private final UserDao userDao = new UserDao();
    private final MessageDao messageDao = new MessageDao();

    private static final DateTimeFormatter LOG_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    public ChatServer(int port) {
        super(new InetSocketAddress(port));
    }

    // ===================== LIFECYCLE =====================

    @Override
    public void onStart() {
        log("Serveur WebSocket demarre sur le port " + getPort());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        log("Nouvelle connexion depuis: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        handleCommand(conn, message);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        disconnect(conn);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        log("ERREUR: " + (conn != null ? conn.getRemoteSocketAddress() : "?")
                + " -> " + ex.getMessage());
    }

    // ===================== ROUTAGE DES COMMANDES =====================

    private void handleCommand(WebSocket conn, String line) {
        String[] parts = line.split(ChatProtocol.SEP, -1);
        String command = parts[0];

        switch (command) {
            case ChatProtocol.REGISTER   -> handleRegister(conn, parts);
            case ChatProtocol.LOGIN      -> handleLogin(conn, parts);
            case ChatProtocol.LOGOUT     -> handleLogout(conn);
            case ChatProtocol.MSG        -> handleMessage(conn, parts);
            case ChatProtocol.LIST_USERS -> handleListUsers(conn);
            case ChatProtocol.HISTORY    -> handleHistory(conn, parts);
            case ChatProtocol.ALL_USERS  -> handleAllUsers(conn);
            default -> send(conn, "ERROR|Commande inconnue: " + command);
        }
    }

    // ===================== HANDLERS =====================

    /**
     * RG1, RG9 : inscription avec verification unicite + hashage BCrypt
     */
    private void handleRegister(WebSocket conn, String[] parts) {
        if (parts.length < 4) {
            send(conn, ChatProtocol.REGISTER_FAIL + "|Parametres manquants");
            return;
        }

        String username = parts[1];
        String password = parts[2];
        String roleStr  = parts[3];

        if (userDao.existsByUsername(username)) {
            send(conn, ChatProtocol.REGISTER_FAIL + "|Ce username est deja utilise");
            return;
        }

        try {
            User.Role role = User.Role.valueOf(roleStr);
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
            User user = new User(username, hashedPassword, role);
            userDao.save(user);
            log("Inscription: " + username + " [" + role + "]");
            send(conn, ChatProtocol.REGISTER_OK);
        } catch (IllegalArgumentException e) {
            send(conn, ChatProtocol.REGISTER_FAIL + "|Role invalide: " + roleStr);
        }
    }

    /**
     * RG2, RG3, RG4, RG6 : authentification
     */
    private void handleLogin(WebSocket conn, String[] parts) {
        if (parts.length < 3) {
            send(conn, ChatProtocol.LOGIN_FAIL + "|Parametres manquants");
            return;
        }

        String username = parts[1];
        String password = parts[2];

        User user = userDao.findByUsername(username);

        if (user == null) {
            send(conn, ChatProtocol.LOGIN_FAIL + "|Username introuvable");
            return;
        }

        if (!BCrypt.checkpw(password, user.getPassword())) {
            send(conn, ChatProtocol.LOGIN_FAIL + "|Mot de passe incorrect");
            return;
        }

        // RG3 : deja connecte ?
        if (connectedClients.containsKey(username)) {
            send(conn, ChatProtocol.LOGIN_FAIL + "|Deja connecte sur une autre session");
            return;
        }

        // Connexion validee
        connectedClients.put(username, conn);
        socketToUser.put(conn, user);

        // RG4 : statut ONLINE
        userDao.updateStatus(username, User.Status.ONLINE);
        user.setStatus(User.Status.ONLINE);

        log("Connexion: " + username);
        send(conn, ChatProtocol.LOGIN_OK + ChatProtocol.SEP_WRITE + user.getRole());

        broadcastUserList();

        // RG6 : messages en attente
        deliverPendingMessages(conn, username);
    }

    private void handleLogout(WebSocket conn) {
        send(conn, ChatProtocol.LOGOUT_OK);
        disconnect(conn);
    }

    /**
     * RG2, RG5, RG6, RG7 : envoi de message
     */
    private void handleMessage(WebSocket conn, String[] parts) {
        User sender = socketToUser.get(conn);

        if (sender == null) {
            send(conn, ChatProtocol.SENT_FAIL + "|Non authentifie");
            return;
        }
        if (parts.length < 3) {
            send(conn, ChatProtocol.SENT_FAIL + "|Parametres manquants");
            return;
        }

        String receiverName = parts[1];
        String contenu      = parts[2];

        // RG7 : validation
        if (contenu == null || contenu.trim().isEmpty()) {
            send(conn, ChatProtocol.SENT_FAIL + "|Le message ne peut pas etre vide");
            return;
        }
        if (contenu.length() > 1000) {
            send(conn, ChatProtocol.SENT_FAIL + "|Message trop long (max 1000 caracteres)");
            return;
        }

        // RG5 : destinataire existe ?
        User receiver = userDao.findByUsername(receiverName);
        if (receiver == null) {
            send(conn, ChatProtocol.SENT_FAIL + "|Destinataire introuvable: " + receiverName);
            return;
        }

        // Sauvegarde en base
        Message msg = new Message(sender, receiver, contenu);
        messageDao.save(msg);

        String heure = msg.getDateEnvoi().format(TIME_FORMAT);

        send(conn, ChatProtocol.SENT_OK);
        log("MSG: " + sender.getUsername() + " -> " + receiverName + ": " + contenu);

        // RG6 : livraison immediate si destinataire connecte
        WebSocket receiverConn = connectedClients.get(receiverName);
        if (receiverConn != null) {
            send(receiverConn,
                    ChatProtocol.INCOMING + ChatProtocol.SEP_WRITE +
                            sender.getUsername() + ChatProtocol.SEP_WRITE +
                            contenu + ChatProtocol.SEP_WRITE + heure);
            msg.setStatut(Message.Statut.RECU);
            messageDao.update(msg);
        }
    }

    private void handleListUsers(WebSocket conn) {
        User user = socketToUser.get(conn);
        if (user == null) { send(conn, "ERROR|Non authentifie"); return; }

        StringBuilder sb = new StringBuilder(ChatProtocol.LIST_RESULT);
        for (String username : connectedClients.keySet()) {
            sb.append(ChatProtocol.SEP_WRITE).append(username).append(":ONLINE");
        }
        send(conn, sb.toString());
    }

    /**
     * RG8 : historique en ordre chronologique
     */
    private void handleHistory(WebSocket conn, String[] parts) {
        User user = socketToUser.get(conn);
        if (user == null) { send(conn, "ERROR|Non authentifie"); return; }
        if (parts.length < 2) { send(conn, "ERROR|Parametres manquants"); return; }

        String interlocuteur = parts[1];
        List<Message> messages = messageDao.findConversation(
                user.getUsername(), interlocuteur);

        for (Message msg : messages) {
            String heure = msg.getDateEnvoi().format(TIME_FORMAT);
            send(conn, ChatProtocol.HIST_MSG + ChatProtocol.SEP_WRITE +
                    msg.getSender().getUsername() + ChatProtocol.SEP_WRITE +
                    msg.getContenu() + ChatProtocol.SEP_WRITE + heure);
        }
        send(conn, ChatProtocol.HIST_END);
    }

    /**
     * RG13 : liste complete reservee aux ORGANISATEURS
     */
    private void handleAllUsers(WebSocket conn) {
        User user = socketToUser.get(conn);
        if (user == null) { send(conn, "ERROR|Non authentifie"); return; }

        if (user.getRole() != User.Role.ORGANISATEUR) {
            send(conn, "ERROR|Acces refuse - role insuffisant");
            return;
        }

        List<User> allUsers = userDao.findAll();
        StringBuilder sb = new StringBuilder(ChatProtocol.LIST_RESULT);
        for (User u : allUsers) {
            sb.append(ChatProtocol.SEP_WRITE)
                    .append(u.getUsername()).append(":").append(u.getStatus());
        }
        send(conn, sb.toString());
    }

    // ===================== UTILITAIRES =====================

    /**
     * RG6 : livraison des messages en attente a la reconnexion
     */
    private void deliverPendingMessages(WebSocket conn, String username) {
        List<Message> pending = messageDao.findPendingMessages(username);
        if (!pending.isEmpty()) {
            for (Message msg : pending) {
                String heure = msg.getDateEnvoi().format(TIME_FORMAT);
                send(conn, ChatProtocol.INCOMING + ChatProtocol.SEP_WRITE +
                        msg.getSender().getUsername() + ChatProtocol.SEP_WRITE +
                        msg.getContenu() + ChatProtocol.SEP_WRITE + heure);
            }
            messageDao.markAsReceived(username);
            log("Messages en attente livres a " + username + " (" + pending.size() + ")");
        }
    }

    /**
     * RG4, RG12 : deconnexion propre
     */
    private void disconnect(WebSocket conn) {
        User user = socketToUser.remove(conn);
        if (user != null) {
            String username = user.getUsername();
            connectedClients.remove(username);
            userDao.updateStatus(username, User.Status.OFFLINE);
            log("Deconnexion: " + username);
            broadcastUserList();
        }
    }

    /**
     * Broadcast de la liste des connectes a tous les clients
     */
    public void broadcastUserList() {
        StringBuilder sb = new StringBuilder(ChatProtocol.LIST_RESULT);
        for (String username : connectedClients.keySet()) {
            sb.append(ChatProtocol.SEP_WRITE).append(username).append(":ONLINE");
        }
        String msg = sb.toString();
        for (WebSocket conn : connectedClients.values()) {
            send(conn, msg);
        }
    }

    private void send(WebSocket conn, String message) {
        if (conn != null && conn.isOpen()) {
            conn.send(message);
        }
    }

    public void log(String message) {
        System.out.println("[" + LocalDateTime.now().format(LOG_FORMAT) + "] " + message);
    }

    // ===================== MAIN =====================

    public static void main(String[] args) {
        // Render injecte le port via variable d'environnement PORT
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;

        ChatServer server = new ChatServer(port);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.log("Arret du serveur...");
            HibernateUtil.close();
        }));
    }
}