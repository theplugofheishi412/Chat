package sn.isi.l3gl.api.chat.server;

import sn.isi.l3gl.api.chat.util.ChatProtocol;
import sn.isi.l3gl.api.chat.util.HibernateUtil;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatServer : serveur principal de l'application de messagerie.
 *
 * Responsabilites :
 * - Ecoute les connexions entrantes sur un port TCP
 * - Cree un thread (ClientHandler) par client connecte (RG11)
 * - Maintient la liste des clients connectes
 * - Permet le broadcast (envoi a tous les clients)
 * - Journalise les evenements (RG12)
 *
 * ARCHITECTURE MULTI-THREAD :
 * Le thread principal accepte les connexions.
 * Chaque client tourne dans son propre thread (ClientHandler).
 * ConcurrentHashMap est thread-safe (pas de race condition).
 */
public class ChatServer {

    /** Port d'ecoute du serveur. Le client doit se connecter au meme port. */
    public static final int PORT = 9000;

    /**
     * Map des clients connectes : username -> ClientHandler.
     *
     * ConcurrentHashMap : version thread-safe de HashMap.
     * Plusieurs threads peuvent lire/ecrire simultanement sans se bloquer.
     * Essential ici car chaque ClientHandler tourne dans son propre thread.
     */
    private final Map<String, ClientHandler> connectedClients = new ConcurrentHashMap<>();

    /** Format de l'horodatage dans les logs (RG12) */
    private static final DateTimeFormatter LOG_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Demarre le serveur :
     * 1. Initialise Hibernate (connexion base de donnees)
     * 2. Ouvre le ServerSocket sur le port defini
     * 3. Boucle infinie d'acceptation de connexions
     */
    public void start() {
        log("Serveur demarre sur le port " + PORT);
        log("En attente de connexions...");

        // Hook d'arret : execute quand le serveur s'arrete (Ctrl+C ou shutdown)
        // Permet de fermer proprement la connexion Hibernate
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log("Arret du serveur...");
            HibernateUtil.close();
        }));

        // ServerSocket : socket cote serveur, ecoute les connexions entrantes
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            // Boucle infinie : le serveur accepte des connexions jusqu'a l'arret
            while (true) {
                // accept() est BLOQUANT : attend qu'un client se connecte
                // Quand un client arrive, retourne un Socket pour communiquer avec lui
                Socket clientSocket = serverSocket.accept();
                log("Nouvelle connexion depuis: " + clientSocket.getInetAddress().getHostAddress());

                // Creation du handler pour ce client
                ClientHandler handler = new ClientHandler(clientSocket, this);

                // RG11 : chaque client dans un thread separe
                // Un thread est une unite d'execution independante
                Thread thread = new Thread(handler);
                thread.setDaemon(true); // daemon = s'arrete quand le programme principal s'arrete
                thread.start();         // demarre le thread -> appelle handler.run()
            }

        } catch (IOException e) {
            log("ERREUR serveur: " + e.getMessage());
        }
    }

    /**
     * Enregistre un client authentifie dans la map des connectes.
     *
     * @param username le nom de l'utilisateur connecte
     * @param handler  son gestionnaire de connexion
     */
    public void addClient(String username, ClientHandler handler) {
        connectedClients.put(username, handler);
    }

    /**
     * Retire un client de la map (a la deconnexion).
     *
     * @param username le nom de l'utilisateur qui se deconnecte
     */
    public void removeClient(String username) {
        connectedClients.remove(username);
    }

    /**
     * Verifie si un utilisateur est actuellement connecte (RG3).
     *
     * @param username le nom a verifier
     * @return true si deja connecte
     */
    public boolean isConnected(String username) {
        return connectedClients.containsKey(username);
    }

    /**
     * Retourne le handler d'un client connecte (pour lui envoyer un message direct).
     *
     * @param username le nom de l'utilisateur cible
     * @return son ClientHandler, ou null s'il est deconnecte
     */
    public ClientHandler getClient(String username) {
        return connectedClients.get(username);
    }

    /**
     * Retourne la liste des noms d'utilisateurs connectes.
     *
     * @return ensemble des usernames connectes
     */
    public Set<String> getConnectedUsernames() {
        return connectedClients.keySet();
    }

    /**
     * Retourne tous les handlers des clients connectes.
     * Utilise pour le broadcast.
     */
    public Collection<ClientHandler> getAllClients() {
        return connectedClients.values();
    }

    /**
     * Envoie la liste des connectes a TOUS les clients (broadcast).
     *
     * Appele a chaque connexion/deconnexion pour mettre a jour
     * l'interface de tous les clients en temps reel.
     */
    public void broadcastUserList() {
        // Construction du message de liste
        StringBuilder sb = new StringBuilder(ChatProtocol.LIST_RESULT);
        for (String username : connectedClients.keySet()) {
            sb.append(ChatProtocol.SEP_WRITE).append(username).append(":ONLINE");
        }
        String listMessage = sb.toString();

        // Envoi a chaque client connecte
        for (ClientHandler handler : connectedClients.values()) {
            handler.send(listMessage);
        }
    }

    /**
     * Journalise un evenement avec horodatage (RG12).
     *
     * RG12 : le serveur doit journaliser les connexions, deconnexions
     * et envois de messages.
     *
     * @param message le message a logger
     */
    public void log(String message) {
        String timestamp = LocalDateTime.now().format(LOG_FORMAT);
        System.out.println("[" + timestamp + "] " + message);
    }

    /**
     * Point d'entree du serveur.
     * Peut etre lance independamment du client JavaFX.
     */
    public static void main(String[] args) {
        new ChatServer().start();
    }
}