package org.sample.azure.student.coreft;

import org.sample.azure.student.coreft.service.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class StudentProfileListServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(StudentProfileListServlet.class);
    private final StudentService studentService;

    public StudentProfileListServlet() {
        studentService = new StudentService();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        logger.info("Start to list student profile list");
        response.setContentType("text/html;charset=UTF-8");
        
        try (PrintWriter out = response.getWriter()) {
            out.println("<html><head><title>Student Profile List</title></head><body>");
            out.println("<h2>Student Profile List</h2>");
            
            try {
                List<StudentProfile> students = studentService.listStudents();
                
                out.println("<table border='1'><tr><th>ID</th><th>Name</th><th>Email</th><th>Major</th></tr>");
                for (StudentProfile student : students) {
                    out.println("<tr><td>" + esc(String.valueOf(student.getId())) + "</td>" +
                               "<td>" + esc(student.getName()) + "</td>" +
                               "<td>" + esc(student.getEmail()) + "</td>" +
                               "<td>" + esc(student.getMajor()) + "</td></tr>");
                }
                out.println("</table>");
                out.println("<br/><br/><br/>");
                
            } catch (Exception ex) {
                logger.error("Error retrieving student list: {}", ex.getMessage(), ex);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.println("<p>Error: Unable to retrieve student list.</p>");
            }
            out.println("</body></html>");
        }
    }
}
