package sn.isi.l3gl.api.chat.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entite User : represente un membre de l'association.
 *
 * L'annotation @Entity indique a Hibernate que cette classe
 * correspond a une table en base de donnees.
 * La table s'appellera "users" grace a @Table.
 */
@Entity
@Table(name = "users")
public class User {

    /**
     * Enumeration des roles possibles (RG13 : ORGANISATEUR a des droits supplementaires).
     * Stocke sous forme de String en base (ORGANISATEUR, MEMBRE, BENEVOLE).
     */
    public enum Role {
        ORGANISATEUR, MEMBRE, BENEVOLE
    }

    /**
     * Enumeration du statut de connexion.
     * ONLINE : connecte, OFFLINE : deconnecte (RG4).
     */
    public enum Status {
        ONLINE, OFFLINE
    }

    /**
     * @Id : cle primaire de la table.
     * @GeneratedValue : auto-incrementation par la base.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * @Column(unique = true) : impose l'unicite du username en base (RG1).
     * nullable = false : le champ est obligatoire.
     */
    @Column(unique = true, nullable = false, length = 50)
    private String username;

    /**
     * Mot de passe hache avec BCrypt (RG9).
     * On ne stocke JAMAIS le mot de passe en clair.
     */
    @Column(nullable = false)
    private String password;

    /**
     * @Enumerated(EnumType.STRING) : Hibernate stocke "ORGANISATEUR" etc.
     * au lieu d'un indice numerique (plus lisible en base).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * Statut en temps reel : mis a jour a chaque connexion/deconnexion (RG4).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OFFLINE;

    /**
     * Date d'inscription : initialisee automatiquement a la creation.
     */
    @Column(nullable = false)
    private LocalDateTime dateCreation;

    /**
     * Constructeur vide requis par JPA/Hibernate pour instancier l'entite.
     */
    public User() {
        this.dateCreation = LocalDateTime.now();
        this.status = Status.OFFLINE;
    }

    /**
     * Constructeur pratique pour creer un nouvel utilisateur.
     *
     * @param username nom d'utilisateur unique
     * @param password mot de passe DEJA hache
     * @param role     role dans l'association
     */
    public User(String username, String password, Role role) {
        this();
        this.username = username;
        this.password = password;
        this.role = role;
    }

    // --- Getters et Setters ---
    // Requis par Hibernate pour lire/ecrire les champs de l'entite.

    public Long getId() { return id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDateTime getDateCreation() { return dateCreation; }

    /**
     * Representation textuelle utilisee dans les ListView JavaFX.
     * Affiche le nom et le statut entre crochets.
     */
    @Override
    public String toString() {
        return username + " [" + status + "]";
    }
}