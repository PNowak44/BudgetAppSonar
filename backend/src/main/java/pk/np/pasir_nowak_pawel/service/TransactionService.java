package pk.np.pasir_nowak_pawel.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import pk.np.pasir_nowak_pawel.dto.BalanceDTO;
import pk.np.pasir_nowak_pawel.dto.TransactionDTO;
import pk.np.pasir_nowak_pawel.model.Transaction;
import pk.np.pasir_nowak_pawel.model.TransactionType;
import pk.np.pasir_nowak_pawel.model.User;
import pk.np.pasir_nowak_pawel.repository.TransactionRepository;
import pk.np.pasir_nowak_pawel.repository.UserRepository;

import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public TransactionService(TransactionRepository transactionRepository, UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    public List<Transaction> getAllTransactions() {
        User user = getCurrentUser();
        return transactionRepository.findAllByUser(user);
    }

    public Transaction getTransactionById(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Nie znaleziono transakcji o ID " + id));

        if (!transaction.getUser().getEmail().equals(getCurrentUser().getEmail())) {
            throw new AccessDeniedException("Nie masz dostępu do tej transakcji");
        }

        return transaction;
    }

    public Transaction updateTransaction(Long id, TransactionDTO transactionDTO) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Nie znaleziono transakcji o ID " + id));

        if (!transaction.getUser().getEmail().equals(getCurrentUser().getEmail())) {
            throw new AccessDeniedException("Nie masz dostępu do tej transakcji");
        }

        transaction.setAmount(transactionDTO.getAmount());
        transaction.setType(TransactionType.valueOf(transactionDTO.getType()));
        transaction.setTags(transactionDTO.getTags());
        transaction.setNotes(transactionDTO.getNotes());

        return transactionRepository.save(transaction);
    }

    public Transaction createTransaction(TransactionDTO transactionDTO) {
        Transaction transaction = new Transaction();
        transaction.setAmount(transactionDTO.getAmount());
        transaction.setType(TransactionType.valueOf(transactionDTO.getType()));
        transaction.setTags(transactionDTO.getTags());
        transaction.setNotes(transactionDTO.getNotes());
        transaction.setUser(getCurrentUser());
        transaction.setTimestamp(LocalDateTime.now());

        return transactionRepository.save(transaction);
    }

    public Transaction createTransactionForUser(TransactionDTO transactionDTO, User user) {
        Transaction transaction = new Transaction();
        transaction.setAmount(transactionDTO.getAmount());
        transaction.setType(TransactionType.valueOf(transactionDTO.getType()));
        transaction.setTags(transactionDTO.getTags());
        transaction.setNotes(transactionDTO.getNotes());
        transaction.setUser(user);
        transaction.setTimestamp(LocalDateTime.now());

        return transactionRepository.save(transaction);
    }

    public Boolean deleteTransaction(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Nie znaleziono transakcji o ID " + id));

        if (!transaction.getUser().getEmail().equals(getCurrentUser().getEmail())) {
            throw new AccessDeniedException("Nie masz dostępu do tej transakcji");
        }

        transactionRepository.delete(transaction);
        return true;
    }

    public User getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Użytkownik nie jest uwierzytelniony");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Nie znaleziono zalogowanego użytkownika: " + email));
    }

    public BalanceDTO getUserBalance(User user, Float days) {
        List<Transaction> userTransactions;

        if (days != null && days > 0) {
            long seconds = (long) (days * 86400);
            LocalDateTime threshold = LocalDateTime.now().minusSeconds(seconds);
            userTransactions = transactionRepository.findAllByUserAndTimestampGreaterThanEqual(user, threshold);
        } else {
            userTransactions = transactionRepository.findByUser(user);
        }

        double income = userTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .mapToDouble(Transaction::getAmount)
                .sum();

        double expense = userTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .mapToDouble(Transaction::getAmount)
                .sum();

        return new BalanceDTO(income, expense, income - expense);
    }
}
