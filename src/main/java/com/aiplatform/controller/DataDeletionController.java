package com.aiplatform.controller;

import com.aiplatform.model.DataDeletionStatus;
import com.aiplatform.repository.DataDeletionStatusRepository;
import com.aiplatform.service.FacebookDataDeletionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/social/facebook")
public class DataDeletionController {

    @Autowired
    private FacebookDataDeletionService deletionService;

    @Autowired
    private DataDeletionStatusRepository statusRepository;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl; // Or a specific status URL

    @PostMapping("/deletion-callback")
    public ResponseEntity<?> handleDataDeletionCallback(@RequestParam("signed_request") String signedRequest) {
        try {
            String confirmationCode = deletionService.processDataDeletion(signedRequest);

            // Pointing to a frontend route that will handle displaying the status
            String statusUrl = frontendUrl + "/data-deletion-status/" + confirmationCode;

            Map<String, String> response = new HashMap<>();
            response.put("url", statusUrl);
            response.put("confirmation_code", confirmationCode);

            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body("Invalid signature");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to process request");
        }
    }

    @GetMapping("/deletion-status/{confirmationCode}")
    public ResponseEntity<?> getDeletionStatus(@PathVariable String confirmationCode) {
        Optional<DataDeletionStatus> statusOpt = statusRepository.findByConfirmationCode(confirmationCode);
        
        if (statusOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(statusOpt.get());
    }
}
