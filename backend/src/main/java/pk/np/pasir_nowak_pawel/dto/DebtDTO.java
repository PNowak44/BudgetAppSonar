package pk.np.pasir_nowak_pawel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DebtDTO {

    @NotNull(message = "Id dłużnika nie może być puste")
    private Long debtorId;

    @NotNull(message = "Id wierzyciela nie może być puste")
    private Long creditorId;

    @NotNull(message = "Id grupy nie może być puste")
    private Long groupId;

    @NotNull(message = "Kwota nie może być pusta")
    @Positive(message = "Kwota musi być większa od zera")
    private Double amount;

    @NotBlank(message = "Tytuł nie może być pusty")
    @Size(max = 100, message = "Tytuł nie może przekraczać 100 znaków")
    private String title;
}