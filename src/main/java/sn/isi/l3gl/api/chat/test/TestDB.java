package sn.isi.l3gl.api.chat.test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public class TestDB {

    public static void main(String[] args) {

        EntityManagerFactory emf =
                Persistence.createEntityManagerFactory("chatPU");

        EntityManager em = emf.createEntityManager();

        try {
            em.getTransaction().begin();

            System.out.println("🔥 Connexion Render OK !");

            em.getTransaction().commit();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            em.close();
            emf.close();
        }
    }
}