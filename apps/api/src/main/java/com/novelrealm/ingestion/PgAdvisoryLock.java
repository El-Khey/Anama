package com.novelrealm.ingestion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verrou consultatif PostgreSQL ({@code pg_try_advisory_lock}) : empêche deux
 * exécutions concurrentes de l'ingestion — que ce soit sur plusieurs replicas
 * (le cron déclenche partout en même temps) ou entre le cron et un
 * déclenchement manuel via l'endpoint admin.
 *
 * <p><b>Best-effort, pas une file d'attente.</b> Si le verrou est déjà tenu,
 * l'action est simplement SAUTÉE (elle retentera au prochain cycle / au prochain
 * appel) — on ne bloque personne.
 *
 * <p>Le verrou est lié à la SESSION : acquisition, travail et libération doivent
 * partager une seule connexion. On prend donc une connexion dédiée le temps de
 * l'action, hors du pool JPA, et on la referme dans un {@code finally}. Léger,
 * sans dépendance supplémentaire (ShedLock/Quartz inutiles ici).
 */
@Component
public class PgAdvisoryLock {

    private static final Logger log = LoggerFactory.getLogger(PgAdvisoryLock.class);

    /** Clé du verrou d'ingestion. Constante arbitraire mais stable (≠ 0). */
    public static final long INGESTION_SYNC_KEY = 0x4E4F56454C53594EL; // "NOVELSYN"

    private final DataSource dataSource;

    public PgAdvisoryLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Exécute {@code action} seulement si le verrou {@code key} est libre.
     *
     * @return {@code true} si le verrou a été pris et l'action lancée ;
     *         {@code false} si le verrou était déjà tenu (action sautée).
     */
    public boolean runIfFree(long key, Runnable action) {
        try (Connection conn = dataSource.getConnection()) {
            if (!tryLock(conn, key)) {
                log.info("Ingestion déjà en cours ailleurs (verrou {} tenu) — exécution sautée.", key);
                return false;
            }
            try {
                action.run();
                return true;
            } finally {
                unlock(conn, key);
            }
        } catch (SQLException ex) {
            // Impossible d'obtenir/relâcher le verrou : on n'exécute PAS (prudence),
            // mais on ne fait pas tomber l'appelant — le prochain cycle réessaiera.
            log.error("Verrou d'ingestion indisponible ({}) — exécution sautée.", ex.getMessage(), ex);
            return false;
        }
    }

    private static boolean tryLock(Connection conn, long key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            ps.setLong(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static void unlock(Connection conn, long key) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            ps.setLong(1, key);
            ps.execute();
        } catch (SQLException ex) {
            // La session se ferme juste après (try-with-resources) : Postgres
            // libère alors automatiquement les verrous de session. On loggue et
            // on n'escalade pas.
            log.warn("Libération du verrou {} impossible ({}) — libéré à la fermeture de session.",
                    key, ex.getMessage());
        }
    }
}
