package com.microsoft.migration.todo.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class PostgreSqlDemonstrator {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Demonstrates executing raw PostgreSQL queries directly.
     * Uses standard SQL functions compatible with PostgreSQL:
     * - SUBSTRING for partial string extraction
     * - TO_CHAR for timestamp formatting
     * - EXTRACT for interval arithmetic
     */
    public List<Map<String, Object>> executeRawQuery(String keyword, int minPriority) {
        String sql = """
                SELECT
                    ID,
                    TITLE,
                    SUBSTRING(DESCRIPTION FROM 1 FOR 50) AS SHORT_DESC,
                    CASE WHEN LENGTH(DESCRIPTION) > 50 THEN 'Y' ELSE 'N' END AS IS_LONG_DESC,
                    PRIORITY,
                    TO_CHAR(DUE_DATE, 'YYYY-MM-DD HH24:MI:SS') AS FORMATTED_DUE_DATE,
                    EXTRACT(DAY FROM (CURRENT_TIMESTAMP - CREATED_AT))::INTEGER AS DAYS_SINCE_CREATION
                FROM
                    TODO_ITEMS
                WHERE
                    (UPPER(TITLE) LIKE UPPER('%' || ? || '%') OR
                     UPPER(DESCRIPTION) LIKE UPPER('%' || ? || '%'))
                    AND PRIORITY >= ?
                ORDER BY
                    PRIORITY DESC,
                    DUE_DATE ASC
                """;

        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection conn = jdbcTemplate.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, keyword);
            stmt.setString(2, keyword);
            stmt.setInt(3, minPriority);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("ID"));
                row.put("title", rs.getString("TITLE"));
                row.put("shortDescription", rs.getString("SHORT_DESC"));
                row.put("isLongDescription", "Y".equals(rs.getString("IS_LONG_DESC")));
                row.put("priority", rs.getInt("PRIORITY"));
                row.put("formattedDueDate", rs.getString("FORMATTED_DUE_DATE"));
                row.put("daysSinceCreation", rs.getInt("DAYS_SINCE_CREATION"));
                results.add(row);
            }

            log.info("Executed PostgreSQL query with {} results", results.size());
            return results;

        } catch (SQLException e) {
            log.error("Error executing PostgreSQL query", e);
            throw new RuntimeException("Failed to execute PostgreSQL query", e);
        }
    }

    /**
     * Demonstrates PostgreSQL-specific database operations using standard DDL/DML.
     * Creates a temporary statistics table and populates it with aggregate counts.
     */
    public void performDatabaseOperations() {
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS temp_todo_stats");
            jdbcTemplate.execute("""
                    CREATE TABLE temp_todo_stats (
                        category     VARCHAR(100),
                        count_value  INTEGER,
                        last_updated TIMESTAMP
                    )""");
            jdbcTemplate.update(
                    "INSERT INTO temp_todo_stats SELECT 'TOTAL', COUNT(*), CURRENT_TIMESTAMP FROM todo_items");
            jdbcTemplate.update(
                    "INSERT INTO temp_todo_stats SELECT 'COMPLETED', COUNT(*), CURRENT_TIMESTAMP FROM todo_items WHERE completed = 1");
            jdbcTemplate.update(
                    "INSERT INTO temp_todo_stats SELECT 'PENDING', COUNT(*), CURRENT_TIMESTAMP FROM todo_items WHERE completed = 0");
            jdbcTemplate.update(
                    "INSERT INTO temp_todo_stats SELECT 'HIGH_PRIORITY', COUNT(*), CURRENT_TIMESTAMP FROM todo_items WHERE priority >= 8");

            log.info("Successfully created and populated temporary statistics table");
        } catch (Exception e) {
            log.error("Error executing database operations", e);
        }
    }
}
