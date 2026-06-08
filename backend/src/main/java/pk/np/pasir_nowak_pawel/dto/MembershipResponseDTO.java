package pk.np.pasir_nowak_pawel.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MembershipResponseDTO {

    private Long id;
    private Long userId;
    private Long groupId;
    private String userEmail;
}