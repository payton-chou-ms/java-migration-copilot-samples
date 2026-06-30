package org.sample.azure.student.coreft;

import org.sample.azure.student.coreft.service.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailAddress;
import com.azure.communication.email.models.EmailMessage;
import com.azure.identity.DefaultAzureCredentialBuilder;

public class AddStudentServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(AddStudentServlet.class);
    private final StudentService studentService = new StudentService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logger.info("Displaying add student form");
        // Forward to the add student form JSP
        request.getRequestDispatcher("/add_student_profile.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String name = request.getParameter("name");
        String email = request.getParameter("email");
        String major = request.getParameter("major");
        boolean success = false;
        String errorMsg = null;
        
        try {
            logger.info("Starting to add student: name={}, email={}, major={}", name, email, major);
            
            Map<String, Object> params = new HashMap<>();
            params.put("name", name);
            params.put("email", email);
            params.put("major", major);
            
            success = studentService.addStudent(params);
            if (!success) {
                errorMsg = "Failed to add student.";
            }
            
        } catch (Exception e) {
            logger.error("Error adding student: {}", e.getMessage(), e);
            errorMsg = e.getMessage();
        }
        
        if (success) {
            logger.info("Student added successfully, sending email to: {}", email);
            try {
                sendEmail(email, name);
            } catch (Exception emailEx) {
                logger.warn("Student added but failed to send email to {}: {}", email, emailEx.getMessage(), emailEx);
            }
        }
        
        if (success) {
            logger.info("Redirecting to HelloServlet after successful add.");
            response.setContentType("text/html");
            response.getWriter().write("<h2>Student added successfully!</h2>");
            response.getWriter().write("<p><a href='studentProfileList'>View All Student Profiles</a></p>");
            response.getWriter().write("<p><a href='/'>Add Another Student</a></p>");
        } else {
            logger.warn("Add student failed: {}", errorMsg);
            request.setAttribute("errorMsg", errorMsg != null ? errorMsg : "Failed to add student.");
            request.getRequestDispatcher("/add_student_profile.jsp").forward(request, response);
        }
    }

    private void sendEmail(String to, String name) throws Exception {
        logger.info("Preparing to send email to: {}", to);
        EmailClient emailClient = new EmailClientBuilder()
                .endpoint(System.getenv("ACS_EMAIL_ENDPOINT"))
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
        String senderAddress = System.getenv("ACS_SENDER_ADDRESS");
        EmailMessage emailMessage = new EmailMessage()
                .setSenderAddress(senderAddress)
                .setToRecipients(new EmailAddress(to))
                .setSubject("Welcome, " + name + "!")
                .setBodyPlainText("Dear " + name + ",\n\nYour student profile has been created successfully.\n\nRegards,\nAdmin");
        emailClient.beginSend(emailMessage).waitForCompletion();
        logger.info("Email sent to: {}", to);
    }
}
