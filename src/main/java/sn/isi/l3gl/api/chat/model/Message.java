package sn.isi.l3gl.api.chat.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entite Message : represente un message echange entre deux utilisateurs.
 *
 * Chaque message a un expediteur (sender), un destinataire (receiver),
 * un contenu texte, une date d'envoi et un statut de livraison.
 */
@Entity
@Table(name = "messages")
public class Message {

    /**
     * Statut du message dans son cycle de vie :
     * ENVOYE  -> le serveur l'a recu
     * RECU    -> le destinataire s'est connecte et l'a recu
     * LU      -> le destinataire l'a ouvert/lu
     */
    public enum Statut {
        ENVOYE, RECU, LU
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * @ManyToOne : plusieurs messages peuvent avoir le meme expediteur.
     * @JoinColumn : cle etrangere "sender_id" dans la table messages.
     * fetch = EAGER : charge l'expediteur immediatement (utile pour l'affichage).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    /**
     * Meme principe pour le destinataire.
     * nullable = false : un message doit toujours avoir un destinataire.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    /**
     * Contenu du message : 1000 caracteres maximum (RG7).
     * @Column(length = 1000) impose cette limite en base.
     */
    @Column(nullable = false, length = 1000)
    private String contenu;

    /**
     * Horodatage de l'envoi : initialise automatiquement.
     */
    @Column(nullable = false)
    private LocalDateTime dateEnvoi;

    /**
     * Statut initial : ENVOYE des la creation du message.
     * Passe a RECU quand le destinataire se connecte (RG6).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.ENVOYE;

    /**
     * Constructeur vide requis par JPA.
     */
    public Message() {
        this.dateEnvoi = LocalDateTime.now();
        this.statut = Statut.ENVOYE;
    }

    /**
     * Constructeur principal pour creer un message.
     *
     * @param sender   l'utilisateur qui envoie
     * @param receiver l'utilisateur qui recoit
     * @param contenu  le texte du message (max 1000 caracteres)
     */
    public Message(User sender, User receiver, String contenu) {
        this();
        this.sender = sender;
        this.receiver = receiver;
        this.contenu = contenu;
    }

    // --- Getters et Setters ---

    public Long getId() { return id; }

    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }

    public User getReceiver() { return receiver; }
    public void setReceiver(User receiver) { this.receiver = receiver; }

    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }

    public LocalDateTime getDateEnvoi() { return dateEnvoi; }

    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }

    /**
     * Format d'affichage dans l'interface :
     * "[HH:MM] expediteur : contenu"
     */
    @Override
    public String toString() {
        String heure = dateEnvoi.getHour() + ":" +
                String.format("%02d", dateEnvoi.getMinute());
        return "[" + heure + "] " + sender.getUsername() + " : " + contenu;
    }
}