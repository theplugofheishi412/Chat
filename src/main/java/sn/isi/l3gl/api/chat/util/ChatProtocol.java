package sn.isi.l3gl.api.chat.util;

/**
 * ChatProtocol : definit le protocole de communication entre le client et le serveur.
 *
 * PRINCIPE DU PROTOCOLE TEXTE :
 * Les messages transitent via des Sockets sous forme de Strings.
 * Chaque message suit le format : COMMANDE|param1|param2|...
 * Le serveur et le client se comprennent grace a ces constantes partagees.
 *
 * Exemple :
 * Client -> Serveur : "LOGIN|alice|motdepasse"
 * Serveur -> Client : "LOGIN_OK|MEMBRE" ou "LOGIN_FAIL|Username incorrect"
 */
public class ChatProtocol {

    // ===== COMMANDES ENVOYEES PAR LE CLIENT =====

    /** Inscription : LOGIN|username|password_hache|ROLE */
    public static final String REGISTER   = "REGISTER";

    /** Connexion : LOGIN|username|password */
    public static final String LOGIN      = "LOGIN";

    /** Deconnexion : LOGOUT (pas de parametre) */
    public static final String LOGOUT     = "LOGOUT";

    /**
     * Envoi d'un message : MSG|destinataire|contenu
     * RG5 : verifie que l'expediteur est connecte et le destinataire existe
     */
    public static final String MSG        = "MSG";

    /** Demande de la liste des membres connectes */
    public static final String LIST_USERS = "LIST_USERS";

    /** Demande de l'historique : HISTORY|username_interlocuteur */
    public static final String HISTORY    = "HISTORY";

    /** Demande liste complete des membres (ORGANISATEUR uniquement - RG13) */
    public static final String ALL_USERS  = "ALL_USERS";

    // ===== REPONSES ENVOYEES PAR LE SERVEUR =====

    /** Inscription reussie */
    public static final String REGISTER_OK   = "REGISTER_OK";

    /** Echec inscription : REGISTER_FAIL|raison */
    public static final String REGISTER_FAIL = "REGISTER_FAIL";

    /** Connexion reussie : LOGIN_OK|ROLE */
    public static final String LOGIN_OK      = "LOGIN_OK";

    /** Echec connexion : LOGIN_FAIL|raison */
    public static final String LOGIN_FAIL    = "LOGIN_FAIL";

    /** Notification de deconnexion confirmee */
    public static final String LOGOUT_OK     = "LOGOUT_OK";

    /**
     * Message recu (push du serveur vers le client destinataire) :
     * INCOMING|expediteur|contenu|heure
     */
    public static final String INCOMING      = "INCOMING";

    /** Confirmation d'envoi : SENT_OK */
    public static final String SENT_OK       = "SENT_OK";

    /** Echec d'envoi : SENT_FAIL|raison */
    public static final String SENT_FAIL     = "SENT_FAIL";

    /**
     * Liste des utilisateurs : LIST_RESULT|user1:STATUS|user2:STATUS|...
     * Envoyee en reponse a LIST_USERS ou a chaque connexion/deconnexion (broadcast).
     */
    public static final String LIST_RESULT   = "LIST_RESULT";

    /**
     * Un message de l'historique : HIST_MSG|expediteur|contenu|heure
     * Le serveur envoie autant de HIST_MSG que de messages dans la conversation.
     */
    public static final String HIST_MSG      = "HIST_MSG";

    /** Fin de l'historique : HIST_END (signale que tous les messages ont ete envoyes) */
    public static final String HIST_END      = "HIST_END";

    /**
     * Separateur entre les parties d'un message de protocole.
     * On utilise | car il n'apparait pas dans les noms d'utilisateurs.
     */
    public static final String SEP = "\\|";     // regex pour split()
    public static final String SEP_WRITE = "|"; // pour ecrire dans le flux

    // Empeche l'instanciation de cette classe utilitaire
    private ChatProtocol() {}
}