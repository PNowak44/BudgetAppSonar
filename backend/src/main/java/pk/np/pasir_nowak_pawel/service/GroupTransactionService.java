package pk.np.pasir_nowak_pawel.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pk.np.pasir_nowak_pawel.config.NotificationWebSocketHandler;
import pk.np.pasir_nowak_pawel.dto.GroupExpenseNotification;
import pk.np.pasir_nowak_pawel.dto.GroupTransactionDTO;
import pk.np.pasir_nowak_pawel.dto.TransactionDTO;
import pk.np.pasir_nowak_pawel.model.Debt;
import pk.np.pasir_nowak_pawel.model.Group;
import pk.np.pasir_nowak_pawel.model.Membership;
import pk.np.pasir_nowak_pawel.model.User;
import pk.np.pasir_nowak_pawel.repository.DebtRepository;
import pk.np.pasir_nowak_pawel.repository.GroupRepository;
import pk.np.pasir_nowak_pawel.repository.MembershipRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class GroupTransactionService {

    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final DebtRepository debtRepository;
    private final MembershipService membershipService;
    private final NotificationWebSocketHandler notificationHandler;
    private final TransactionService transactionService;

    public GroupTransactionService(
            GroupRepository groupRepository,
            MembershipRepository membershipRepository,
            DebtRepository debtRepository,
            MembershipService membershipService,
            NotificationWebSocketHandler notificationHandler,
            TransactionService transactionService) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.debtRepository = debtRepository;
        this.membershipService = membershipService;
        this.notificationHandler = notificationHandler;
        this.transactionService = transactionService;
    }

    public void addGroupTransaction(GroupTransactionDTO transactionDTO, User currentUser) {
        Group group = groupRepository.findById(transactionDTO.getGroupId())
                .orElseThrow(() -> new EntityNotFoundException("Nie znaleziono Grupy"));

        membershipService.assertCurrentUserIsGroupMember(group.getId());

        List<Membership> members = membershipRepository.findByGroupId(group.getId());
        List<Membership> selectedMembers = selectParticipants(transactionDTO, members, currentUser);

        if (selectedMembers.isEmpty()) {
            throw new IllegalStateException("Grupa nie ma czlonkow, nie mozna dodac transakcji.");
        }
        double amountPerUser = transactionDTO.getAmount() / selectedMembers.size();
        boolean expense = "EXPENSE".equals(transactionDTO.getType());

        if (expense) {
            TransactionDTO personalTransaction = new TransactionDTO();

            personalTransaction.setNotes(transactionDTO.getTitle() + " (Grupa: " + group.getName() + ")");
            personalTransaction.setAmount(transactionDTO.getAmount());
            personalTransaction.setType("EXPENSE");

            transactionService.createTransaction(personalTransaction);
        }

        for (Membership member : selectedMembers){
            User otherUser = member.getUser();
            if (!otherUser.getId().equals(currentUser.getId())){
                Debt debt = new Debt();
                debt.setDebtor(expense ? otherUser : currentUser);
                debt.setCreditor(expense ? currentUser : otherUser);
                debt.setGroup(group);
                debt.setAmount(amountPerUser);
                debt.setTitle(transactionDTO.getTitle());
                debtRepository.save(debt);

                String messageText = String.format(java.util.Locale.US,
                        "%s dodał‚ wydatek \"%s\" w grupie %s. Twoja część: %.2f zł",
                        currentUser.getEmail(), transactionDTO.getTitle(), group.getName(), amountPerUser);

                GroupExpenseNotification notification = new GroupExpenseNotification(
                        "GROUP_EXPENSE_ADDED",
                        group.getId(),
                        group.getName(),
                        transactionDTO.getTitle(),
                        transactionDTO.getAmount(),
                        amountPerUser,
                        currentUser.getEmail(),
                        messageText
                );

                notificationHandler.sendNotificationToUser(otherUser.getEmail(), notification);
            }
        }
    }

    private List<Membership> selectParticipants(
            GroupTransactionDTO transactionDTO,
            List<Membership> members,
            User currentUser) {
        List<Long> selectedUserIds = transactionDTO.getSelectedUserIds();
        if (selectedUserIds == null || selectedUserIds.isEmpty()) {
            return members;
        }
        Set<Long> uniqueSelectedUserIds = new HashSet<>(selectedUserIds);
        List<Membership> selectedMembers = members.stream()
                .filter(membership -> uniqueSelectedUserIds.contains(membership.getUser().getId()))
                .toList();
        if (selectedMembers.size() != uniqueSelectedUserIds.size()) {
            throw new IllegalStateException(
                    "Wszyscy wybrani uzytkownicy musza byc czlonkami grupy.");
        }
        boolean currentUserSelected = selectedMembers.stream()
                .anyMatch(membership -> membership.getUser().getId().equals(currentUser.getId()));
        if (!currentUserSelected) {
            throw new IllegalStateException(
                    "Aktualny uzytkownik musi byc uczestnikiem transakcji grupowej.");
        }
        if (selectedMembers.size() < 2) {
            throw new IllegalStateException("Transakcja grupowa wymaga co najmniej dwoch uczestnikow.");
        }
        return selectedMembers;
    }
}