package sn.isi.l3gl.api.chat.util;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.HashMap;
import java.util.Map;

/**
 * HibernateUtil : classe utilitaire qui gere le cycle de vie de l'EntityManagerFactory.
 *
 * PATTERN SINGLETON :
 * L'EntityManagerFactory est tres couteuse a creer (connexion, lecture du schema...).
 * On en cree donc une seule instance pour toute l'application,
 * et on la reutilise a chaque fois qu'on a besoin d'un EntityManager.
 *
 * Les credentials sont lus depuis :
 * - Le fichier .env en local (development)
 * - Les variables d'environnement systeme sur Render (production)
 */
public class HibernateUtil {

    private static final EntityManagerFactory ENTITY_MANAGER_FACTORY =
            buildEntityManagerFactory();

    private static EntityManagerFactory buildEntityManagerFactory() {

        // Dotenv charge le .env si present (local)
        // ignoreIfMissing() : pas d'erreur si absent (ex: sur Render)
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        // Lecture des variables : variables systeme en priorite, puis .env
        String dbUrl      = getEnv(dotenv, "DB_URL");
        String dbUser     = getEnv(dotenv, "DB_USER");
        String dbPassword = getEnv(dotenv, "DB_PASSWORD");

        // Injection des credentials dans Hibernate
        Map<String, Object> props = new HashMap<>();
        props.put("jakarta.persistence.jdbc.url",      dbUrl);
        props.put("jakarta.persistence.jdbc.user",     dbUser);
        props.put("jakarta.persistence.jdbc.password", dbPassword);

        // "chatPU" correspond au nom defini dans persistence.xml
        return Persistence.createEntityManagerFactory("chatPU", props);
    }

    /**
     * Lit une variable d'environnement :
     * 1. Variable systeme (Render en production)
     * 2. Fichier .env (local en developpement)
     *
     * @param dotenv instance Dotenv
     * @param key    nom de la variable
     * @return valeur de la variable
     * @throws IllegalStateException si la variable est absente partout
     */
    private static String getEnv(Dotenv dotenv, String key) {
        String value = System.getenv(key); // Render injecte ici
        if (value == null) {
            value = dotenv.get(key);       // .env en local
        }
        if (value == null) {
            throw new IllegalStateException(
                    "Variable d'environnement manquante : " + key +
                            "\nVerifie ton fichier .env ou les variables Render.");
        }
        return value;
    }

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