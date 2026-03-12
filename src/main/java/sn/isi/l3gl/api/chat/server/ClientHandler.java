package sn.isi.l3gl.api.chat.server;

import sn.isi.l3gl.api.chat.dao.MessageDao;
import sn.isi.l3gl.api.chat.dao.UserDao;
import sn.isi.l3gl.api.chat.model.Message;
import sn.isi.l3gl.api.chat.model.User;
import sn.isi.l3gl.api.chat.util.ChatProtocol;
import org.mindrot.jbcrypt.BCrypt;

import java.io.*;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ClientHandler : gere la communication avec UN client connecte.
 *
 * RG11 : Chaque client est gere dans un thread separe.
 * ClientHandler implemente Runnable -> peut etre execute dans un Thread.
 *
 * Cycle de vie :
 * 1. Un client se connecte -> le serveur cree un ClientHandler
 * 2. Le handler tourne en boucle dans son thread, lisant les commandes
 * 3. Quand le client se deconnecte, le handler nettoie et se termine
 */
public class ClientHandler implements Runnable {

    private final Socket socket;            // connexion reseau avec le client
    private final ChatServer server;        // reference au serveur pour acceder aux autres clients
    private PrintWriter out;                // flux sortant (serveur -> client)
    private BufferedReader in;              // flux entrant (client -> serveur)

    private User connectedUser;             // l'utilisateur authentifie sur ce client (null si pas encore connecte)

    private final UserDao userDao = new UserDao();
    private final MessageDao messageDao = new MessageDao();

    // Format pour afficher les heures dans les messages
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * @param socket le socket de connexion avec ce client specifique
     * @param server reference au serveur principal (pour broadcaster, acceder aux autres handlers)
     */
    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    /**
     * Point d'entree du thread.
     * Initialise les flux puis entre dans la boucle de lecture des commandes.
     */
    @Override
    public void run() {
        try {
            // OutputStreamWriter : convertit les octets en caracteres (UTF-8)
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            // BufferedReader : lit ligne par ligne (chaque commande = une ligne)
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

            String line;
            // Boucle principale : lit et traite les commandes jusqu'a deconnexion
            while ((line = in.readLine()) != null) {
                handleCommand(line);
            }

        } catch (IOException e) {
            // Perte de connexion reseau (RG10 cote serveur)
            server.log("Perte de connexion client: " + e.getMessage());
        } finally {
            // Nettoyage systematique, meme en cas d'erreur
            disconnect();
        }
    }

    /**
     * Analyse et traite une commande recue du client.
     * Le format est : COMMANDE|param1|param2|...
     *
     * @param line la ligne brute recue
     */
    private void handleCommand(String line) {
        // Decoupage selon le separateur du protocole
        String[] parts = line.split(ChatProtocol.SEP, -1);
        // parts[0] = la commande, parts[1..n] = les parametres
        String command = parts[0];

        switch (command) {
            case ChatProtocol.REGISTER  -> handleRegister(parts);
            case ChatProtocol.LOGIN     -> handleLogin(parts);
            case ChatProtocol.LOGOUT    -> handleLogout();
            case ChatProtocol.MSG       -> handleMessage(parts);
            case ChatProtocol.LIST_USERS-> handleListUsers();
            case ChatProtocol.HISTORY   -> handleHistory(parts);
            case ChatProtocol.ALL_USERS -> handleAllUsers();
            default -> send("ERROR|Commande inconnue: " + command);
        }
    }

    /**
     * Gere l'inscription d'un nouvel utilisateur.
     * Format : REGISTER|username|password|ROLE
     *
     * Verifie RG1 (username unique) et hache le mot de passe (RG9).
     */
    private void handleRegister(String[] parts) {
        if (parts.length < 4) { send(ChatProtocol.REGISTER_FAIL + "|Parametres manquants"); return; }

        String username = parts[1];
        String password = parts[2];
        String roleStr  = parts[3];

        // RG1 : verification de l'unicite du username
        if (userDao.existsByUsername(username)) {
            send(ChatProtocol.REGISTER_FAIL + "|Ce username est deja utilise");
            return;
        }

        try {
            User.Role role = User.Role.valueOf(roleStr);

            // RG9 : hachage du mot de passe avec BCrypt avant stockage
            // BCrypt.hashpw() genere un hash irreversible et sale automatiquement
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

            User user = new User(username, hashedPassword, role);
            userDao.save(user);

            server.log("Inscription: " + username + " [" + role + "]");
            send(ChatProtocol.REGISTER_OK);

        } catch (IllegalArgumentException e) {
            send(ChatProtocol.REGISTER_FAIL + "|Role invalide: " + roleStr);
        }
    }

    /**
     * Gere la connexion d'un utilisateur existant.
     * Format : LOGIN|username|password
     *
     * RG2 : verifie l'authentification
     * RG3 : verifie qu'il n'est pas deja connecte
     * RG4 : met le statut a ONLINE
     * RG6 : livre les messages en attente
     */
    private void handleLogin(String[] parts) {
        if (parts.length < 3) { send(ChatProtocol.LOGIN_FAIL + "|Parametres manquants"); return; }

        String username = parts[1];
        String password = parts[2];

        User user = userDao.findByUsername(username);

        // Verification : utilisateur existe ?
        if (user == null) {
            send(ChatProtocol.LOGIN_FAIL + "|Username introuvable");
            return;
        }

        // RG9 : BCrypt.checkpw() compare le mot de passe avec le hash stocke
        if (!BCrypt.checkpw(password, user.getPassword())) {
            send(ChatProtocol.LOGIN_FAIL + "|Mot de passe incorrect");
            return;
        }

        // RG3 : verification qu'il n'est pas deja connecte ailleurs
        if (server.isConnected(username)) {
            send(ChatProtocol.LOGIN_FAIL + "|Deja connecte sur une autre session");
            return;
        }

        // Connexion validee
        this.connectedUser = user;

        // RG4 : passage du statut a ONLINE
        userDao.updateStatus(username, User.Status.ONLINE);
        user.setStatus(User.Status.ONLINE);

        // Enregistrement du handler dans la map du serveur
        server.addClient(username, this);

        server.log("Connexion: " + username);

        // Reponse au client avec son role (pour adapter l'UI)
        send(ChatProtocol.LOGIN_OK + ChatProtocol.SEP_WRITE + user.getRole());

        // Broadcast : notifier tous les clients de la nouvelle connexion
        server.broadcastUserList();

        // RG6 : livraison des messages recus pendant la deconnexion
        deliverPendingMessages(username);
    }

    /**
     * Gere la deconnexion propre d'un utilisateur.
     * RG4 : statut passe a OFFLINE.
     */
    private void handleLogout() {
        send(ChatProtocol.LOGOUT_OK);
        disconnect();
    }

    /**
     * Gere l'envoi d'un message a un autre utilisateur.
     * Format : MSG|destinataire|contenu
     *
     * RG2 : expediteur doit etre connecte
     * RG5 : destinataire doit exister
     * RG6 : si destinataire offline, sauvegarde en base pour livraison ulterieure
     * RG7 : contenu non vide et max 1000 caracteres
     */
    private void handleMessage(String[] parts) {
        // RG2 : authentification obligatoire
        if (connectedUser == null) { send(ChatProtocol.SENT_FAIL + "|Non authentifie"); return; }
        if (parts.length < 3)     { send(ChatProtocol.SENT_FAIL + "|Parametres manquants"); return; }

        String receiverName = parts[1];
        String contenu      = parts[2];

        // RG7 : validation du contenu
        if (contenu == null || contenu.trim().isEmpty()) {
            send(ChatProtocol.SENT_FAIL + "|Le message ne peut pas etre vide");
            return;
        }
        if (contenu.length() > 1000) {
            send(ChatProtocol.SENT_FAIL + "|Message trop long (max 1000 caracteres)");
            return;
        }

        // RG5 : le destinataire doit exister en base
        User receiver = userDao.findByUsername(receiverName);
        if (receiver == null) {
            send(ChatProtocol.SENT_FAIL + "|Destinataire introuvable: " + receiverName);
            return;
        }

        // Sauvegarde du message en base (toujours, qu'il soit online ou offline)
        Message msg = new Message(connectedUser, receiver, contenu);
        messageDao.save(msg);

        // Formatage de l'heure pour l'affichage
        String heure = msg.getDateEnvoi().format(TIME_FORMAT);

        // Confirmation a l'expediteur
        send(ChatProtocol.SENT_OK);

        server.log("MSG: " + connectedUser.getUsername() + " -> " + receiverName + ": " + contenu);

        // RG6 : si le destinataire est connecte, livraison immediate
        ClientHandler receiverHandler = server.getClient(receiverName);
        if (receiverHandler != null) {
            // Push du message vers le client destinataire
            receiverHandler.send(
                    ChatProtocol.INCOMING + ChatProtocol.SEP_WRITE +
                            connectedUser.getUsername() + ChatProtocol.SEP_WRITE +
                            contenu + ChatProtocol.SEP_WRITE + heure
            );
            // Marquer comme RECU immediatement
            msg.setStatut(Message.Statut.RECU);
            messageDao.update(msg);
        }
        // Sinon : le message reste en base avec statut ENVOYE, sera livre a la reconnexion
    }

    /**
     * Envoie la liste des utilisateurs connectes a ce client.
     * Format reponse : LIST_RESULT|user1:ONLINE|user2:ONLINE|...
     */
    private void handleListUsers() {
        if (connectedUser == null) { send("ERROR|Non authentifie"); return; }

        StringBuilder sb = new StringBuilder(ChatProtocol.LIST_RESULT);
        // Recupere tous les utilisateurs ONLINE depuis la map du serveur
        for (String username : server.getConnectedUsernames()) {
            sb.append(ChatProtocol.SEP_WRITE).append(username).append(":ONLINE");
        }
        send(sb.toString());
    }

    /**
     * Envoie l'historique d'une conversation (RG8 : ordre chronologique).
     * Format requete : HISTORY|username_interlocuteur
     * Format reponse : N messages HIST_MSG puis HIST_END
     */
    private void handleHistory(String[] parts) {
        if (connectedUser == null) { send("ERROR|Non authentifie"); return; }
        if (parts.length < 2) { send("ERROR|Parametres manquants"); return; }

        String interlocuteur = parts[1];
        List<Message> messages = messageDao.findConversation(
                connectedUser.getUsername(), interlocuteur);

        // Envoi de chaque message un par un
        for (Message msg : messages) {
            String heure = msg.getDateEnvoi().format(TIME_FORMAT);
            send(ChatProtocol.HIST_MSG + ChatProtocol.SEP_WRITE +
                    msg.getSender().getUsername() + ChatProtocol.SEP_WRITE +
                    msg.getContenu() + ChatProtocol.SEP_WRITE + heure);
        }

        // Signal de fin d'historique
        send(ChatProtocol.HIST_END);
    }

    /**
     * Envoie la liste complete de tous les membres inscrits (RG13).
     * Accessible uniquement aux ORGANISATEURS.
     */
    private void handleAllUsers() {
        if (connectedUser == null) { send("ERROR|Non authentifie"); return; }

        // RG13 : restriction aux ORGANISATEURS
        if (connectedUser.getRole() != User.Role.ORGANISATEUR) {
            send("ERROR|Acces refuse - role insuffisant");
            return;
        }

        List<User> allUsers = userDao.findAll();
        StringBuilder sb = new StringBuilder(ChatProtocol.LIST_RESULT);
        for (User u : allUsers) {
            sb.append(ChatProtocol.SEP_WRITE)
                    .append(u.getUsername()).append(":").append(u.getStatus());
        }
        send(sb.toString());
    }

    /**
     * Livre les messages en attente apres reconnexion d'un utilisateur (RG6).
     *
     * @param username l'utilisateur qui vient de se connecter
     */
    private void deliverPendingMessages(String username) {
        List<Message> pending = messageDao.findPendingMessages(username);

        if (!pending.isEmpty()) {
            for (Message msg : pending) {
                String heure = msg.getDateEnvoi().format(TIME_FORMAT);
                send(ChatProtocol.INCOMING + ChatProtocol.SEP_WRITE +
                        msg.getSender().getUsername() + ChatProtocol.SEP_WRITE +
                        msg.getContenu() + ChatProtocol.SEP_WRITE + heure);
            }
            // Marquer tous ces messages comme RECU
            messageDao.markAsReceived(username);
            server.log("Messages en attente livres a " + username + " (" + pending.size() + " messages)");
        }
    }

    /**
     * Deconnexion propre du client.
     * RG4 : statut -> OFFLINE
     * RG12 : log de la deconnexion
     */
    private void disconnect() {
        if (connectedUser != null) {
            String username = connectedUser.getUsername();

            // RG4 : statut OFFLINE en base
            userDao.updateStatus(username, User.Status.OFFLINE);

            // Retrait de la map des clients connectes
            server.removeClient(username);

            // RG12 : journalisation
            server.log("Deconnexion: " + username);

            connectedUser = null;

            // Broadcast : notifier les autres clients
            server.broadcastUserList();
        }

        // Fermeture du socket reseau
        try { socket.close(); } catch (IOException ignored) {}
    }

    /**
     * Envoie une ligne au client via le flux de sortie.
     * PrintWriter.println() envoie la ligne + retour chariot.
     *
     * @param message le message a envoyer
     */
    public void send(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    /**
     * Retourne le nom de l'utilisateur connecte sur ce handler.
     * Utilise par le serveur pour la liste des connectes.
     */
    public String getConnectedUsername() {
        return connectedUser != null ? connectedUser.getUsername() : null;
    }
}