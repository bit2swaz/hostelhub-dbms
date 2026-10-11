package com.hostelhub.service;

import com.hostelhub.db.ConnectionFactory;
import com.hostelhub.util.OracleErrorMapper;
import java.sql.Connection;
import java.sql.SQLException;

/** Runs work in one transaction: commit on success, roll back on any failure, always close. */
final class Tx {

    @FunctionalInterface
    interface Work<T> {
        T run(Connection c) throws SQLException;
    }

    private Tx() {
    }

    static <T> T run(ConnectionFactory db, Work<T> work) {
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new ServiceException(OracleErrorMapper.toMessage(e), e);
        }
    }
}
