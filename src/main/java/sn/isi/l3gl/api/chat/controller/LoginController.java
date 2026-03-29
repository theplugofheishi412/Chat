package sn.isi.l3gl.api.chat.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import sn.isi.l3gl.api.chat.client.ServerConnection;
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

    @FXML private TextField        usernameField;
    @FXML private PasswordField    passwordField;
    @FXML private ComboBox<String> roleComboBox;
    @FXML private Label            statusLabel;
    @FXML private Button           loginButton;
    @FXML private Button           registerButton;
    @FXML private TextField        serverField;

    /**
     * Adresse du serveur.
     *  l'URL Render une fois le serveur deploye.
     * Exemple : "chat-server-xxxx.onrender.com"
     */
    private static final String SERVER_IP = "ton-serveur.onrender.com";

    /**
     * Port WebSocket :
     * - 443 en production (Render gere le SSL)
     * - 8080 en local
     */
    private static final int SERVER_PORT = 443;

    private ServerConnection connection;

    @FXML
    public void initialize() {
        roleComboBox.getItems().addAll("MEMBRE", "BENEVOLE", "ORGANISATEUR");
        roleComboBox.setValue("MEMBRE");
        serverField.setText(SERVER_IP);
        passwordField.setOnAction(e -> onLogin());
    }

    @FXML
    private void onLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String server   = serverField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showStatus("Veuillez remplir tous les champs", true);
            return;
        }

        try {
            connectToServer(server);
            connection.sendLogin(username, password);
            showStatus("Connexion en cours...", false);
            setButtonsDisabled(true);

        } catch (Exception e) {
            showStatus("Impossible de joindre le serveur: " + e.getMessage(), true);
        }
    }

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

        } catch (Exception e) {
            showStatus("Impossible de joindre le serveur: " + e.getMessage(), true);
        }
    }

    /**
     * Etablit la connexion WebSocket avec le serveur.
     *
     * @param serverHost adresse du serveur
     * @throws Exception si la connexion echoue
     */
    private void connectToServer(String serverHost) throws Exception {
        if (connection == null || !connection.isConnected()) {
            connection = new ServerConnection(this::handleServerMessage);
            connection.connect(serverHost, SERVER_PORT);
        }
    }

    /**
     * Traite les messages recus du serveur.
     *
     * ATTENTION : appelee depuis le thread WebSocket, pas le thread JavaFX.
     * Toute modification de l'UI doit passer par Platform.runLater().
     */
    private void handleServerMessage(String message) {
        String[] parts = message.split(ChatProtocol.SEP, -1);
        String command = parts[0];

        switch (command) {
            case ChatProtocol.LOGIN_OK -> {
                String role = parts.length > 1 ? parts[1] : "MEMBRE";
                Platform.runLater(() -> openChatWindow(
                        usernameField.getText().trim(), role));
            }

            case ChatProtocol.LOGIN_FAIL -> {
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

    private void openChatWindow(String username, String role) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/sn/isi/l3gl/api/chat/chat-view.fxml"));
            Scene chatScene = new Scene(loader.load(), 900, 600);

            ChatController chatController = loader.getController();
            chatController.initSession(username, role, connection);

            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setTitle("Chat - " + username + " [" + role + "]");
            stage.setScene(chatScene);
            stage.setOnCloseRequest(e -> connection.disconnect());

        } catch (IOException e) {
            showStatus("Erreur ouverture fenetre chat: " + e.getMessage(), true);
            e.printStackTrace();
        }
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: green;");
    }

    private void setButtonsDisabled(boolean disabled) {
        loginButton.setDisable(disabled);
        registerButton.setDisable(disabled);
    }
}