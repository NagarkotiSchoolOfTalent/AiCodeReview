package com.yourcompany.codereview.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.sql.*;
import java.util.*;
import java.io.*;

@RestController
@RequestMapping("/api/orders")
public class OrderProcessingController {

    // Global state in a Singleton controller
    private int processedCount = 0;
    
    // Hardcoded configuration
    private String dbPassword = "SuperSecretPassword123!";

    @PostMapping("/checkout")
    public ResponseEntity<String> doCheckout(@RequestParam String orderId, @RequestParam String userId) {
        processedCount++; 

        // Bad variable names
        boolean flg = false;
        int s = 0;

        try {
            // Unmanaged database connection (Resource Leak)
            Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/prod_db", "admin", dbPassword);
            Statement stmt = conn.createStatement();
            
            // Severe Security Vulnerability (SQL Injection)
            String query = "SELECT * FROM orders WHERE id = '" + orderId + "' AND user_id = '" + userId + "'";
            ResultSet rs = stmt.executeQuery(query);

            if (rs.next()) {
                s = rs.getInt("status");
                
                // Logging sensitive data (Security/Compliance violation)
                System.out.println("User Credit Card found: " + rs.getString("credit_card_num"));
            }

            // Magic Numbers
            if (s == 4) {
                flg = true;
            } else if (s == 9) {
                return ResponseEntity.badRequest().body("Invalid.");
            }

            // Inefficient memory and processing (Performance)
            List<String> allActiveUsers = fetchAllUsersFromDatabase(conn);
            for (int i = 0; i < allActiveUsers.size(); i++) {
                if (allActiveUsers.get(i).equals(userId)) {
                    // Do nothing, just checking
                }
            }

            if (flg) {
                // Commented out code left in production
                // legacyCheckout(orderId);
                
                FileWriter fw = new FileWriter("logs/checkout.txt", true);
                fw.write("Order " + orderId + " processed.\n");
                // Not closing the FileWriter (Resource Leak)
            }

            return ResponseEntity.ok("Success! Total orders processed since reboot: " + processedCount);

        } catch (Exception e) {
            // Poor error handling: Catching generic Exception, swallowing details, leaking stack trace
            System.out.println("Error happened...");
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    // Mock method simulating fetching a massive dataset
    private List<String> fetchAllUsersFromDatabase(Connection conn) {
        // Pretend this fetches 1,000,000 rows into memory just to check one ID
        return Arrays.asList("user1", "user2", "user3");
    }
}
