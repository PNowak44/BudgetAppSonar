package pk.np.pasir_nowak_pawel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pk.np.pasir_nowak_pawel.model.Group;
import pk.np.pasir_nowak_pawel.model.User;

import java.util.List;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {
    List<Group> findByMemberships_User(User user);
}