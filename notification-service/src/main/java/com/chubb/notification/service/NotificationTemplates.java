package com.chubb.notification.service;

import com.chubb.claims.events.ClaimEvent;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Maps a claim event to the message the claimant should receive. Events that only matter internally
 * (assessment, reassignment, the claimant's own answer) produce no notification.
 */
@Component
public class NotificationTemplates {

    public record Message(String subject, String body) {
    }

    public Optional<Message> messageFor(ClaimEvent e) {
        String ref = e.claimNumber();
        String name = e.claimantName() == null ? "Customer" : e.claimantName();
        return switch (e.type()) {
            case CLAIM_SUBMITTED -> msg("We have received your claim " + ref,
                    "Dear " + name + ",\n\nWe have received your claim " + ref + ". A claims officer will pick it up "
                            + "shortly. You can track its progress at any time.");
            case CLAIM_ASSIGNED -> msg("Your claim " + ref + " is being reviewed",
                    "Dear " + name + ",\n\nA claims officer has started reviewing your claim " + ref + ".");
            case INFO_REQUESTED -> msg("Action needed: more information for claim " + ref,
                    "Dear " + name + ",\n\nWe need more information to continue with claim " + ref + ":\n\n"
                            + e.note() + "\n\nPlease reply through the claims portal.");
            case CLAIM_APPROVED -> msg("Your claim " + ref + " has been approved",
                    "Dear " + name + ",\n\nGood news - your claim " + ref + " has been approved. We will arrange "
                            + "settlement next.");
            case CLAIM_REJECTED -> msg("Decision on your claim " + ref,
                    "Dear " + name + ",\n\nWe are sorry to tell you that your claim " + ref + " has been declined.\n\n"
                            + "Reason: " + e.note());
            case CLAIM_SETTLED -> msg("Your claim " + ref + " has been settled",
                    "Dear " + name + ",\n\nYour claim " + ref + " has been settled. Thank you for choosing us.");
            case CLAIM_ASSESSED, CLAIM_REASSIGNED, INFO_PROVIDED -> Optional.empty();
        };
    }

    private static Optional<Message> msg(String subject, String body) {
        return Optional.of(new Message(subject, body));
    }
}
