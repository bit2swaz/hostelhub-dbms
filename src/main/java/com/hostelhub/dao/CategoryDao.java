package com.hostelhub.dao;

import com.hostelhub.model.Category;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** CATEGORY. */
public final class CategoryDao {

    private CategoryDao() {
    }

    public static List<Category> all(Connection c) throws SQLException {
        return Jdbc.list(c, "SELECT category_id, category_name, sla_days FROM category ORDER BY category_name",
                rs -> new Category(rs.getLong("category_id"), rs.getString("category_name"), rs.getInt("sla_days")));
    }
}
