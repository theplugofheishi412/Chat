package sn.isi.l3gl.api.chat.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

/**
 * HibernateUtil : classe utilitaire qui gere le cycle de vie de l'EntityManagerFactory.
 *
 * PATTERN SINGLETON :
 * L'EntityManagerFactory est tres couteuse a creer (connexion, lecture du schema...).
 * On en cree donc une seule instance pour toute l'application,
 * et on la reutilise a chaque fois qu'on a besoin d'un EntityManager.
 *
 * EntityManagerFactory -> usine qui fabrique des EntityManager
 * EntityManager        -> objet qui permet de faire les operations CRUD (Create/Read/Update/Delete)
 */
public class HibernateUtil {

    /**
     * Instance unique de l'usine : "static" = partagee par toute l'application.
     * "final" = ne peut pas etre reassignee apres initialisation.
     * "chatPU" correspond au nom defini dans persistence.xml.
     */
    private static final EntityManagerFactory ENTITY_MANAGER_FACTORY =
            Persistence.createEntityManagerFactory("chatPU");

    /**
     * Cree et retourne un nouvel EntityManager.
     *
     * Un EntityManager est une unite de travail :
     * - on l'ouvre avant une operation
     * - on fait l'operation
     * - on le ferme apres
     *
     * @return un EntityManager pret a l'emploi
     */
    public static EntityManager getEntityManager() {
        return ENTITY_MANAGER_FACTORY.createEntityManager();
    }

    /**
     * Ferme l'EntityManagerFactory proprement a l'arret de l'application.
     * A appeler une seule fois, dans le hook d'arret (shutdown hook).
     */
    public static void close() {
        if (ENTITY_MANAGER_FACTORY != null && ENTITY_MANAGER_FACTORY.isOpen()) {
            ENTITY_MANAGER_FACTORY.close();
        }
    }
}