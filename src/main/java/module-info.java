/**
 * module-info.java : descripteur du module Java (JPMS - Java Platform Module System).
 *
 * COMMENT CA MARCHE :
 * Le JPMS (introduit en Java 9) divise la JVM en modules.
 * Chaque module declare ce dont il a besoin (requires) et ce qu'il expose (exports/opens).
 *
 * POURQUOI L'ERREUR "Module jakarta.persistence not found" ?
 * Parce que le vrai nom du module n'est pas "jakarta.persistence" tout seul.
 * Chaque JAR a un nom de module qui peut etre different de son artifactId Maven.
 * Il faut utiliser le NOM EXACT declare dans le JAR.
 *
 * NOMS EXACTS utilises ici (verifies depuis les JARs) :
 *   jakarta.persistence-api  -> jakarta.persistence
 *   hibernate-core           -> org.hibernate.orm.core
 *   jbcrypt                  -> jbcrypt
 *   postgresql               -> org.postgresql.jdbc
 */
module sn.isi.l3gl.api.chat {

    // ===== MODULES JAVAFX =====
    // javafx.controls : tous les composants UI (Button, Label, TextField, ListView...)
    requires javafx.controls;

    // javafx.fxml : FXMLLoader, annotation @FXML
    requires javafx.fxml;

    // ===== MODULES JPA / HIBERNATE =====
    // jakarta.persistence : annotations @Entity, @Id, @Column, EntityManager, etc.
    // Nom du module dans le JAR jakarta.persistence-api-3.1.0.jar
    requires jakarta.persistence;

    // org.hibernate.orm.core : implementation Hibernate de JPA
    // Nom du module dans le JAR hibernate-core-6.4.4.Final.jar
    requires org.hibernate.orm.core;

    // ===== MODULES UTILITAIRES =====
    // jbcrypt : hachage BCrypt des mots de passe (RG9)
    // Nom derive du JAR jbcrypt-0.4.jar (pas de module-info.class -> automatic module)
    requires jbcrypt;

    // org.postgresql.jdbc : driver JDBC PostgreSQL
    // Automatic-Module-Name declare dans le MANIFEST du JAR postgresql-42.7.3.jar
    requires org.postgresql.jdbc;

    // ===== MODULES JAVA STANDARD =====
    // java.sql : interfaces JDBC (Connection, DriverManager, etc.)
    // Utilise par Hibernate et le driver PostgreSQL
    requires java.sql;

    // java.naming : JNDI, utilise par Hibernate pour la configuration
    requires java.naming;

    // java.xml.bind (JAXB) : utilise par certaines parties de Hibernate pour la serialisation
    requires java.xml;

    // ===== OUVERTURE PAR REFLEXION =====
    /**
     * "opens X to Y" : autorise Y a acceder au package X par reflexion.
     *
     * Reflexion = capacite a inspecter/modifier des objets a l'execution.
     * Sans "opens", le module JPMS bloque la reflexion par securite.
     *
     * JavaFX en a besoin pour :
     * - Lire les fichiers FXML et instancier les controllers
     * - Injecter les champs @FXML dans les controllers
     * - Appeler les methodes d'evenements (@FXML onAction)
     *
     * Hibernate en a besoin pour :
     * - Lire les annotations @Entity, @Id, @Column sur les classes
     * - Acceder aux champs prives pour lire/ecrire les valeurs
     * - Creer des instances des entites par reflexion
     */

    // Package principal : ouvert a JavaFX pour le Launcher et ChatApplication
    opens sn.isi.l3gl.api.chat to javafx.fxml;

    // Controllers : ouverts a JavaFX pour l'injection @FXML
    opens sn.isi.l3gl.api.chat.controller to javafx.fxml;

    // Entites JPA : ouvertes a Hibernate pour la reflexion ORM
    // javafx.base est aussi inclus pour les proprietes observables si besoin
    opens sn.isi.l3gl.api.chat.model to org.hibernate.orm.core, javafx.base;

    // ===== EXPORTS =====
    /**
     * "exports X" : rend le package X visible aux autres modules.
     * Necessaire pour que ChatServer puisse etre lance comme main class separee.
     */
    exports sn.isi.l3gl.api.chat;
    exports sn.isi.l3gl.api.chat.server;
    exports sn.isi.l3gl.api.chat.model;
    exports sn.isi.l3gl.api.chat.util;
    exports sn.isi.l3gl.api.chat.client;
    exports sn.isi.l3gl.api.chat.controller;
    exports sn.isi.l3gl.api.chat.dao;
}