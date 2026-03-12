package sn.isi.l3gl.api.chat.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import sn.isi.l3gl.api.chat.client.ServerConnection;
import sn.isi.l3gl.api.chat.server.ChatServer;
import sn.isi.l3gl.api.chat.util.ChatProtocol;

import java.io.IOException;

/**
 * LoginController : controleur de la fenetre de connexion/inscription.
 *
 * En JavaFX, le pattern MVC separe :
 * - Model      : les donnees (User, Message)
 * - View       : le fichier FXML (description de l'interface)
 * - Controller : cette classe (gestion des evenements, logique d'affichage)
 *
 * Le @FXML lie les elements du fichier FXML aux champs Java.
 */
public class LoginController {

    // ===== Elements lies au FXML via @FXML =====
    // Ces champs sont injectes automatiquement par FXMLLoader
    // Leurs noms doivent correspondre aux fx:id dans le fichier FXML

    @FXML private TextField     usernameField;     // champ nom d'utilisateur
    @FXML private PasswordField passwordField;     // champ mot de passe (masque)
    @FXML private ComboBox<String> roleComboBox;   // liste deroulante des roles
    @FXML private Label         statusLabel;       // label de statut/erreur
    @FXML private Button        loginButton;       // bouton connexion
    @FXML private Button        registerButton;    // bouton inscription
    @FXML private TextField     serverField;       // adresse du serveur (cache dans le FXML)

    /**
     * IP du serveur fixee en dur.
     * Le champ serverField est cache (visible=false dans le FXML) :
     * les membres de l'equipe ne peuvent pas voir ni changer cette valeur.
     * Seul toi (l'admin) modifies cette constante avant de distribuer le JAR.
     */
    private static final String SERVER_IP = "192.168.1.40";

    /** Connexion reseau avec le serveur */
    private ServerConnection connection;

    /**
     * @FXML initialize() : methode appelee automatiquement apres le chargement du FXML.
     * C'est ici qu'on initialise l'etat de l'interface.
     */
    @FXML
    public void initialize() {
        // Remplissage de la liste deroulante des roles
        roleComboBox.getItems().addAll("MEMBRE", "BENEVOLE", "ORGANISATEUR");
        roleComboBox.setValue("MEMBRE"); // valeur par defaut

        // Fixe l'IP en dur dans le champ cache : l'utilisateur ne peut pas la modifier
        serverField.setText(SERVER_IP);

        // Ecoute de l'appui sur Entree dans le champ mot de passe
        passwordField.setOnAction(e -> onLogin());
    }

    /**
     * Methode appelee quand l'utilisateur clique sur "Se connecter".
     *
     * 1. Recupere les valeurs des champs
     * 2. Etablit la connexion reseau
     * 3. Envoie la commande LOGIN
     * 4. La reponse sera traitee dans handleServerMessage()
     */
    @FXML
    private void onLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String server   = serverField.getText().trim();

        // Validation basique cote client
        if (username.isEmpty() || password.isEmpty()) {
            showStatus("Veuillez remplir tous les champs", true);
            return;
        }

        try {
            // Connexion au serveur (si pas deja connecte)
            connectToServer(server);

            // Envoi de la commande LOGIN
            // La methode sendLogin construit : "LOGIN|username|password"
            connection.sendLogin(username, password);

            showStatus("Connexion en cours...", false);
            setButtonsDisabled(true);   // desactive les boutons pendant l'attente

        } catch (IOException e) {
            showStatus("Impossible de joindre le serveur: " + e.getMessage(), true);
        }
    }

    /**
     * Methode appelee quand l'utilisateur clique sur "S'inscrire".
     */
    @FXML
    private void onRegister() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String role     = roleComboBox.getValue();
        String server   = serverField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showStatus("Veuillez remplir tous les champs", true);
            return;
        }

        try {
            connectToServer(server);
            connection.sendRegister(username, password, role);
            showStatus("Inscription en cours...", false);
            setButtonsDisabled(true);

        } catch (IOException e) {
            showStatus("Impossible de joindre le serveur: " + e.getMessage(), true);
        }
    }

    /**
     * Etablit la connexion avec le serveur si elle n'existe pas.
     *
     * @param serverHost adresse du serveur
     * @throws IOException si la connexion echoue
     */
    private void connectToServer(String serverHost) throws IOException {
        // Cree une nouvelle connexion si necessaire
        if (connection == null || !connection.isConnected()) {
            // Le lambda "this::handleServerMessage" est le callback :
            // chaque message recu du serveur sera passe a handleServerMessage()
            connection = new ServerConnection(this::handleServerMessage);
            connection.connect(serverHost, ChatServer.PORT);
        }
    }

    /**
     * Traite les messages recus du serveur.
     *
     * ATTENTION : cette methode est appelee depuis le thread reseau,
     * PAS depuis le thread JavaFX (FX Application Thread).
     * Toute modification de l'UI doit passer par Platform.runLater().
     *
     * Platform.runLater() : execute le code dans le thread JavaFX
     * au prochain cycle de rendu.
     *
     * @param message la ligne recue du serveur
     */
    private void handleServerMessage(String message) {
        // Decoupe selon le separateur de protocole
        String[] parts = message.split(ChatProtocol.SEP, -1);
        String command = parts[0];

        switch (command) {
            case ChatProtocol.LOGIN_OK -> {
                // Connexion reussie : parts[1] = role de l'utilisateur
                String role = parts.length > 1 ? parts[1] : "MEMBRE";

                // Mise a jour de l'UI dans le thread JavaFX
                Platform.runLater(() -> openChatWindow(
                        usernameField.getText().trim(), role));
            }

            case ChatProtocol.LOGIN_FAIL -> {
                // Echec : parts[1] = message d'erreur
                String reason = parts.length > 1 ? parts[1] : "Erreur inconnue";
                Platform.runLater(() -> {
                    showStatus("Echec : " + reason, true);
                    setButtonsDisabled(false);
                });
            }

            case ChatProtocol.REGISTER_OK ->
                    Platform.runLater(() -> {
                        showStatus("Inscription reussie ! Vous pouvez vous connecter.", false);
                        setButtonsDisabled(false);
                    });

            case ChatProtocol.REGISTER_FAIL -> {
                String reason = parts.length > 1 ? parts[1] : "Erreur inconnue";
                Platform.runLater(() -> {
                    showStatus("Echec inscription : " + reason, true);
                    setButtonsDisabled(false);
                });
            }
        }
    }

    /**
     * Ouvre la fenetre principale du chat apres connexion reussie.
     *
     * FXMLLoader : charge un fichier FXML et cree l'arbre de composants JavaFX.
     * Stage : une fenetre JavaFX.
     * Scene : le contenu d'une fenetre (graphe de noeuds).
     *
     * @param username le nom de l'utilisateur connecte
     * @param role     son role (pour adapter l'interface)
     */
    private void openChatWindow(String username, String role) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/sn/isi/l3gl/api/chat/chat-view.fxml"));
            Scene chatScene = new Scene(loader.load(), 900, 600);

            // Recupere le controller de la fenetre chat et l'initialise
            ChatController chatController = loader.getController();
            chatController.initSession(username, role, connection);

            // Recupere la fenetre actuelle (login) et change sa scene
            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setTitle("Chat - " + username + " [" + role + "]");
            stage.setScene(chatScene);
            stage.setOnCloseRequest(e -> connection.disconnect());

        } catch (IOException e) {
            showStatus("Erreur ouverture fenetre chat: " + e.getMessage(), true);
            e.printStackTrace();
        }
    }

    /**
     * Affiche un message de statut sous le formulaire.
     *
     * @param message le texte a afficher
     * @param isError true = rouge (erreur), false = vert (succes)
     */
    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        // setStyle() : applique du CSS en ligne sur le composant
        statusLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: green;");
    }

    /**
     * Active ou desactive les boutons pendant une operation reseau.
     * Evite les doubles clics pendant l'attente d'une reponse.
     *
     * @param disabled true pour desactiver
     */
    private void setButtonsDisabled(boolean disabled) {
        loginButton.setDisable(disabled);
        registerButton.setDisable(disabled);
    }
}