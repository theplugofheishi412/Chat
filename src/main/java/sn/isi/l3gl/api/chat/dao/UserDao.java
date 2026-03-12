package sn.isi.l3gl.api.chat.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import sn.isi.l3gl.api.chat.model.User;
import sn.isi.l3gl.api.chat.util.HibernateUtil;

import java.util.List;

/**
 * UserDao (Data Access Object) : contient toutes les operations
 * de base de donnees liees aux utilisateurs.
 *
 * Le pattern DAO isole la logique d'acces aux donnees du reste de l'application.
 * Le reste du code n'a pas a savoir comment les donnees sont stockees.
 */
public class UserDao {

    /**
     * Enregistre un nouvel utilisateur en base (inscription).
     *
     * Sequence :
     * 1. Ouvrir un EntityManager
     * 2. Demarrer une transaction (pour garantir l'atomicite)
     * 3. Persister l'objet (INSERT en SQL)
     * 4. Valider la transaction (commit)
     * 5. Fermer l'EntityManager
     *
     * @param user l'utilisateur a sauvegarder
     */
    public void save(User user) {
        // try-with-resources : ferme l'EntityManager automatiquement meme en cas d'erreur
        try (EntityManager em = HibernateUtil.getEntityManager()) {
            em.getTransaction().begin();
            em.persist(user);             // INSERT INTO users ...
            em.getTransaction().commit();
        }
    }

    /**
     * Met a jour un utilisateur existant (ex: changer le statut ONLINE/OFFLINE).
     *
     * em.merge() : si l'objet existe deja en base (meme id), fait un UPDATE.
     *
     * @param user l'utilisateur avec les nouvelles valeurs
     */
    public void update(User user) {
        try (EntityManager em = HibernateUtil.getEntityManager()) {
            em.getTransaction().begin();
            em.merge(user);               // UPDATE users SET ... WHERE id = ?
            em.getTransaction().commit();
        }
    }

    /**
     * Recherche un utilisateur par son nom d'utilisateur.
     *
     * JPQL (Java Persistence Query Language) : comme SQL mais oriente objet.
     * "FROM User u WHERE u.username = :username" au lieu de
     * "SELECT * FROM users WHERE username = ?"
     *
     * @param username le nom a chercher
     * @return l'utilisateur trouve, ou null si inexistant
     */
    public User findByUsername(String username) {
        try (EntityManager em = HibernateUtil.getEntityManager()) {
            // TypedQuery<User> : requete qui retourne des objets User (type-safe)
            TypedQuery<User> query = em.createQuery(
                    "FROM User u WHERE u.username = :username", User.class);
            query.setParameter("username", username);

            // getSingleResult() lance une exception si aucun resultat
            return query.getSingleResult();
        } catch (NoResultException e) {
            // Aucun utilisateur avec ce username : on retourne null
            return null;
        }
    }

    /**
     * Recupere tous les utilisateurs (utile pour ORGANISATEUR - RG13).
     *
     * @return liste de tous les membres inscrits
     */
    public List<User> findAll() {
        try (EntityManager em = HibernateUtil.getEntityManager()) {
            return em.createQuery("FROM User u ORDER BY u.username", User.class)
                    .getResultList();
        }
    }

    /**
     * Verifie si un username est deja pris en base (RG1).
     *
     * @param username le nom a verifier
     * @return true si le username existe deja
     */
    public boolean existsByUsername(String username) {
        return findByUsername(username) != null;
    }

    /**
     * Met a jour uniquement le statut d'un utilisateur.
     * Utilise directement une requete JPQL UPDATE pour etre plus efficace
     * (pas besoin de charger tout l'objet User).
     *
     * @param username le nom de l'utilisateur
     * @param status   le nouveau statut (ONLINE ou OFFLINE)
     */
    public void updateStatus(String username, User.Status status) {
        try (EntityManager em = HibernateUtil.getEntityManager()) {
            em.getTransaction().begin();
            em.createQuery(
                            "UPDATE User u SET u.status = :status WHERE u.username = :username")
                    .setParameter("status", status)
                    .setParameter("username", username)
                    .executeUpdate();           // UPDATE users SET status = ? WHERE username = ?
            em.getTransaction().commit();
        }
    }
}