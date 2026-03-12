package sn.isi.l3gl.api.chat.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import sn.isi.l3gl.api.chat.client.ServerConnection;
import sn.isi.l3gl.api.chat.util.ChatProtocol;

/**
 * ChatController : controleur de la fenetre principale du chat.
 *
 * Cette fenetre se divise en trois zones :
 * - Panneau gauche  : liste des membres connectes (ListView)
 * - Panneau central : affichage des messages de la conversation
 * - Panneau bas     : saisie et envoi des messages
 *
 * Le controller ecoute les messages du serveur et met a jour l'UI.
 */
public class ChatController {

    // ===== Composants de l'interface lies au FXML =====

    @FXML private ListView<String> userListView;    // liste des membres connectes
    @FXML private TextArea         chatArea;        // zone d'affichage des messages
    @FXML private TextField        messageField;    // champ de saisie du message
    @FXML private Button           sendButton;      // bouton envoyer
    @FXML private Label            currentUserLabel;// affiche "Connecte : username"
    @FXML private Label            conversationLabel;// "Conversation avec : ..."
    @FXML private Button           refreshButton;  // actualiser la liste
    @FXML private Button           allUsersButton; // bouton ORGANISATEUR uniquement

    /** Connexion reseau partagee avec LoginController */
    private ServerConnection connection;

    /** Nom de l'utilisateur connecte */
    private String currentUsername;

    /** Role de l'utilisateur (MEMBRE, BENEVOLE, ORGANISATEUR) */
    private String currentRole;

    /** L'interlocuteur selectionne dans la liste (null si aucun) */
    private String selectedUser;

    /**
     * initialize() : appele apres chargement du FXML.
     * On y configure les evenements des composants.
     */
    @FXML
    public void initialize() {
        // Ecoute de la selection dans la liste des utilisateurs
        // ChangeListener : reagit quand la valeur selectionnee change
        userListView.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        // Extrait le username (format: "username:ONLINE")
                        selectedUser = newValue.split(":")[0];
                        conversationLabel.setText("Conversation avec : " + selectedUser);
                        chatArea.clear();

                        // Charge l'historique de la conversation (RG8)
                        connection.requestHistory(selectedUser);

                        // Active le champ de saisie maintenant qu'un utilisateur est selectionne
                        enableMessageInput(true);
                    }
                });

        // Envoi du message avec la touche Entree
        messageField.setOnAction(e -> onSendMessage());

        // Desactive la saisie tant qu'aucun utilisateur n'est selectionne
        messageField.setDisable(true);
        sendButton.setDisable(true);
    }

    /**
     * Initialise la session utilisateur apres connexion reussie.
     * Appelee par LoginController apres un LOGIN_OK.
     *
     * @param username   nom de l'utilisateur connecte
     * @param role       son role dans l'association
     * @param connection la connexion reseau deja etablie
     */
    public void initSession(String username, String role, ServerConnection connection) {
        this.currentUsername = username;
        this.currentRole     = role;
        this.connection      = connection;

        // Mise a jour du label de bienvenue
        currentUserLabel.setText("Connecte : " + username + " [" + role + "]");

        // Le bouton "Tous les membres" n'est visible que pour les ORGANISATEURS (RG13)
        allUsersButton.setVisible("ORGANISATEUR".equals(role));

        // Redirige les messages serveur vers ce controller
        // On remplace le handler du LoginController par celui du ChatController
        connection.setMessageHandler(this::handleServerMessage);

        // Demande initiale de la liste des connectes
        connection.requestUserList();
    }

    /**
     * Traite les messages recus du serveur dans le contexte du chat.
     *
     * Rappel : cette methode est appelee depuis le thread reseau,
     * donc toutes les modifs UI passent par Platform.runLater().
     *
     * @param message la ligne recue du serveur
     */
    private void handleServerMessage(String message) {
        String[] parts = message.split(ChatProtocol.SEP, -1);
        String command = parts[0];

        switch (command) {

            case ChatProtocol.LIST_RESULT ->
                // Mise a jour de la liste des utilisateurs connectes
                    Platform.runLater(() -> updateUserList(parts));

            case ChatProtocol.INCOMING ->
                // Message recu d'un autre utilisateur (push du serveur)
                // parts : [INCOMING, expediteur, contenu, heure]
                    Platform.runLater(() -> handleIncomingMessage(parts));

            case ChatProtocol.HIST_MSG ->
                // Un message de l'historique de conversation
                // parts : [HIST_MSG, expediteur, contenu, heure]
                    Platform.runLater(() -> appendMessage(parts[1], parts[2], parts[3]));

            case ChatProtocol.HIST_END ->
                // L'historique est complet : rien de special a faire
                // (on pourrait afficher une ligne separatrice)
                    Platform.runLater(() ->
                            chatArea.appendText("---- historique ----\n"));

            case ChatProtocol.SENT_OK ->
            // Message envoye avec succes : rien a faire (deja affiche localement)
            {}

            case ChatProtocol.SENT_FAIL -> {
                // Echec d'envoi : afficher l'erreur
                String reason = parts.length > 1 ? parts[1] : "Erreur";
                Platform.runLater(() ->
                        chatArea.appendText("[ERREUR] Envoi echoue : " + reason + "\n"));
            }

            case "DISCONNECTED" ->
                // RG10 : perte de connexion au serveur
                    Platform.runLater(this::handleDisconnection);
        }
    }

    /**
     * Met a jour la liste des utilisateurs connectes dans le ListView.
     *
     * @param parts le tableau de parties du message LIST_RESULT
     *              Format : ["LIST_RESULT", "user1:ONLINE", "user2:ONLINE", ...]
     */
    private void updateUserList(String[] parts) {
        // Sauvegarde de la selection actuelle pour la restaurer apres mise a jour
        String previousSelection = userListView.getSelectionModel().getSelectedItem();

        // Efface et reremplit la liste
        userListView.getItems().clear();

        // parts[0] = "LIST_RESULT", parts[1..n] = les utilisateurs
        for (int i = 1; i < parts.length; i++) {
            String entry = parts[i]; // format "username:STATUS"
            // On n'affiche pas l'utilisateur courant dans sa propre liste
            if (!entry.startsWith(currentUsername + ":")) {
                userListView.getItems().add(entry);
            }
        }

        // Restaure la selection si l'utilisateur est encore connecte
        if (previousSelection != null) {
            userListView.getSelectionModel().select(previousSelection);
        }
    }

    /**
     * Affiche un message recu en push du serveur (INCOMING).
     * Si c'est de l'interlocuteur selectionne, affiche dans la chatArea.
     * Sinon, signale une notification (titre de la fenetre par exemple).
     *
     * @param parts [INCOMING, expediteur, contenu, heure]
     */
    private void handleIncomingMessage(String[] parts) {
        if (parts.length < 4) return;

        String sender  = parts[1];
        String content = parts[2];
        String heure   = parts[3];

        if (sender.equals(selectedUser)) {
            // Message de l'interlocuteur actuel : afficher directement
            appendMessage(sender, content, heure);
        } else {
            // Message d'un autre utilisateur : notification
            // (dans une vraie app, on afficherait une badge ou notification)
            chatArea.appendText("[Nouveau message de " + sender + "]\n");
        }
    }

    /**
     * Ajoute une ligne de message dans la zone de chat.
     * Format : "[HH:MM] expediteur : contenu"
     *
     * @param sender  nom de l'expediteur
     * @param content texte du message
     * @param heure   horodatage
     */
    private void appendMessage(String sender, String content, String heure) {
        // Met en evidence les messages envoyes par l'utilisateur courant
        String prefix = sender.equals(currentUsername) ? "Moi" : sender;
        chatArea.appendText("[" + heure + "] " + prefix + " : " + content + "\n");
        // Defilement automatique vers le bas
        chatArea.setScrollTop(Double.MAX_VALUE);
    }

    /**
     * Methode appelee quand l'utilisateur clique sur "Envoyer" ou appuie sur Entree.
     *
     * Verifie les conditions (RG5, RG7) avant d'envoyer.
     */
    @FXML
    private void onSendMessage() {
        // Un interlocuteur doit etre selectionne
        if (selectedUser == null) {
            chatArea.appendText("[INFO] Selectionnez un utilisateur d'abord.\n");
            return;
        }

        String content = messageField.getText().trim();

        // RG7 : contenu non vide
        if (content.isEmpty()) return;

        // RG7 : max 1000 caracteres (verification cote client)
        if (content.length() > 1000) {
            chatArea.appendText("[ERREUR] Message trop long (max 1000 caracteres).\n");
            return;
        }

        // Envoi au serveur
        connection.sendMessage(selectedUser, content);

        // Affichage local immediat (optimistic update)
        // On n'attend pas la confirmation du serveur pour fluidifier l'UX
        String heure = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        appendMessage(currentUsername, content, heure);

        // Efface le champ de saisie
        messageField.clear();
    }

    /**
     * Actualise la liste des utilisateurs connectes.
     * Methode liee au bouton "Actualiser".
     */
    @FXML
    private void onRefreshUsers() {
        connection.requestUserList();
    }

    /**
     * Affiche tous les membres inscrits (RG13 : ORGANISATEUR uniquement).
     * Methode liee au bouton "Tous les membres".
     */
    @FXML
    private void onAllUsers() {
        connection.requestAllUsers();
        chatArea.appendText("---- Liste complete des membres ----\n");
    }

    /**
     * Gere la perte de connexion au serveur (RG10).
     * Affiche une alerte et desactive l'interface.
     */
    private void handleDisconnection() {
        // Desactivation de l'interface
        messageField.setDisable(true);
        sendButton.setDisable(true);

        // Alerte utilisateur
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Connexion perdue");
        alert.setHeaderText("Deconnecte du serveur");
        alert.setContentText("La connexion au serveur a ete perdue.\n" +
                "Veuillez relancer l'application.");
        alert.showAndWait();
    }

    /**
     * Active le champ de saisie quand un utilisateur est selectionne.
     * La selection dans la liste active la conversation.
     */
    private void enableMessageInput(boolean enabled) {
        messageField.setDisable(!enabled);
        sendButton.setDisable(!enabled);
        if (enabled) messageField.requestFocus();
    }
}