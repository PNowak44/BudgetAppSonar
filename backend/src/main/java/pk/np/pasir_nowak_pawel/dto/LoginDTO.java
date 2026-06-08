package pk.np.pasir_nowak_pawel.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginDTO {

    @NotBlank(message = "Email nie może być pusty")
    @Email(message = "Podaj poprawny format adresu email")
    private String email;

    @NotBlank(message = "Hasło nie może być puste")
    private String password;
}