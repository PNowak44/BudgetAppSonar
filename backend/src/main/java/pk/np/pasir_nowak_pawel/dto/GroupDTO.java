package pk.np.pasir_nowak_pawel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GroupDTO {

    @NotBlank(message = "Nazwa grupy nie może być pusta")
    @Size(max = 100, message = "Nazwa grupy nie może przekraczać 100 znaków")
    private String name;
}