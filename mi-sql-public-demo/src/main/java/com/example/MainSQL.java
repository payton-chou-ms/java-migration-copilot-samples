package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;

public class MainSQL {

    /**
     * Resolves {@code ${ENV_VAR}} placeholders in {@code value} by looking up each
     * variable name in {@link System#getenv}.  Unresolved placeholders are left as-is.
     */
    private static String resolveEnvPlaceholders(String value) {
        if (value == null) return null;
        Matcher m = Pattern.compile("\\$\\{([^}]+)\\}").matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String envVal = System.getenv(m.group(1));
            m.appendReplacement(sb, envVal != null
                    ? Matcher.quoteReplacement(envVal)
                    : Matcher.quoteReplacement(m.group(0)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static void main(String[] args) {

        Properties properties = new Properties();
        try (InputStream input = MainSQL.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to find application.properties");
                return;
            }
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }

        // Resolve ${ENV_VAR} references — credentials come from the environment,
        // never from hard-coded values in config files.
        String connString = resolveEnvPlaceholders(properties.getProperty("AZURE_SQLDB_CONNECTIONSTRING"));

        SQLServerDataSource ds = new SQLServerDataSource();
        ds.setURL(connString);
        try (Connection connection = ds.getConnection()) {
            System.out.println("Connected successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
