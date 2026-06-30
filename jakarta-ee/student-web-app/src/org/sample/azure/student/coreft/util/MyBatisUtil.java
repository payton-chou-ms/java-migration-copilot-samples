package org.sample.azure.student.coreft.util;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.Reader;

public class MyBatisUtil {
    private static SqlSessionFactory sqlSessionFactory;
    private static Exception initializationException;

    static {
        try {
            System.out.println("Initializing MyBatisUtil...");
            Reader reader = Resources.getResourceAsReader("mybatis-config.xml");
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
            System.out.println("SqlSessionFactory initialized successfully!");

        } catch (Exception e) {
            initializationException = e;
            System.err.println("Failed to initialize SqlSessionFactory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static SqlSessionFactory getSqlSessionFactory() {
        if (sqlSessionFactory == null) {
            throw new RuntimeException("SqlSessionFactory was not properly initialized. " +
                                     (initializationException != null ?
                                      "Initialization error: " + initializationException.getMessage() :
                                      "Unknown initialization error"),
                                     initializationException);
        }
        return sqlSessionFactory;
    }
}