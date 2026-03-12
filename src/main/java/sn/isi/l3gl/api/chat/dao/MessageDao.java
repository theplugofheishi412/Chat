package sn.isi.l3gl.api.chat.dao;

import jakarta.persistence.EntityManager;
import sn.isi.l3gl.api.chat.model.Message;
import sn.isi.l3gl.api.chat.util.HibernateUtil;

import java.util.List;

/**
 * MessageDao : operations de base de donnees pour les messages.
 *
 * Gere la sauvegarde, la recuperation et la mise a jour des messages.
 * Implemente les regles RG6 (messages hors ligne) et RG8 (ordre chronologique).
 */
public class MessageDao {

    /**
     * Sauvegarde un nouveau message en base.
     * Statut initial = ENVOYE (defini dans le constructeur de Message).
     *
     * @param message le message a persister
     */
    public void save(Message message) {
        try (EntityManager em = HibernateUtil.getEntityManager()) {
            em.getTransaction().begin();
            em.persist(message);          // INSERT INTO messages ...
            em.getTransaction().commit();
        }
    }

    /**
     * Met a jour le statut d'un message (ENVOYE -> RECU -> LU).
     *
     * @param message le message avec le nouveau statut
     */
    public void update(Message message) {
        try (EntityManager em = HibernateUtil.getEntityManager()) {
            em.getTransaction().begin();
            em.merge(message);
            em.getTransaction().commit();
        }
    }

    /**
     * Recupere la conversation entre deux utilisateurs, triee par date (RG8).
     *
     * La condition WHERE couvre les deux sens :
     * - messages de userA vers userB
     * - messages de userB vers userA
     *
     * ORDER BY m.dateEnvoi ASC = ordre chronologique (le plus ancien en premier).
     *
     * @param usernameA premier utilisateur
     * @param usernameB second utilisateur
     * @return liste des messages dans l'ordre chronologique
     */
    public List<Message> findConversation(String usernameA, String usernameB) {
        try (EntityManager em = HibernateUtil.getEntityManager()) {
            return em.createQuery(
                            "FROM Message m " +
                                    "WHERE (m.sender.username = :a AND m.receiver.username = :b) " +
                                    "   OR (m.sender.username = :b AND m.receiver.username = :a) " +
                                    "ORDER BY m.dateEnvoi ASC",
                            Message.class)
                    .setParameter("a", usernameA)
                    .setParameter("b", usernameB)
                    .getResultList();
        }
    }

    /**
     * Recupere les messages en attente pour un utilisateur (RG6).
     *
     * Quand un utilisateur se reconnecte, on cherche tous les messages
     * qui lui ont ete envoyes pendant qu'il etait OFFLINE (statut = ENVOYE).
     *
     * @param receiverUsername le nom du destinataire qui vient de se connecter
     * @return liste des messages non encore livres
     */
    public List<Message> findPendingMessages(String receiverUsername) {
        try (EntityManager em = HibernateUtil.getEntityManager()) {
            return em.createQuery(
                            "FROM Message m " +
                                    "WHERE m.receiver.username = :receiver " +
                                    "  AND m.statut = :statut " +
                                    "ORDER BY m.dateEnvoi ASC",
                            Message.class)
                    .setParameter("receiver", receiverUsername)
                    .setParameter("statut", Message.Statut.ENVOYE)
                    .getResultList();
        }
    }

    /**
     * Marque tous les messages en attente d'un utilisateur comme RECU.
     * Appele apres avoir livre les messages hors-ligne (RG6).
     *
     * @param receiverUsername l'utilisateur qui vient de recevoir ses messages
     */
    public void markAsReceived(String receiverUsername) {
        try (EntityManager em = HibernateUtil.getEntityManager()) {
            em.getTransaction().begin();
            em.createQuery(
                            "UPDATE Message m SET m.statut = :recu " +
                                    "WHERE m.receiver.username = :receiver " +
                                    "  AND m.statut = :envoye")
                    .setParameter("recu", Message.Statut.RECU)
                    .setParameter("receiver", receiverUsername)
                    .setParameter("envoye", Message.Statut.ENVOYE)
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }
}