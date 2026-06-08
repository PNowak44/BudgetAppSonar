package pk.np.pasir_nowak_pawel.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import pk.np.pasir_nowak_pawel.dto.DebtDTO;
import pk.np.pasir_nowak_pawel.dto.TransactionDTO;
import pk.np.pasir_nowak_pawel.model.Debt;
import pk.np.pasir_nowak_pawel.model.Group;
import pk.np.pasir_nowak_pawel.model.User;
import pk.np.pasir_nowak_pawel.repository.DebtRepository;
import pk.np.pasir_nowak_pawel.repository.GroupRepository;
import pk.np.pasir_nowak_pawel.repository.UserRepository;

import java.util.List;

@Service
public class DebtService {

    private final DebtRepository debtRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final MembershipService membershipService;
    private final CurrentUserService currentUserService;
    private final TransactionService transactionService;

    public DebtService(
            DebtRepository debtRepository,
            GroupRepository groupRepository,
            UserRepository userRepository,
            MembershipService membershipService,
            CurrentUserService currentUserService,
            TransactionService transactionService) {
        this.debtRepository = debtRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.membershipService = membershipService;
        this.currentUserService = currentUserService;
        this.transactionService = transactionService;
    }

    public List<Debt> getGroupDebts(Long groupId) {
        membershipService.assertCurrentUserIsGroupMember(groupId);
        return debtRepository.findByGroupId(groupId);
    }

    public Debt createDebt(DebtDTO debtDTO) {
        Group group = groupRepository.findById(debtDTO.getGroupId())
                .orElseThrow(() -> new EntityNotFoundException("Nie znaleziono grupy"));
        User debtor = userRepository.findById(debtDTO.getDebtorId())
                .orElseThrow(() -> new EntityNotFoundException("Nie znaleziono dłużnika"));
        User creditor = userRepository.findById(debtDTO.getCreditorId())
                .orElseThrow(() -> new EntityNotFoundException("Nie znaleziono wierzyciela"));

        membershipService.assertCurrentUserIsGroupMember(group.getId());
        membershipService.assertUserIsGroupMember(group.getId(), debtor.getId());
        membershipService.assertUserIsGroupMember(group.getId(), creditor.getId());

        if (debtor.getId().equals(creditor.getId())) {
            throw new IllegalStateException("Dłużnik i wierzyciel muszą być różnymi użytkownikami.");
        }

        User currentUser = currentUserService.getCurrentUser();
        assertCurrentUserCanManageDebt(group, debtor, creditor, currentUser);

        Debt debt = new Debt();
        debt.setDebtor(debtor);
        debt.setCreditor(creditor);
        debt.setGroup(group);
        debt.setAmount(debtDTO.getAmount());
        debt.setTitle(debtDTO.getTitle());

        return debtRepository.save(debt);
    }

    public void deleteDebt(Long debtId) {
        Debt debt = debtRepository.findById(debtId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Nie można usunąć długu. Dług o ID " + debtId + " nie istnieje."));

        membershipService.assertCurrentUserIsGroupMember(debt.getGroup().getId());
        User currentUser = currentUserService.getCurrentUser();
        assertCurrentUserCanManageDebt(debt.getGroup(), debt.getDebtor(), debt.getCreditor(), currentUser);
        debtRepository.delete(debt);
    }

    private void assertCurrentUserCanManageDebt(Group group, User debtor, User creditor, User currentUser) {
        boolean isGroupOwner = group.getOwner().getId().equals(currentUser.getId());
        boolean isDebtParticipant = debtor.getId().equals(currentUser.getId())
                || creditor.getId().equals(currentUser.getId());

        if (!isGroupOwner && !isDebtParticipant) {
            throw new AccessDeniedException(
                    "Tylko właściciel grupy albo uczestnik długu może wykonać te operacje.");
        }
    }

    public Debt markDebtAsPaid(Long debtId) {
        Debt debt = getDebtForCurrentGroupMember(debtId);
        User currentUser = currentUserService.getCurrentUser();
        if (!debt.getDebtor().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Tylko dluznik moze oznaczyc dlug jako oplacony.");
        }
        debt.setPaidByDebtor(true);
        debt.setConfirmedByCreditor(false);
        return debtRepository.save(debt);
    }

    public Debt confirmDebtPayment(Long debtId) {
        Debt debt = getDebtForCurrentGroupMember(debtId);
        User currentUser = currentUserService.getCurrentUser();

        if (!debt.getCreditor().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Tylko wierzyciel może potwierdzić spłatę długu.");
        }
        if (!debt.isPaidByDebtor()) {
            throw new IllegalStateException("Dług musi zostać najpierw oznaczony jako opłacony przez dłużnika.");
        }

        debt.setConfirmedByCreditor(true);
        Debt savedDebt = debtRepository.save(debt);

        TransactionDTO incomeDTO = new TransactionDTO();
        incomeDTO.setAmount(debt.getAmount());
        incomeDTO.setType("INCOME");
        incomeDTO.setNotes("Spłata długu grupowego: " + debt.getTitle() + " od " + debt.getDebtor().getEmail());
        transactionService.createTransactionForUser(incomeDTO, debt.getCreditor());

        TransactionDTO expenseDTO = new TransactionDTO();
        expenseDTO.setAmount(debt.getAmount());
        expenseDTO.setType("EXPENSE");
        expenseDTO.setNotes("Opłacenie długu grupowego: " + debt.getTitle() + " dla " + debt.getCreditor().getEmail());
        transactionService.createTransactionForUser(expenseDTO, debt.getDebtor());

        return savedDebt;
    }

    private Debt getDebtForCurrentGroupMember(Long debtId) {
        Debt debt = debtRepository.findById(debtId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Nie znaleziono dlugu o ID " + debtId + "."));
        membershipService.assertCurrentUserIsGroupMember(debt.getGroup().getId());
        return debt;
    }


}