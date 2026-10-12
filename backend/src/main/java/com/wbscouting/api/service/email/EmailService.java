package com.wbscouting.api.service.email;

import com.wbscouting.api.dto.CandidateApplicationDto;

public interface EmailService {

    void sendPasswordResetEmail(String to, String name, String token);

    void sendAdminWelcomeEmail(String to, String name, String role, String activationToken);

    void sendCandidateApplicationNotification(String recipientEmail, CandidateApplicationDto candidateData);
}
