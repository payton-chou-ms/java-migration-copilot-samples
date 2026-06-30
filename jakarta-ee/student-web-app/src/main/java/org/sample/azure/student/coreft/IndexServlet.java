package org.sample.azure.student.coreft;

import org.sample.azure.student.coreft.util.MyBatisUtil;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Servlet to handle the root path and populate index.jsp with student data
 */
public class IndexServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(IndexServlet.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        logger.info("Processing request for index page with student data");
        
        SqlSession session = null;
        try {
            // Get student data
            session = MyBatisUtil.getSqlSessionFactory().openSession();
            List<StudentProfile> students = session.selectList("com.azure.sample.StudentMapper.listStudent");
            
            // Set attributes for the JSP
            request.setAttribute("students", students);
            logger.info("Successfully loaded {} students for index page", students.size());
            
        } catch (Exception ex) {
            logger.error("Error loading students for index page: {}", ex.getMessage(), ex);
            // Set error message and empty list
            request.setAttribute("error", "Unable to load student data: " + ex.getMessage());
            request.setAttribute("students", null);
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (Exception e) {
                    logger.error("Error closing session: {}", e.getMessage(), e);
                }
            }
        }
        
        // Forward to the JSP
        request.getRequestDispatcher("/index.jsp").forward(request, response);
    }
}
