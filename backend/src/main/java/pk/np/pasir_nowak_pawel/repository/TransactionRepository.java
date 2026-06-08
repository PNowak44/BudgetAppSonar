package pk.np.pasir_nowak_pawel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pk.np.pasir_nowak_pawel.model.Transaction;
import org.springframework.stereotype.Repository;
import pk.np.pasir_nowak_pawel.model.User;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findAllByUser(User user);
    List<Transaction> findByUser(User user);
    List<Transaction> findAllByUserAndTimestampGreaterThanEqual(User user, LocalDateTime timestamp);
}
