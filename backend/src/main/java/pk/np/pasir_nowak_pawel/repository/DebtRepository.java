package pk.np.pasir_nowak_pawel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pk.np.pasir_nowak_pawel.model.Debt;

import java.util.List;

@Repository
public interface DebtRepository extends JpaRepository<Debt, Long> {
    List<Debt> findByGroupId(Long groupId);
    void deleteByGroupId(Long groupId);
}