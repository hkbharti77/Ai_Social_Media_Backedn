package com.aiplatform.service;

import com.aiplatform.dto.SupportTicketDtos.SupportTicketRequest;
import com.aiplatform.dto.SupportTicketDtos.SupportTicketResponse;
import com.aiplatform.model.SupportTicket;
import com.aiplatform.model.SupportTicketStatus;
import com.aiplatform.model.User;
import com.aiplatform.repository.SupportTicketRepository;
import com.aiplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportTicketService {

    private final SupportTicketRepository supportTicketRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional
    public SupportTicketResponse createTicket(Long userId, SupportTicketRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        SupportTicket ticket = SupportTicket.builder()
                .subject(request.getSubject())
                .description(request.getDescription())
                .priority(request.getPriority() != null ? request.getPriority() : com.aiplatform.model.SupportTicketPriority.MEDIUM)
                .status(SupportTicketStatus.OPEN)
                .user(user)
                .build();

        ticket = supportTicketRepository.save(ticket);
        log.info("Support ticket created with ID {} for user ID {}", ticket.getId(), userId);

        emailService.sendTicketCreatedEmail(user, ticket);
        emailService.sendAdminNewTicketAlert(ticket);

        return mapToResponse(ticket);
    }

    @Transactional(readOnly = true)
    public List<SupportTicketResponse> getUserTickets(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return supportTicketRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SupportTicketResponse> getAllTickets() {
        return supportTicketRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public SupportTicketResponse updateTicketStatus(Long ticketId, SupportTicketStatus newStatus) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        ticket.setStatus(newStatus);
        ticket = supportTicketRepository.save(ticket);
        log.info("Support ticket ID {} status updated to {}", ticketId, newStatus);

        emailService.sendTicketStatusUpdateEmail(ticket.getUser(), ticket);

        return mapToResponse(ticket);
    }

    @Transactional
    public void replyToTicket(Long ticketId, String message) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        log.info("Admin replied to ticket ID {}", ticketId);
        emailService.sendTicketReplyEmail(ticket.getUser(), ticket, message);
    }

    private SupportTicketResponse mapToResponse(SupportTicket ticket) {
        return SupportTicketResponse.builder()
                .id(ticket.getId())
                .subject(ticket.getSubject())
                .description(ticket.getDescription())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .userId(ticket.getUser().getId())
                .userFullName(ticket.getUser().getFullName())
                .userEmail(ticket.getUser().getEmail())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }
}
