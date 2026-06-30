package org.sample.azure.student.coreft.service;

import org.sample.azure.student.coreft.StudentProfile;
import org.sample.azure.student.coreft.util.MyBatisUtil;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentService {
    
    private static final Logger logger = LoggerFactory.getLogger(StudentService.class);
    
    public List<StudentProfile> getAllStudents() {
        logger.info("Getting all students from database");
        SqlSession session = null;
        List<StudentProfile> students = new ArrayList<>();
        
        try {
            session = MyBatisUtil.getSqlSessionFactory().openSession();
            students = session.selectList("com.azure.sample.StudentMapper.listStudent");
            logger.info("Retrieved {} students", students.size());
        } catch (Exception ex) {
            logger.error("Error retrieving students: {}", ex.getMessage(), ex);
            // Return empty list in case of error
            students = new ArrayList<>();
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (Exception e) {
                    logger.error("Error closing session: {}", e.getMessage(), e);
                }
            }
        }
        
        return students;
    }
    
    public boolean saveStudent(String name, String email, String major) {
        logger.info("Saving student to database: {}, {}, {}", name, email, major);
        SqlSession session = null;
        boolean success = false;
        
        try {
            session = MyBatisUtil.getSqlSessionFactory().openSession();
            
            // Create parameter map for the insert operation
            Map<String, String> parameters = new HashMap<>();
            parameters.put("name", name);
            parameters.put("email", email);
            parameters.put("major", major);
            
            // Execute the insert
            session.insert("com.azure.sample.StudentMapper.addStudent", parameters);
            session.commit();
            
            logger.info("Student saved successfully: {}", name);
            success = true;
            
        } catch (Exception ex) {
            logger.error("Error saving student: {}", ex.getMessage(), ex);
            if (session != null) {
                try {
                    session.rollback();
                } catch (Exception rollbackEx) {
                    logger.error("Error rolling back transaction: {}", rollbackEx.getMessage(), rollbackEx);
                }
            }
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (Exception e) {
                    logger.error("Error closing session: {}", e.getMessage(), e);
                }
            }
        }
        
        return success;
    }
}
