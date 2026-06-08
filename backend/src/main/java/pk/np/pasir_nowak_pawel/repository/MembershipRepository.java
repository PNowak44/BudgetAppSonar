package pk.np.pasir_nowak_pawel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pk.np.pasir_nowak_pawel.model.Membership;

import java.util.List;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {
    List<Membership> findByGroupId(Long groupId);
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
    void deleteByGroupId(Long groupId);
}